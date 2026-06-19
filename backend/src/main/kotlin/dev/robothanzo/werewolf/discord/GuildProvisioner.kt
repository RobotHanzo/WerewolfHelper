package dev.robothanzo.werewolf.discord

import dev.robothanzo.werewolf.domain.GameSession
import dev.robothanzo.werewolf.domain.Seat
import dev.robothanzo.werewolf.ops.BulkItem
import dev.robothanzo.werewolf.ops.BulkOperationEngine
import dev.robothanzo.werewolf.ops.BulkPhase
import dev.robothanzo.werewolf.ops.ProgressSink
import dev.robothanzo.werewolf.i18n.Msg
import dev.robothanzo.werewolf.websocket.GameWebSocketHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Icon
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.awt.Color

/**
 * Guild provisioning (FEATURES §3): build/resize/tear-down the Discord roles + channels for a game
 * server. Long-running, so the resize streams progress through [BulkOperationEngine] over the
 * WebSocket. Lifted out of the old gateway so it stands alone; with no Discord connection (`JDA?` is
 * null) it still keeps the in-memory seat list in sync, which is all the tokenless/dev path needs.
 */
@Component
class GuildProvisioner(
    private val jda: JDA?,
    private val engine: BulkOperationEngine,
    private val ws: GameWebSocketHandler,
    private val msg: Msg,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    private fun sink(guildId: Long): ProgressSink =
        ProgressSink { percent, line, severity -> ws.broadcastProgress(guildId, percent, line, severity.name.lowercase()) }

    suspend fun provisionGuild(session: GameSession) {
        val jda = jda ?: run {
            log.info("[no-jda] provisionGuild {}", session.guildId)
            ensureSeats(session)
            return
        }
        withContext(Dispatchers.IO) {
            val guild = jda.getGuildById(session.guildId) ?: error("guild ${session.guildId} not found")
            log.info("Provisioning guild {}", session.guildId)

            runCatching {
                guild.manager
                    .setName("狼人殺伺服器")
                    .setDefaultNotificationLevel(Guild.NotificationLevel.MENTIONS_ONLY)
                    .complete()
                javaClass.classLoader.getResourceAsStream("wolf.png")
                    ?.use { guild.manager.setIcon(Icon.from(it)).complete() }
            }.onFailure { log.warn("guild rename/icon failed: {}", it.message) }

            // Delete existing channels (per-item isolated).
            guild.channels.forEach { ch ->
                runCatching { ch.delete().complete() }.onFailure { log.warn("delete {} failed: {}", ch.id, it.message) }
            }

            // Judge role (yellow, admin).
            val judgeRole = guild.createRole().setName("法官").setColor(Color.YELLOW).setHoisted(true)
                .setPermissions(listOf(Permission.ADMINISTRATOR)).complete()
            session.discordIds.judgeRoleId = judgeRole.idLong

            // Seat roles + private channels.
            ensureSeats(session)
            session.seats.sortedBy { it.number }.forEach { seat -> provisionSeat(guild, session, seat) }

            // Spectator role (brown) + per-seat-channel overrides.
            val spectatorRole = guild.createRole().setName("旁觀者").setColor(Color(0x65, 0x43, 0x21))
                .setPermissions(listOf(Permission.VIEW_CHANNEL)).complete()
            session.discordIds.spectatorRoleId = spectatorRole.idLong
            session.seats.forEach { seat ->
                guild.getTextChannelById(seat.channelId)?.let { ch ->
                    runCatching {
                        ch.upsertPermissionOverride(spectatorRole)
                            .setAllowed(Permission.VIEW_CHANNEL).setDenied(Permission.MESSAGE_SEND).complete()
                    }
                }
            }

            // Shared channels with overwrites set at creation time.
            val pub = guild.publicRole
            val courtText = guild.createTextChannel("法院")
                .addPermissionOverride(pub, emptyList(), listOf(Permission.USE_APPLICATION_COMMANDS))
                .addPermissionOverride(spectatorRole, listOf(Permission.VIEW_CHANNEL), listOf(Permission.MESSAGE_SEND))
                .complete()
            session.discordIds.courtTextChannelId = courtText.idLong

            val courtVoice = guild.createVoiceChannel("法院")
                .addPermissionOverride(spectatorRole, listOf(Permission.VIEW_CHANNEL), listOf(Permission.VOICE_SPEAK))
                .complete()
            session.discordIds.courtVoiceChannelId = courtVoice.idLong

            val spectatorText = guild.createTextChannel("旁觀者")
                .addPermissionOverride(spectatorRole, listOf(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND), emptyList())
                .addPermissionOverride(pub, emptyList(), listOf(Permission.VIEW_CHANNEL))
                .complete()
            session.discordIds.spectatorTextChannelId = spectatorText.idLong

            val judgeText = guild.createTextChannel("法官")
                .addPermissionOverride(
                    pub,
                    emptyList(),
                    listOf(Permission.VIEW_CHANNEL, Permission.USE_APPLICATION_COMMANDS),
                )
                .addPermissionOverride(judgeRole, listOf(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND), emptyList())
                .complete()
            session.discordIds.judgeTextChannelId = judgeText.idLong

            log.info("Provisioning complete for guild {}", session.guildId)
        }
    }

    suspend fun resizeGuild(session: GameSession, newCount: Int) {
        val jda = jda ?: run {
            log.info("[no-jda] resizeGuild {} -> {}", session.guildId, newCount)
            session.seats.removeAll { it.number > newCount }
            (1..newCount).forEach { n -> if (session.seats.none { it.number == n }) session.seats.add(Seat(number = n)) }
            session.seats.sortBy { it.number }
            session.settings.playerCount = newCount
            ws.broadcastProgress(session.guildId, 100, msg.msg("bulk.finished"), "info")
            return
        }
        withContext(Dispatchers.IO) {
            val guild = jda.getGuildById(session.guildId) ?: error("guild ${session.guildId} not found")
            val excessSeats = session.seats.filter { it.number > newCount }.sortedByDescending { it.number }
            val missingSeatNumbers = (1..newCount).filter { n -> session.seats.none { it.number == n } }

            val deleteItems = excessSeats.flatMap { seat ->
                listOf(
                    BulkItem(msg.msg("bulk.item.delete_role", seat.paddedNumber)) {
                        guild.getRoleById(seat.roleId)?.delete()?.complete()
                    },
                    BulkItem(msg.msg("bulk.item.delete_channel", seat.paddedNumber)) {
                        guild.getTextChannelById(seat.channelId)?.delete()?.complete()
                    },
                )
            }

            val seatsToAdd = missingSeatNumbers.map { n -> Seat(number = n) }
            // Roles must be created before channels (the channel's permission override references the
            // seat role), so they're two separate phases — also reported as distinct progress steps.
            val createRoleItems = seatsToAdd.map { seat ->
                BulkItem(msg.msg("bulk.item.create_role", seat.paddedNumber)) { provisionSeatRole(guild, seat) }
            }
            val createChannelItems = seatsToAdd.map { seat ->
                BulkItem(msg.msg("bulk.item.create_channel", seat.paddedNumber)) { provisionSeatChannel(guild, session, seat) }
            }

            // Split 0..100 evenly across the active phases (delete / create-roles / create-channels).
            val phases = mutableListOf<BulkPhase>()
            val active = buildList {
                if (deleteItems.isNotEmpty()) add("delete_seats" to deleteItems)
                if (createRoleItems.isNotEmpty()) {
                    add("create_roles" to createRoleItems)
                    add("create_channels" to createChannelItems)
                }
            }
            active.forEachIndexed { i, (name, items) ->
                val start = 100 * i / active.size
                val end = 100 * (i + 1) / active.size
                phases.add(BulkPhase(name, start, end, items))
            }

            if (phases.isNotEmpty()) engine.execute(phases, sink(session.guildId))

            // Apply changes in memory
            session.seats.removeAll { it.number > newCount }
            session.seats.addAll(seatsToAdd)
            session.seats.sortBy { it.number }
            session.settings.playerCount = newCount
        }
    }

    suspend fun deleteGuild(guildId: Long) {
        jda?.getGuildById(guildId)?.leave()?.queue()
    }

    private fun ensureSeats(session: GameSession) {
        (1..session.playerCount).forEach { n -> if (session.seats.none { it.number == n }) session.seats.add(Seat(number = n)) }
        session.seats.sortBy { it.number }
    }

    /** Full seat provision = role then channel (split so the bulk engine reports two phases; the
     *  channel creation reads back the role via `seat.roleId`). */
    private fun provisionSeat(guild: Guild, session: GameSession, seat: Seat) {
        provisionSeatRole(guild, seat)
        provisionSeatChannel(guild, session, seat)
    }

    private fun provisionSeatRole(guild: Guild, seat: Seat) {
        val role = guild.createRole().setName("玩家${seat.paddedNumber}").setColor(randomColor()).setHoisted(true).complete()
        seat.roleId = role.idLong
    }

    private fun provisionSeatChannel(guild: Guild, session: GameSession, seat: Seat) {
        val name = "玩家${seat.paddedNumber}"
        val role = guild.getRoleById(seat.roleId) ?: error("seat ${seat.number} role not provisioned")
        val channel = guild.createTextChannel(name)
            .addPermissionOverride(
                role,
                listOf(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.USE_APPLICATION_COMMANDS),
                emptyList(),
            )
            .addPermissionOverride(
                guild.publicRole,
                emptyList(),
                listOf(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND),
            )
            .complete()
        seat.channelId = channel.idLong

        val courtCh = guild.getTextChannelById(session.discordIds.courtTextChannelId)
        val specCh = guild.getTextChannelById(session.discordIds.spectatorTextChannelId)
        val judgeCh = guild.getTextChannelById(session.discordIds.judgeTextChannelId)
        val globalChannels = listOfNotNull(courtCh, specCh, judgeCh)
        val minPosition = globalChannels.minOfOrNull { it.position }
        if (minPosition != null) {
            runCatching {
                guild.modifyTextChannelPositions().selectPosition(channel).moveTo(minPosition).complete()
            }.onFailure { log.warn("failed to move text channel position for {}: {}", name, it.message) }
        }
    }

    private fun randomColor(): Color = Color(Color.HSBtoRGB(Math.random().toFloat(), 0.6f, 0.85f))
}
