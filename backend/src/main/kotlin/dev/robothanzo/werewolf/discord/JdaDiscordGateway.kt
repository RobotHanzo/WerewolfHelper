package dev.robothanzo.werewolf.discord

import club.minnced.discord.webhook.WebhookClient
import club.minnced.discord.webhook.send.WebhookMessageBuilder
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import dev.robothanzo.werewolf.domain.GameSession
import dev.robothanzo.werewolf.domain.Seat
import dev.robothanzo.werewolf.domain.repo.GameSessionRepository
import dev.robothanzo.werewolf.game.roles.RoleRegistry
import dev.robothanzo.werewolf.game.roles.RoleTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Icon
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.guild.GuildJoinEvent
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent
import net.dv8tion.jda.api.events.guild.GuildReadyEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.events.session.ReadyEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.components.selections.StringSelectMenu
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.ChunkingFilter
import net.dv8tion.jda.api.utils.MemberCachePolicy
import net.dv8tion.jda.api.utils.cache.CacheFlag
import dev.robothanzo.werewolf.ops.BulkItem
import dev.robothanzo.werewolf.ops.BulkOperationEngine
import dev.robothanzo.werewolf.ops.BulkPhase
import dev.robothanzo.werewolf.ops.ProgressSink
import dev.robothanzo.werewolf.websocket.GameWebSocketHandler
import org.slf4j.LoggerFactory
import java.awt.Color
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Live JDA-backed gateway. Implements provisioning (FEATURES §3), per-member mutations, channel
 * messaging, mass voice control, the seven audio cues (lavaplayer), and the wolf-chat webhook relay
 * (one cached webhook per channel). Long-running provisioning runs on [Dispatchers.IO] using
 * blocking `.complete()` calls, which lean on JDA's own rate-limit queue as the barrier.
 */
