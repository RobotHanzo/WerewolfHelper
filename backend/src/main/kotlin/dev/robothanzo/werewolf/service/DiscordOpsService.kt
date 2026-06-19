package dev.robothanzo.werewolf.service

import dev.robothanzo.werewolf.discord.ChannelKind
import dev.robothanzo.werewolf.discord.InteractionIds
import dev.robothanzo.werewolf.discord.NicknameService
import dev.robothanzo.werewolf.discord.clearNickname
import dev.robothanzo.werewolf.discord.grantSeatRole
import dev.robothanzo.werewolf.discord.removeSeatRoles
import dev.robothanzo.werewolf.discord.sendChannelEmbed
import dev.robothanzo.werewolf.discord.sendSeatEmbed
import dev.robothanzo.werewolf.discord.setNickname
import dev.robothanzo.werewolf.domain.GameSession
import dev.robothanzo.werewolf.game.roles.RoleRegistry
import dev.robothanzo.werewolf.i18n.Msg
import dev.robothanzo.werewolf.ops.BulkItem
import dev.robothanzo.werewolf.ops.BulkOperationEngine
import dev.robothanzo.werewolf.ops.BulkPhase
import dev.robothanzo.werewolf.ops.ProgressSink
import dev.robothanzo.werewolf.websocket.GameWebSocketHandler
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.components.buttons.Button
import org.springframework.stereotype.Service

/**
 * Runs the long, Discord-touching operations (assignment, reset) through the bulk engine, streaming
 * percent + per-item log lines over the WebSocket progress channel.
 *
 * Critical-before-cosmetic (FEATURES §10.2): role grants and nickname changes complete as their own
 * fully-awaited phase **before** any notification messages are sent, so a game never starts with
 * players missing roles/nicknames.
 */
@Service
class DiscordOpsService(
    private val jda: JDA?,
    private val nicknames: NicknameService,
    private val engine: BulkOperationEngine,
    private val roles: RoleRegistry,
    private val ws: GameWebSocketHandler,
    private val msg: Msg,
    private val orderService: IdentityOrderService,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun sink(guildId: Long): ProgressSink =
        ProgressSink { percent, line, severity -> ws.broadcastProgress(guildId, percent, line, severity.name.lowercase()) }

    /** Apply the assignment's Discord side-effects: critical role/nickname batch, then notifications. */
    fun applyAssignment(session: GameSession) {
        val jda = jda
        if (jda == null) {
            scope.launch { ws.broadcastProgress(session.guildId, 100, msg.msg("bulk.finished"), "info") }
            return
        }
        scope.launch {
            val guildId = session.guildId
            // One player at a time: each item blocks (await = true) until Discord confirms, so the role
            // grant finishes before the nickname, and a player fully completes before the next starts.
            val critical = BulkPhase(
                "critical", 0, 90,
                session.seats.filter { it.assigned }.flatMap { seat ->
                    val memberId = seat.memberId!!
                    listOf(
                        BulkItem(msg.msg("bulk.item.assign_role", seat.paddedNumber)) { jda.grantSeatRole(session, memberId, seat.number, await = true) },
                        BulkItem(msg.msg("bulk.item.assign_nickname", seat.paddedNumber)) { jda.setNickname(session, memberId, nicknames.nicknameFor(seat), await = true) },
                    )
                },
            )
            // Double identity: each announcement carries a 調換順序 button so the player can swap their
            // two cards' order before the lock countdown (FEATURES §5.3).
            val swapButtons = if (session.settings.doubleIdentity)
                listOf(Button.secondary(InteractionIds.SWAP_ORDER, msg.msg("assign.order.swap_button")))
            else emptyList()
            // 金寶寶 coordinate as a team (their own private cross-chat group) — tell each one who their
            // partner is, or that they are the only 金寶寶 on the board (FEATURES §5.3 / §284).
            val goldenBabies = session.seats.filter { it.assigned && it.goldenBaby }
            val notify = BulkPhase(
                "notify", 90, 100,
                session.seats.filter { it.assigned }.map { seat ->
                    BulkItem(msg.msg("bulk.item.assign_notify", seat.paddedNumber)) {
                        val lines = mutableListOf(seat.cards.joinToString("、") { roles.localizedName(it.roleId) })
                        if (seat.goldenBaby) {
                            val others = goldenBabies.filter { it.number != seat.number }
                            lines += if (others.isEmpty()) msg.msg("assign.dm.golden_baby.solo")
                            else msg.msg("assign.dm.golden_baby.partners", others.joinToString("、") { "玩家${it.paddedNumber}" })
                        }
                        val embed = EmbedBuilder()
                            .setTitle(msg.msg("assign.dm.title"))
                            .setDescription(lines.joinToString("\n"))
                            .setColor(0xE8B923)
                        jda.sendSeatEmbed(session, seat.number, embed, if (seat.cards.size > 1) swapButtons else emptyList())
                    }
                },
            )
            engine.execute(listOf(critical, notify), sink(guildId))
            // Now that announcements are out, start the swap window's lock countdown (double identity only).
            orderService.startOrderLock(guildId)
            val summaryEmbed = EmbedBuilder()
                .setTitle(msg.msg("assign.summary.title"))
                .setColor(kotlin.random.Random.nextInt(0x1000000))
                .apply {
                    session.seats.filter { it.assigned }.forEach { seat ->
                        addField("玩家${seat.paddedNumber}", seat.cards.joinToString("、") { roles.localizedName(it.roleId) }, true)
                    }
                }
            jda.sendChannelEmbed(session, ChannelKind.JUDGE, summaryEmbed)
            jda.sendChannelEmbed(session, ChannelKind.SPECTATOR, summaryEmbed)
        }
    }

    /** Reset the Discord side: clear each member's nickname/roles. Member ids are captured eagerly
     *  because the caller clears the bindings synchronously right after. */
    fun applyReset(session: GameSession) {
        val jda = jda
        if (jda == null) {
            scope.launch { ws.broadcastProgress(session.guildId, 100, msg.msg("bulk.finished"), "info") }
            return
        }
        val guildId = session.guildId
        val seated = session.seats.filter { it.memberId != null }.map { it.paddedNumber to it.memberId!! }
        scope.launch {
            // Role removal then nickname reset, as two separate progress steps per player (one by one).
            val phase = BulkPhase(
                "reset", 0, 100,
                seated.flatMap { (paddedNumber, memberId) ->
                    listOf(
                        BulkItem(msg.msg("bulk.item.reset_role", paddedNumber)) { jda.removeSeatRoles(session, memberId) },
                        BulkItem(msg.msg("bulk.item.reset_nickname", paddedNumber)) { jda.clearNickname(session, memberId) },
                    )
                },
            )
            engine.execute(listOf(phase), sink(guildId))
        }
    }


    @PreDestroy
    fun shutdown() = scope.cancel()
}
