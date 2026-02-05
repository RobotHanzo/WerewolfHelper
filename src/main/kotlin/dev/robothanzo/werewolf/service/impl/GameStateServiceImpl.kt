package dev.robothanzo.werewolf.service.impl

import dev.robothanzo.werewolf.database.documents.LogType
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.GameStep
import dev.robothanzo.werewolf.game.model.GameStateData
import dev.robothanzo.werewolf.service.GameSessionService
import dev.robothanzo.werewolf.service.GameStateService
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class GameStateServiceImpl(
    private val sessionService: GameSessionService,
    stepList: List<GameStep>
) : GameStateService {

    private val steps = ConcurrentHashMap<String, GameStep>()

    init {
        stepList.forEach { registerStep(it) }
        // Ensure unknown step doesn't crash on retrieval if needed, but better to be strict
    }

    // Simple robust ordered flow for now, can be made dynamic later
    private val gameFlow = listOf(
        "NIGHT_PHASE",
        "SHERIFF_ELECTION", // Day 1 only
        "DEATH_ANNOUNCEMENT",
        "DEATH_TRIGGER", // Hunter/Wolf King revenge
        "SPEECH_PHASE",
        "VOTING_PHASE"
    )

    override fun registerStep(step: GameStep) {
        steps[step.id] = step
    }

    override fun getStep(stepId: String): GameStep? {
        return steps[stepId]
    }

    override fun getAvailableSteps(): List<GameStep> {
        return steps.values.toList()
    }

    override fun getCurrentStep(session: Session): GameStep? {
        return steps[session.currentState]
    }

    override fun startStep(session: Session, stepId: String) {
        val currentStep = steps[session.currentState]
        currentStep?.onEnd(session, this)

        val nextStep = steps[stepId] ?: throw IllegalArgumentException("Unknown step: $stepId")

        val pendingActionsSnapshot = session.stateData.pendingActions.toMutableList()

        session.currentState = stepId
        // Reset state data for new step
        session.stateData = GameStateData()

        if (pendingActionsSnapshot.isNotEmpty()) {
            session.stateData.pendingActions.addAll(pendingActionsSnapshot)
        }

        // Timer Logic
        val duration = nextStep.getDurationSeconds(session)
        if (duration > 0) {
            session.currentStepEndTime = System.currentTimeMillis() + (duration * 1000)
        } else {
            session.currentStepEndTime = 0
        }

        sessionService.saveSession(session)

        // Log the transition
        session.addLog(LogType.SYSTEM, "進入階段: ${nextStep.name}")

        nextStep.onStart(session, this)
    }

    override fun nextStep(session: Session) {
        val currentId = session.currentState

        // Custom logic for flow control
        if (currentId == "SETUP") {
            startStep(session, "NIGHT_PHASE")
            return
        }

        // Find next in list
        val idx = gameFlow.indexOf(currentId)
        if (idx == -1) {
            // Fallback or error
            return
        }

        var nextIdx = (idx + 1) % gameFlow.size
        var nextId = gameFlow[nextIdx]

        // Handle cycles (Day increment)
        if (nextId == "NIGHT_PHASE") {
            session.day += 1
            sessionService.saveSession(session)
            session.addLog(LogType.SYSTEM, "進入第 ${session.day} 天")
        }

        // Conditional Skips
        if (nextId == "SHERIFF_ELECTION" && session.day != 1) {
            // Skip sheriff election after day 1
            nextIdx = (nextIdx + 1) % gameFlow.size
            nextId = gameFlow[nextIdx]
        }

        startStep(session, nextId)
    }

    override fun handleInput(session: Session, input: Map<String, Any>): Map<String, Any> {
        val step = steps[session.currentState] ?: throw IllegalStateException("No active step")
        val result = step.handleInput(session, input)
        // Save session after handling input to persist any changes
        if (result["success"] == true) {
            sessionService.saveSession(session)
            // If votingEnded flag is set, automatically advance to next step
            if (result["votingEnded"] == true) {
                nextStep(session)
            }
        }
        return result
    }
}