class JdaDiscordGateway(
    properties: DiscordProperties,
    private val nicknames: NicknameService,
    private val sessions: GameSessionRepository,
    private val roles: RoleRegistry,
    private val engine: BulkOperationEngine,
    private val ws: GameWebSocketHandler,
) : DiscordGateway {

    private val log = LoggerFactory.getLogger(javaClass)

    private val jda: JDA = JDABuilder.create(
        properties.token,
        GatewayIntent.GUILD_MEMBERS,
        GatewayIntent.GUILD_MESSAGES,
        GatewayIntent.GUILD_VOICE_STATES,
        GatewayIntent.MESSAGE_CONTENT,
    )
        .setMemberCachePolicy(MemberCachePolicy.ALL)
        .setChunkingFilter(ChunkingFilter.ALL)
        .enableCache(CacheFlag.VOICE_STATE)
        .disableCache(CacheFlag.ACTIVITY, CacheFlag.EMOJI, CacheFlag.STICKER, CacheFlag.SCHEDULED_EVENTS)
        .build()

    private val playerManager = DefaultAudioPlayerManager().also { AudioSourceManagers.registerLocalSource(it) }
    private val webhookCache = ConcurrentHashMap<Long, WebhookClient>()
    private val soundFiles = ConcurrentHashMap<SoundCue, File>()

    @Volatile
    private var interactionHandler: DiscordInteractionHandler? = null

    @Volatile
    private var commandHandler: DiscordCommandHandler? = null

    init {
        jda.addEventListener(RelayListener())
        jda.addEventListener(LifecycleListener())
        jda.addEventListener(ComponentListener())
        jda.addEventListener(CommandListener())
    }

    override val available: Boolean = true

    // ---- helpers ----
    private fun guild(guildId: Long): Guild? = jda.getGuildById(guildId)
    private fun session(guildId: Long): GameSession? = sessions.findById(guildId).orElse(null)
    private fun member(guildId: Long, memberId: Long): Member? =
        guild(guildId)?.let { g -> g.getMemberById(memberId) ?: runCatching { g.retrieveMemberById(memberId).complete() }.getOrNull() }

    // ---- queries ----
    override fun listMembers(guildId: Long): List<GuildMember> =
        guild(guildId)?.members.orEmpty().map {
            GuildMember(it.idLong, it.user.name, it.effectiveName, it.user.effectiveAvatarUrl, it.user.isBot, it.isOwner)
        }

    override fun isOwner(guildId: Long, memberId: Long): Boolean = guild(guildId)?.ownerIdLong == memberId

    override fun canInteract(guildId: Long, memberId: Long): Boolean {
        val g = guild(guildId) ?: return false
        val m = member(guildId, memberId) ?: return false
        return g.selfMember.canInteract(m)
    }

    // ---- provisioning ----
    override suspend fun provisionGuild(session: GameSession) = withContext(Dispatchers.IO) {
        val guild = guild(session.guildId) ?: error("guild ${session.guildId} not found")
        log.info("Provisioning guild {}", session.guildId)

        runCatching {
            guild.manager
                .setName("狼人殺伺服器")
                .setDefaultNotificationLevel(Guild.NotificationLevel.MENTIONS_ONLY)
                .complete()
            javaClass.classLoader.getResourceAsStream("wolf.png")?.use { guild.manager.setIcon(Icon.from(it)).complete() }
        }.onFailure { log.warn("guild rename/icon failed: {}", it.message) }

        // Delete existing channels (per-item isolated).
        guild.channels.forEach { ch -> runCatching { ch.delete().complete() }.onFailure { log.warn("delete {} failed: {}", ch.id, it.message) } }

        // Judge role (yellow, admin).
        val judgeRole = guild.createRole().setName("法官").setColor(Color.YELLOW).setHoisted(true)
            .setPermissions(listOf(Permission.ADMINISTRATOR)).complete()
        session.discordIds.judgeRoleId = judgeRole.idLong

        // Seat roles + private channels.
        ensureSeats(session)
        session.seats.sortedBy { it.number }.forEach { seat -> provisionSeat(guild, seat) }

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
            .addPermissionOverride(pub, emptyList(), listOf(Permission.VIEW_CHANNEL, Permission.USE_APPLICATION_COMMANDS))
            .addPermissionOverride(judgeRole, listOf(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND), emptyList())
            .complete()
        session.discordIds.judgeTextChannelId = judgeText.idLong

        log.info("Provisioning complete for guild {}", session.guildId)
    }

    private fun sink(guildId: Long): ProgressSink =
        ProgressSink { percent, line, severity -> ws.broadcastProgress(guildId, percent, line, severity.name.lowercase()) }

    override suspend fun resizeGuild(session: GameSession, newCount: Int) = withContext(Dispatchers.IO) {
        val guild = guild(session.guildId) ?: error("guild ${session.guildId} not found")
        val excessSeats = session.seats.filter { it.number > newCount }.sortedByDescending { it.number }
        val missingSeatNumbers = (1..newCount).filter { n -> session.seats.none { it.number == n } }

        val deleteItems = excessSeats.flatMap { seat ->
            listOf(
                BulkItem("玩家${seat.paddedNumber} 刪除角色") {
                    guild.getRoleById(seat.roleId)?.delete()?.complete()
                },
                BulkItem("玩家${seat.paddedNumber} 刪除頻道") {
                    guild.getTextChannelById(seat.channelId)?.delete()?.complete()
                }
            )
        }

        val seatsToAdd = missingSeatNumbers.map { n -> Seat(number = n) }
        val createItems = seatsToAdd.map { seat ->
            BulkItem("玩家${seat.paddedNumber} 配置") {
                provisionSeat(guild, seat)
            }
        }

        val phases = mutableListOf<BulkPhase>()
        if (deleteItems.isNotEmpty() && createItems.isNotEmpty()) {
            phases.add(BulkPhase("delete_seats", 0, 50, deleteItems))
            phases.add(BulkPhase("create_seats", 50, 100, createItems))
        } else if (deleteItems.isNotEmpty()) {
            phases.add(BulkPhase("delete_seats", 0, 100, deleteItems))
        } else if (createItems.isNotEmpty()) {
            phases.add(BulkPhase("create_seats", 0, 100, createItems))
        }

        if (phases.isNotEmpty()) {
            engine.execute(phases, sink(session.guildId))
        }

        // Apply changes in memory
        session.seats.removeAll { it.number > newCount }
        session.seats.addAll(seatsToAdd)
        session.seats.sortBy { it.number }
        session.settings.playerCount = newCount
    }

    override suspend fun deleteGuild(guildId: Long) {
        guild(guildId)?.leave()?.queue()
    }

    private fun ensureSeats(session: GameSession) {
        (1..session.playerCount).forEach { n ->
            if (session.seats.none { it.number == n }) session.seats.add(Seat(number = n))
        }
        session.seats.sortBy { it.number }
    }

    private fun provisionSeat(guild: Guild, seat: Seat) {
        val name = "玩家${seat.paddedNumber}"
        val role = guild.createRole().setName(name).setColor(randomColor()).setHoisted(true).complete()
        seat.roleId = role.idLong
        val channel = guild.createTextChannel(name)
            .addPermissionOverride(role, listOf(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.USE_APPLICATION_COMMANDS), emptyList())
            .addPermissionOverride(guild.publicRole, emptyList(), listOf(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND))
            .complete()
        seat.channelId = channel.idLong
    }

    private fun randomColor(): Color = Color(Color.HSBtoRGB(Math.random().toFloat(), 0.6f, 0.85f))

    // ---- per-member mutations ----
    override fun grantSeatRole(guildId: Long, memberId: Long, seatNumber: Int) {
        val g = guild(guildId) ?: return
        val seat = session(guildId)?.seat(seatNumber) ?: return
        val role = g.getRoleById(seat.roleId) ?: return
        val m = member(guildId, memberId) ?: return
        if (g.selfMember.canInteract(role)) g.addRoleToMember(m, role).queue()
    }

    override fun setNickname(guildId: Long, memberId: Long, nickname: String) {
        val g = guild(guildId) ?: error("guild $guildId not found")
        val m = member(guildId, memberId) ?: error("member $memberId not found")
        if (m.isOwner) return // bots can never rename the owner
        if (!g.selfMember.canInteract(m)) error("權限不足")
        if (m.nickname == nickname) return // skip no-op updates
        m.modifyNickname(nickname).queue()
    }

    override fun grantSpectatorRole(guildId: Long, memberId: Long) {
        val g = guild(guildId) ?: return
        val role = session(guildId)?.discordIds?.spectatorRoleId?.let { g.getRoleById(it) } ?: return
        val m = member(guildId, memberId) ?: return
        if (g.selfMember.canInteract(role)) g.addRoleToMember(m, role).queue()
    }

    override fun grantJudgeRole(guildId: Long, memberId: Long) {
        val g = guild(guildId) ?: return
        val role = session(guildId)?.discordIds?.judgeRoleId?.let { g.getRoleById(it) } ?: return
        val m = member(guildId, memberId) ?: return
        if (g.selfMember.canInteract(role)) g.addRoleToMember(m, role).queue()
    }

    override fun resetMember(guildId: Long, memberId: Long) {
        val m = member(guildId, memberId) ?: return
        if (!m.isOwner) m.modifyNickname(null).queue()
    }

    // ---- messaging ----
    override fun sendChannelMessage(guildId: Long, channel: ChannelKind, text: String) {
        val ids = session(guildId)?.discordIds ?: return
        val channelId = when (channel) {
            ChannelKind.COURT -> ids.courtTextChannelId
            ChannelKind.JUDGE -> ids.judgeTextChannelId
            ChannelKind.SPECTATOR -> ids.spectatorTextChannelId
        }
        guild(guildId)?.getTextChannelById(channelId)?.sendMessage(text)?.queue()
    }

    override fun sendSeatMessage(guildId: Long, seatNumber: Int, text: String) {
        val seat = session(guildId)?.seat(seatNumber) ?: return
        guild(guildId)?.getTextChannelById(seat.channelId)?.sendMessage(text)?.queue()
    }

    // ---- voice ----
    override fun muteAll(guildId: Long) = setMuteAll(guildId, true)
    override fun unmuteAll(guildId: Long) = setMuteAll(guildId, false)

    private fun setMuteAll(guildId: Long, muted: Boolean) {
        val g = guild(guildId) ?: return
        g.voiceChannels.flatMap { it.members }
            .filter { !it.user.isBot && !it.isOwner && g.selfMember.canInteract(it) }
            .forEach { it.mute(muted).queue() }
    }

    override fun muteMember(guildId: Long, memberId: Long, muted: Boolean) {
        val m = member(guildId, memberId) ?: return
        if (!m.isOwner && m.voiceState?.channel != null) m.mute(muted).queue()
    }

    override fun playSound(guildId: Long, cue: SoundCue) {
        val g = guild(guildId) ?: return
        val voice = session(guildId)?.discordIds?.courtVoiceChannelId?.let { g.getVoiceChannelById(it) } ?: return
        val file = soundFiles.getOrPut(cue) { extractSound(cue) } ?: return
        val player = playerManager.createPlayer()
        val audioManager = g.audioManager
        audioManager.sendingHandler = AudioPlayerSendHandler(player)
        if (!audioManager.isConnected) runCatching { audioManager.openAudioConnection(voice) }
        playerManager.loadItem(file.absolutePath, object : AudioLoadResultHandler {
            override fun trackLoaded(track: AudioTrack) {
                player.startTrack(track, false)
            }
            override fun playlistLoaded(playlist: AudioPlaylist) {}
            override fun noMatches() {
                log.warn("audio cue {} not found", cue)
            }
            override fun loadFailed(e: FriendlyException) {
                log.error("audio cue {} failed: {}", cue, e.message)
            }
        })
    }

    /** Extract a bundled mp3 to a temp file so lavaplayer's local source can load it by path. */
    private fun extractSound(cue: SoundCue): File? = runCatching {
        val tmp = File.createTempFile("wh-", ".mp3").apply { deleteOnExit() }
        javaClass.classLoader.getResourceAsStream(cue.resource)!!.use { input -> tmp.outputStream().use { input.copyTo(it) } }
        tmp
    }.getOrNull()

    // ---- wolf-chat relay (one cached webhook per channel) ----
    override fun relayWolfChat(guildId: Long, fromSeat: Int, authorName: String, authorAvatar: String?, content: String) {
        val g = guild(guildId) ?: return
        val session = session(guildId) ?: return
        val sender = session.seat(fromSeat) ?: return
        val message = WebhookMessageBuilder().setContent(content).setUsername(authorName).setAvatarUrl(authorAvatar).build()

        // Mirror to the other members of whichever relay group the sender belongs to.
        val wolfGroup = isWolfChat(sender)
        val gbabyGroup = sender.goldenBaby
        session.seats.forEach { target ->
            if (target.number == fromSeat || target.channelId == 0L) return@forEach
            val sameGroup = (wolfGroup && isWolfChat(target)) || (gbabyGroup && target.goldenBaby)
            if (sameGroup) g.getTextChannelById(target.channelId)?.let { webhookFor(it).send(message) }
        }
        // Wolves are also mirrored into the judge channel.
        if (wolfGroup) g.getTextChannelById(session.discordIds.judgeTextChannelId)?.let { webhookFor(it).send(message) }
    }

    /** A seat participates in wolf chat if any living card has the WOLF_CHAT tag (wolves, 夢魘, …). */
    private fun isWolfChat(seat: Seat): Boolean =
        seat.livingCards().ifEmpty { seat.cards }.any { roles.byId(it.roleId)?.hasTag(RoleTag.WOLF_CHAT) == true }

    private fun webhookFor(channel: TextChannel): WebhookClient =
        webhookCache.getOrPut(channel.idLong) {
            val hook = channel.retrieveWebhooks().complete().firstOrNull { it.token != null }
                ?: channel.createWebhook("Werewolf Relay").complete()
            WebhookClient.withUrl(hook.url)
        }

    /** Listens for messages in seat channels and mirrors wolf-team chatter. */
    private inner class RelayListener : ListenerAdapter() {
        override fun onMessageReceived(event: MessageReceivedEvent) {
            if (event.author.isBot || !event.isFromGuild) return
            val session = session(event.guild.idLong) ?: return
            val author = event.member?.effectiveName ?: event.author.name
            val avatar = event.author.effectiveAvatarUrl
            val content = event.message.contentRaw

            // From a seat channel → relay within the sender's group.
            session.seats.firstOrNull { it.channelId == event.channel.idLong }?.let { sender ->
                relayWolfChat(event.guild.idLong, sender.number, "$author（${sender.paddedNumber}）", avatar, content)
                return
            }
            // From the judge channel → mirror to every wolf-team channel.
            if (event.channel.idLong == session.discordIds.judgeTextChannelId) {
                val message = WebhookMessageBuilder().setContent(content).setUsername("法官頻道（$author）").setAvatarUrl(avatar).build()
                session.seats.filter { isWolfChat(it) && it.channelId != 0L }
                    .forEach { event.guild.getTextChannelById(it.channelId)?.let { ch -> webhookFor(ch).send(message) } }
            }
        }
    }

    // ---- night interactions ----
    override fun setInteractionHandler(handler: DiscordInteractionHandler) {
        interactionHandler = handler
    }

    override fun setCommandHandler(handler: DiscordCommandHandler) {
        commandHandler = handler
    }

    override fun promptNightAction(
        guildId: Long,
        seatNumber: Int,
        customId: String,
        prompt: String,
        options: List<SeatOption>,
        extraValues: List<Pair<String, String>>,
        allowSkip: Boolean,
        maxValues: Int,
    ) {
        val seat = session(guildId)?.seat(seatNumber) ?: return
        val channel = guild(guildId)?.getTextChannelById(seat.channelId) ?: return
        val menu = StringSelectMenu.create(customId).apply {
            extraValues.forEach { (value, label) -> addOption(label, value) }
            options.take(23).forEach { addOption(it.label, it.seat.toString()) }
            if (allowSkip) addOption("不行動", InteractionIds.SKIP)
            setRequiredRange(1, maxValues.coerceAtLeast(1))
        }.build()
        channel.sendMessage(prompt).addComponents(ActionRow.of(menu)).queue()
    }

    override fun promptWolfVote(guildId: Long, voterSeats: List<Int>, options: List<SeatOption>) {
        val g = guild(guildId) ?: return
        val session = session(guildId) ?: return
        val buttons = options.map { Button.danger("${InteractionIds.WOLF_VOTE}:${it.seat}", it.label) } +
            Button.secondary("${InteractionIds.WOLF_VOTE}:${InteractionIds.SKIP}", "本夜不刀")
        val rows = buttons.chunked(5).map { ActionRow.of(it) }
        voterSeats.forEach { seatNumber ->
            val seat = session.seat(seatNumber) ?: return@forEach
            g.getTextChannelById(seat.channelId)?.sendMessage("🐺 狼人請投票決定今晚刀口：")?.addComponents(rows)?.queue()
        }
    }

    /** Membership rules (FEATURES §3): latecomers become spectators once identities are assigned;
     *  the session is deleted if the bot is removed from the guild. */
    private inner class LifecycleListener : ListenerAdapter() {
        override fun onGuildMemberJoin(event: GuildMemberJoinEvent) {
            val session = session(event.guild.idLong) ?: return
            if (event.member.idLong == session.ownerId) {
                grantJudgeRole(event.guild.idLong, event.member.idLong) // re-grant judge to the owner (§3)
                return
            }
            if (session.assigned) grantSpectatorRole(event.guild.idLong, event.member.idLong)
        }

        // Provision when the bot joins a guild with a matching pending config, and on restart-readiness.
        override fun onGuildJoin(event: GuildJoinEvent) {
            commandHandler?.onGuildJoined(event.guild.idLong, event.guild.ownerIdLong)
        }

        override fun onGuildReady(event: GuildReadyEvent) {
            commandHandler?.onGuildJoined(event.guild.idLong, event.guild.ownerIdLong)
        }

        override fun onGuildLeave(event: GuildLeaveEvent) {
            runCatching { sessions.deleteById(event.guild.idLong) }
            webhookCache.clear()
        }
    }

    /** Registers and handles the `/server` slash command (create / delete), for server creators. */
    private inner class CommandListener : ListenerAdapter() {
        override fun onReady(event: ReadyEvent) {
            jda.updateCommands().addCommands(
                Commands.slash("server", "管理狼人殺遊戲伺服器")
                    .addSubcommands(
                        SubcommandData("create", "建立一個新的遊戲伺服器設定")
                            .addOptions(
                                OptionData(OptionType.INTEGER, "players", "玩家人數", true).setRequiredRange(4, 20),
                                OptionData(OptionType.BOOLEAN, "double", "雙身分模式", false),
                            ),
                        SubcommandData("delete", "刪除目前伺服器的遊戲"),
                    ),
            ).queue()
        }

        override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
            if (event.name != "server") return
            val handler = commandHandler ?: return event.reply("尚未就緒").setEphemeral(true).queue()
            val reply = when (event.subcommandName) {
                "create" -> handler.onServerCreate(
                    event.user.idLong,
                    event.getOption("players")!!.asInt,
                    event.getOption("double")?.asBoolean ?: false,
                )
                "delete" -> handler.onServerDelete(event.user.idLong, event.guild?.idLong ?: 0)
                else -> "未知的指令"
            }
            event.reply(reply).setEphemeral(true).queue()
        }
    }

    /** Routes night-action select menus and wolf-kill vote buttons into the engine handler. */
    private inner class ComponentListener : ListenerAdapter() {
        override fun onButtonInteraction(event: ButtonInteractionEvent) {
            if (!event.componentId.startsWith(InteractionIds.PREFIX) || !event.isFromGuild) return
            val reply = interactionHandler?.handle(event.guild!!.idLong, event.user.idLong, event.componentId, emptyList())
            event.reply(reply?.ack ?: "已收到").setEphemeral(true).queue()
        }

        override fun onStringSelectInteraction(event: StringSelectInteractionEvent) {
            if (!event.componentId.startsWith(InteractionIds.PREFIX) || !event.isFromGuild) return
            val reply = interactionHandler?.handle(event.guild!!.idLong, event.user.idLong, event.componentId, event.values)
            event.reply(reply?.ack ?: "已收到").setEphemeral(true).queue()
        }
    }
}
