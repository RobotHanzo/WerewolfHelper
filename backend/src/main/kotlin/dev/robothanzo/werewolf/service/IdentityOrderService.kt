package dev.robothanzo.werewolf.service

import dev.robothanzo.werewolf.discord.DiscordGateway
import dev.robothanzo.werewolf.discord.DiscordInteractionHandler
import dev.robothanzo.werewolf.discord.InteractionIds
import dev.robothanzo.werewolf.discord.InteractionReply
import dev.robothanzo.werewolf.domain.GameSession
import dev.robothanzo.werewolf.domain.LogSeverity
import dev.robothanzo.werewolf.domain.Seat
import dev.robothanzo.werewolf.game.GameConstants
import dev.robothanzo.werewolf.game.flow.GameScheduler
import dev.robothanzo.werewolf.game.roles.RoleRegistry
import dev.robothanzo.werewolf.i18n.Msg
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Service

/**
 * Owns the double-identity **identity-order swap** window (FEATURES §5.3). When assignment finishes
 * a [GameConstants.ORDER_LOCK_SECONDS]-second countdown starts: players may swap their two cards via
 * the 調換順序 button on their assignment announcement (the seat reveals/loses identities in card
 * order, so which sits first matters). Seats are reminded at the
 * [GameConstants.ORDER_LOCK_REMINDER_AT_SECONDS] marks, and on the deadline every seat's order locks.
 *
 * Registers itself as the `wh:assign:...` interaction handler (post-construct, like the orchestrators).
 */
@Service
class IdentityOrderService(
    private val sessionService: GameSessionService,
    private val gateway: DiscordGateway,
    private val scheduler: GameScheduler,
    private val roles: RoleRegistry,
    private val msg: Msg,
    private val router: InteractionRouter,
) : DiscordInteractionHandler {

    @PostConstruct
    fun register() = router.register(InteractionIds.NS_ASSIGN, this)

    /** Begin the lock countdown + reminders. No-op outside double-identity (single cards never swap). */
    fun startOrderLock(guildId: Long) {
        val session = sessionService.find(guildId) ?: return
        if (!session.settings.doubleIdentity) return
        val durationMs = GameConstants.ORDER_LOCK_SECONDS * 1000L
        val endsAt = System.currentTimeMillis() + durationMs
        sessionService.mutate(guildId) { s ->
            s.seats.filter { it.assigned }.forEach { it.orderLocked = false }
            s.orderLockEndsAt = endsAt
        }
        scheduler.schedule(guildId, GameScheduler.ORDER_LOCK, durationMs) { lockOrder(guildId) }
        GameConstants.ORDER_LOCK_REMINDER_AT_SECONDS.forEach { mark ->
            val delay = durationMs - mark * 1000L
            if (delay > 0) {
                scheduler.schedule(guildId, "${GameScheduler.ORDER_LOCK_REMINDER}.$mark", delay) {
                    remindPending(guildId, mark)
                }
            }
        }
    }

    /** Cancel a running countdown + its reminders (game reset). */
    fun cancel(guildId: Long) {
        scheduler.cancel(guildId, GameScheduler.ORDER_LOCK)
        GameConstants.ORDER_LOCK_REMINDER_AT_SECONDS.forEach {
            scheduler.cancel(guildId, "${GameScheduler.ORDER_LOCK_REMINDER}.$it")
        }
    }

    /** DM every still-unlocked double-identity seat that the order locks in [secondsLeft] seconds. */
    private fun remindPending(guildId: Long, secondsLeft: Int) {
        val session = sessionService.find(guildId) ?: return
        if (session.orderLockEndsAt == null) return
        session.seats.filter { it.assigned && !it.orderLocked && it.cards.size > 1 }
            .forEach { gateway.sendSeatMessage(guildId, it.number, msg.msg("assign.order.reminder", secondsLeft)) }
    }

    /** Lock every seat's order and tell each seat channel; clears the countdown. */
    private fun lockOrder(guildId: Long) {
        cancel(guildId)
        var seatNumbers: List<Int> = emptyList()
        sessionService.mutate(guildId) { session ->
            if (session.orderLockEndsAt == null) return@mutate
            session.seats.filter { it.assigned }.forEach { it.orderLocked = true }
            session.orderLockEndsAt = null
            seatNumbers = session.seats.filter { it.assigned && it.cards.size > 1 }.map { it.number }
        }
        seatNumbers.forEach { gateway.sendSeatMessage(guildId, it, msg.msg("assign.order.locked")) }
    }

    override fun handle(guildId: Long, userId: Long, channelId: Long, customId: String, values: List<String>): InteractionReply? {
        if (customId != InteractionIds.SWAP_ORDER) return null
        val session = sessionService.find(guildId) ?: return null
        // A player swaps their own seat; a judge (who sees every seat channel) swaps on that seat's behalf.
        val ownSeat = session.seats.firstOrNull { it.memberId == userId }?.number
        val channelSeat = session.seats.firstOrNull { it.channelId == channelId && it.channelId != 0L }?.number
        val seatNumber = when {
            channelSeat != null && gateway.isJudge(guildId, userId) -> channelSeat
            ownSeat != null -> ownSeat
            else -> return InteractionReply("你不是這場遊戲的玩家")
        }
        val seat = session.seat(seatNumber) ?: return null
        if (seat.cards.size < 2) return InteractionReply("你只有一個身分，無法調換順序")
        if (seat.orderLocked || session.orderLockEndsAt == null) return InteractionReply(msg.msg("assign.order.refused"))

        var order = ""
        sessionService.mutate(guildId) { s ->
            val live = s.seat(seatNumber) ?: return@mutate
            live.cards.reverse()
            order = live.cards.joinToString("、") { roles.localizedName(it.roleId) }
        }
        sessionService.log(guildId, LogSeverity.ACTION, "assign.order.swapped_seat", seat.paddedNumber)
        return InteractionReply("${msg.msg("assign.order.swapped")}：$order")
    }
}
