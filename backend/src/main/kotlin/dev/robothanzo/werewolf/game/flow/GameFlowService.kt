package dev.robothanzo.werewolf.game.flow

import dev.robothanzo.werewolf.domain.Phase
import org.springframework.stereotype.Service

/** The next phase plus the day counter after a transition. */
data class PhaseTransition(val phase: Phase, val day: Int)

/**
 * The coarse day/night phase machine (FEATURES §6/§7). The police election runs only on day 1;
 * every other day goes straight from dawn into speeches. Kept as a pure function so the ordering is
 * unit-tested; the services layer wires each phase to the night engine, polls, speech, and Discord.
 */
@Service
class GameFlowService {

    fun next(phase: Phase, day: Int): PhaseTransition = when (phase) {
        Phase.LOBBY, Phase.ASSIGNMENT -> PhaseTransition(Phase.NIGHT, if (day < 1) 1 else day)
        Phase.NIGHT -> PhaseTransition(Phase.DAWN, day)
        Phase.DAWN, Phase.DAY -> if (day == 1) PhaseTransition(Phase.POLICE_ELECTION, day) else PhaseTransition(Phase.SPEECHES, day)
        Phase.POLICE_ELECTION -> PhaseTransition(Phase.SPEECHES, day)
        Phase.SPEECHES -> PhaseTransition(Phase.EXPEL_VOTE, day)
        Phase.EXPEL_VOTE -> PhaseTransition(Phase.NIGHT, day + 1)
        Phase.OVER -> PhaseTransition(Phase.OVER, day)
    }

    /** Starting the game from the lobby enters the first night. */
    fun start(): PhaseTransition = PhaseTransition(Phase.NIGHT, 1)
}
