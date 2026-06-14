package dev.robothanzo.werewolf.service

import dev.robothanzo.werewolf.discord.DiscordGateway
import dev.robothanzo.werewolf.discord.NicknameService
import dev.robothanzo.werewolf.domain.DashboardRole
import dev.robothanzo.werewolf.domain.GameSession
import dev.robothanzo.werewolf.domain.LogSeverity
import dev.robothanzo.werewolf.domain.Phase
import dev.robothanzo.werewolf.domain.Seat
import dev.robothanzo.werewolf.game.assign.AssignmentService
import dev.robothanzo.werewolf.game.roles.RoleRegistry
import dev.robothanzo.werewolf.game.win.WinConditionChecker
import dev.robothanzo.werewolf.game.flow.GameScheduler
import dev.robothanzo.werewolf.security.DashboardRoleService
import org.springframework.stereotype.Service
import kotlin.random.Random

/**
 * The judge's per-seat mutations (FEATURES §5/§9). Each method mutates the session in place;
 * the caller wraps it in [GameSessionService.mutate], which persists and broadcasts the snapshot.
 * Discord side-effects (nicknames) are kept in sync best-effort through the gateway.
 */
@Service
class GameActionService(
    private val sessionService: GameSessionService,
    private val assignment: AssignmentService,
    private val roles: RoleRegistry,
    private val win: WinConditionChecker,
    private val nicknames: NicknameService,
    private val gateway: DiscordGateway,
    private val discordOps: DiscordOpsService,
    private val gameScheduler: GameScheduler,
    private val roleService: DashboardRoleService,
    private val deaths: DeathService,
) {

    /** Deal identities to the eligible (non-bot, non-owner, non-spectator) members. */
    fun assign(guildId: Long) = sessionService.mutate(guildId) { session ->
        val eligible = gateway.listMembers(guildId)
            .filter { !it.bot && !it.owner && !it.spectator && roleService.roleFor(guildId, it.id) != DashboardRole.JUDGE }
            .map { it.id }
            .ifEmpty { session.seats.mapNotNull { it.memberId } } // dev fallback to existing bindings
        assignment.assign(session, eligible, Random.Default)
        session.assigned = true
        sessionService.log(
            guildId, LogSeverity.ACTION, "assign.completed",
            session.playerCount, session.pool.values.sum(),
        )
        // Critical role/nickname batch then notifications, streamed over WS (FEATURES §10.2).
        discordOps.applyAssignment(session)
    }

    /** Mark one identity of a seat dead (soft death) through the shared [DeathService] — which arms
     *  any 獵人 / 狼王 revenge and cascades 殉情 — then re-check win conditions. */
    fun kill(guildId: Long, seatNumber: Int, identityIndex: Int?, @Suppress("UNUSED_PARAMETER") lastWords: Boolean) =
        sessionService.mutate(guildId) { session ->
            val seat = session.seat(seatNumber) ?: error("seat $seatNumber not found")
            val idx = identityIndex ?: seat.cards.indexOfFirst { !it.dead }
            require(idx in seat.cards.indices) { "no living identity to kill" }
            val produced = deaths.applyDeath(session, seatNumber, seat.cards[idx], DeathCause.JUDGE)
            logDeaths(guildId, session, produced)
            checkWin(session)
        }

    /** Fire an armed 獵人 / 狼王 / 白狼王 revenge shot at [targetSeat] (FEATURES §5, ROLES.md). */
    fun revenge(guildId: Long, seatNumber: Int, targetSeat: Int) = sessionService.mutate(guildId) { session ->
        val seat = session.seat(seatNumber) ?: error("seat $seatNumber not found")
        require(seat.revengePending) { "seat $seatNumber has no pending revenge" }
        seat.revengePending = false
        // The shot itself is a normal death (so a 狼王 can chain its own 殉情); the target keeps its abilities.
        val produced = deaths.killSeat(session, targetSeat, DeathCause.JUDGE)
        sessionService.log(guildId, LogSeverity.ACTION, "day.revenge", seat.paddedNumber, String.format("%02d", targetSeat))
        logDeaths(guildId, session, produced)
        checkWin(session)
    }

    /** Announce every death the shared applier produced (the primary death plus any 殉情 cascade). */
    private fun logDeaths(guildId: Long, session: GameSession, produced: List<DeathInfo>) {
        produced.forEach { d ->
            val paddedNumber = session.seat(d.seat)?.paddedNumber ?: String.format("%02d", d.seat)
            sessionService.log(guildId, LogSeverity.ALERT, "death.announce", paddedNumber, roles.localizedName(d.roleId))
        }
    }

    /** Revive the whole seat or a single named identity. */
    fun revive(guildId: Long, seatNumber: Int, identityIndex: Int?) = sessionService.mutate(guildId) { session ->
        val seat = session.seat(seatNumber) ?: error("seat $seatNumber not found")
        if (identityIndex != null) {
            seat.cards.getOrNull(identityIndex)?.let {
                it.dead = false
                sessionService.log(guildId, LogSeverity.ACTION, "revive.identity", seat.paddedNumber, roles.localizedName(it.roleId))
            }
        } else {
            seat.cards.forEach { it.dead = false }
            sessionService.log(guildId, LogSeverity.ACTION, "revive.seat", seat.paddedNumber)
        }
        syncNickname(session, seat)
    }

    /** Edit a seat's identities / order lock from the dashboard editor. */
    fun editIdentities(guildId: Long, seatNumber: Int, roleIds: List<String>, orderLocked: Boolean?) =
        sessionService.mutate(guildId) { session ->
            val seat = session.seat(seatNumber) ?: error("seat $seatNumber not found")
            roleIds.forEachIndexed { i, roleId ->
                seat.cards.getOrNull(i)?.let { it.dead = it.dead && roleId == it.roleId }
                if (i < seat.cards.size) seat.cards[i] = seat.cards[i].copy(roleId = roleId)
            }
            orderLocked?.let { seat.orderLocked = it }
        }

    /** Force-assign the police badge to a seat (judge action). */
    fun setPolice(guildId: Long, seatNumber: Int) = sessionService.mutate(guildId) { session ->
        session.seats.forEach { it.police = false }
        session.seat(seatNumber)?.let { it.police = true; syncNickname(session, it) }
        session.policeSeat = seatNumber
        sessionService.log(guildId, LogSeverity.ACTION, "police.auto_elected", String.format("%02d", seatNumber))
    }

    /** Transfer the police badge between two seats and refresh both nicknames. */
    fun transferPolice(guildId: Long, fromSeat: Int, toSeat: Int) = sessionService.mutate(guildId) { session ->
        session.seat(fromSeat)?.let { it.police = false; syncNickname(session, it) }
        session.seat(toSeat)?.let { it.police = true; syncNickname(session, it) }
        session.policeSeat = toSeat
        sessionService.log(
            guildId, LogSeverity.ACTION, "police.transferred",
            String.format("%02d", fromSeat), String.format("%02d", toSeat),
        )
    }

    /** Return the server to the pre-assignment state. */
    fun reset(guildId: Long) = sessionService.mutate(guildId) { session ->
        discordOps.applyReset(session) // captures member ids before we clear the bindings below
        session.seats.forEach { seat ->
            seat.memberId = null
            seat.cards.clear()
            seat.police = false
            seat.goldenBaby = false
            seat.clone = false
            seat.idiot = false
            seat.orderLocked = false
        }
        session.assigned = false
        session.policeSeat = null
        session.phase = Phase.LOBBY
        session.day = 0
        session.timerEndsAt = null
        session.stepEndsAt = null
        session.speech = null
        session.poll = null
        session.wolfChat.clear()
        sessionService.clearLogs(guildId)
        sessionService.log(guildId, LogSeverity.ACTION, "game.reset")
        gameScheduler.cancelAll(guildId)
    }

    private fun checkWin(session: GameSession) {
        val result = win.check(session)
        if (result.over) {
            session.phase = Phase.OVER
            val winnerKey = if (result.winner?.name == "WOLF") "game.over.wolf" else "game.over.good"
            sessionService.log(session.guildId, LogSeverity.ALERT, winnerKey)
            result.reasonKey?.let { sessionService.log(session.guildId, LogSeverity.INFO, it) }
        }
    }

    private fun syncNickname(session: GameSession, seat: Seat) {
        val memberId = seat.memberId ?: return
        if (!gateway.canInteract(session.guildId, memberId)) return
        gateway.setNickname(session.guildId, memberId, nicknames.nicknameFor(seat))
    }
}
