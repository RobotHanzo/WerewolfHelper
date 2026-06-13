package dev.robothanzo.werewolf.service

import dev.robothanzo.werewolf.discord.ChannelKind
import dev.robothanzo.werewolf.discord.DiscordGateway
import dev.robothanzo.werewolf.discord.NicknameService
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
    private val gateway: DiscordGateway,
    private val nicknames: NicknameService,
    private val engine: BulkOperationEngine,
    private val roles: RoleRegistry,
    private val ws: GameWebSocketHandler,
    private val msg: Msg,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun sink(guildId: Long): ProgressSink =
        ProgressSink { percent, line, severity -> ws.broadcastProgress(guildId, percent, line, severity.name.lowercase()) }

    /** Apply the assignment's Discord side-effects: critical role/nickname batch, then notifications. */
    fun applyAssignment(session: GameSession) {
        if (!gateway.available) {
            scope.launch {
                ws.broadcastProgress(session.guildId, 100, msg.msg("bulk.finished"), "info")
            }
            return
        }
        scope.launch {
            val guildId = session.guildId
            val critical = BulkPhase(
                "critical", 0, 90,
                session.seats.filter { it.assigned }.flatMap { seat ->
                    val memberId = seat.memberId!!
                    listOf(
                        BulkItem("玩家${seat.paddedNumber} 角色") { gateway.grantSeatRole(guildId, memberId, seat.number) },
                        BulkItem("玩家${seat.paddedNumber} 暱稱") { gateway.setNickname(guildId, memberId, nicknames.nicknameFor(seat)) },
                    )
                },
            )
            val notify = BulkPhase(
                "notify", 90, 100,
                session.seats.filter { it.assigned }.map { seat ->
                    BulkItem("玩家${seat.paddedNumber} 身分通知") {
                        val ids = seat.cards.joinToString("、") { roles.localizedName(it.roleId) }
                        gateway.sendSeatMessage(guildId, seat.number, "${msg.msg("assign.dm.title")}：$ids")
                    }
                },
            )
            engine.execute(listOf(critical, notify), sink(guildId))
            gateway.sendChannelMessage(guildId, ChannelKind.JUDGE, msg.msg("assign.summary.title"))
        }
    }

    /** Reset the Discord side: clear each member's nickname/roles. Member ids are captured eagerly
     *  because the caller clears the bindings synchronously right after. */
    fun applyReset(session: GameSession) {
        if (!gateway.available) {
            scope.launch {
                ws.broadcastProgress(session.guildId, 100, msg.msg("bulk.finished"), "info")
            }
            return
        }
        val guildId = session.guildId
        val memberIds = session.seats.mapNotNull { it.memberId }
        scope.launch {
            val phase = BulkPhase(
                "reset", 0, 100,
                memberIds.map { memberId -> BulkItem("成員 $memberId 重置") { gateway.resetMember(guildId, memberId) } },
            )
            engine.execute(listOf(phase), sink(guildId))
        }
    }


    @PreDestroy
    fun shutdown() = scope.cancel()
}
