package dev.robothanzo.werewolf.service

import dev.robothanzo.werewolf.domain.GameSession
import dev.robothanzo.werewolf.game.GameConstants
import dev.robothanzo.werewolf.game.vote.PollContext

/**
 * Bridges a [GameSession] to the pure [PollContext] the [dev.robothanzo.werewolf.game.vote.PollEngine]
 * needs — the police vote weight, liveness, and the dead-白癡 vote loss. Kept out of `game/vote` so
 * that package stays free of the Mongo document.
 */
class SessionPollContext(private val session: GameSession) : PollContext {

    override fun weightOf(seat: Int): Double =
        if (seat == session.policeSeat) GameConstants.POLICE_VOTE_WEIGHT else 1.0

    override fun isAlive(seat: Int): Boolean = session.seat(seat)?.alive == true

    /** A 白癡 loses its vote once it has flipped its card on expel (ROLES.md 白癡), or once fully dead. */
    override fun isDeadIdiot(seat: Int): Boolean =
        session.seat(seat)?.let { it.idiot && (it.idiotRevealed || !it.alive) } == true
}
