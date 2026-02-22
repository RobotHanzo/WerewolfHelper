package dev.robothanzo.werewolf.service.impl

import dev.robothanzo.werewolf.database.documents.LogType
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.GameStep
import dev.robothanzo.werewolf.service.GameSessionService
import dev.robothanzo.werewolf.service.GameStateService
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class GameStateServiceImpl(
    private val sessionService: GameSessionService,
    @param:org.springframework.context.annotation.Lazy private val speechService: dev.robothanzo.werewolf.service.SpeechService,
    @param:org.springframework.context.annotation.Lazy private val policeService: dev.robothanzo.werewolf.service.PoliceService,
    @param:org.springframework.context.annotation.Lazy private val expelService: dev.robothanzo.werewolf.service.ExpelService,
    stepList: List<GameStep>
) : GameStateService {

    private val steps = ConcurrentHashMap<String, GameStep>()

    init {
        stepList.forEach { registerStep(it) }
        // Ensure unknown step doesn't crash on retrieval if needed, but better to be strict
    }

    // Simple robust ordered flow for now, can be made dynamic later
    private val gameFlow = listOf(
        "NIGHT_STEP",
        "DAY_STEP",
        "SHERIFF_ELECTION", // Day 1 only
        "DEATH_ANNOUNCEMENT",
        "SPEECH_STEP",
        "VOTING_STEP"
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
        if (session.stateData.paused) {
            session.addLog(LogType.SYSTEM, "遊戲暫停中，無法切換階段")
            return
        }

        val currentStep = steps[session.currentState]
        currentStep?.onEnd(session, this)

        val nextStep = steps[stepId] ?: throw IllegalArgumentException("Unknown step: $stepId")

        session.currentState = stepId

        sessionService.saveSession(session)

        // Log the transition
        session.addLog(LogType.SYSTEM, "進入階段: ${nextStep.name}")

        nextStep.onStart(session, this)
    }

    override fun nextStep(session: Session) {
        if (session.stateData.paused) {
            session.addLog(LogType.SYSTEM, "遊戲暫停中，無法切換階段")
            return
        }

        val currentId = session.currentState
        if (currentId == "SETUP") {
            if (session.stateData.gameStartTime == 0L) {
                session.stateData.gameStartTime = System.currentTimeMillis()
            }
            startStep(session, "NIGHT_STEP")
            return
        }

        val idx = gameFlow.indexOf(currentId)
        if (idx == -1) return

        var nextIdx = (idx + 1) % gameFlow.size
        var nextId = gameFlow[nextIdx]

        // Conditional Skips
        if (nextId == "SHERIFF_ELECTION" && session.day != 0) {
            // Skip sheriff election after night 1 (which has day 0)
            nextIdx = (nextIdx + 1) % gameFlow.size
            nextId = gameFlow[nextIdx]
        }

        // Skip voting and jump to night if detonation occurred
        if ((nextId == "SPEECH_STEP" || nextId == "VOTING_STEP") && session.stateData.detonatedThisDay) {
            // If detonated, go straight to night
            while (nextId != "NIGHT_STEP") {
                nextIdx = (nextIdx + 1) % gameFlow.size
                nextId = gameFlow[nextIdx]
            }
        }

        // Handle cycles (Day increment at sunrise)
        if (nextId == "DEATH_ANNOUNCEMENT") {
            session.day += 1
            sessionService.saveSession(session)
            session.addLog(LogType.SYSTEM, "進入第 ${session.day} 天")
        }

        // Check for Game End
        // We only check if we are NOT already in JUDGE_DECISION
        // Defer game end check if we are heading into DEATH_ANNOUNCEMENT to allow resolution results to be shown.
        if (nextId != "DEATH_ANNOUNCEMENT") {
            val endResult = session.hasEnded(null)
            if (endResult != Session.Result.NOT_ENDED) {
                session.stateData.pendingNextStep = nextId
                session.stateData.gameEndReason = endResult.reason
                startStep(session, "JUDGE_DECISION")
                return
            }
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

    override fun pauseStep(session: Session) {
        if (session.stateData.paused) return
        session.stateData.paused = true
        session.stateData.pauseStartTime = System.currentTimeMillis()
        sessionService.saveSession(session)
        session.addLog(LogType.SYSTEM, "遊戲暫停")
        sessionService.broadcastSessionUpdate(session)
    }

    override fun resumeStep(session: Session) {
        if (!session.stateData.paused) return
        val pauseTime = session.stateData.pauseStartTime ?: return
        val now = System.currentTimeMillis()
        val pausedDuration = now - pauseTime

        // shifting the start and end times by the duration the game was paused
        session.stateData.stepStartTime += pausedDuration

        if (session.stateData.phaseType != null) {
            session.stateData.phaseStartTime += pausedDuration
            session.stateData.phaseEndTime += pausedDuration
        }

        // Adjust specific step end times if needed (e.g. Speech Status, Expel Status, Police Status etc.)
        session.stateData.speech?.let {
            speechService.extendSpeechEndTime(session.guildId, pausedDuration)
            session.stateData.speech = it.copy(endTime = it.endTime + pausedDuration)
        }
        session.stateData.police?.let {
            if (it.stageEndTime != null) {
                policeService.extendPoliceStageEndTime(session.guildId, pausedDuration)
                session.stateData.police = it.copy(stageEndTime = it.stageEndTime + pausedDuration)
            }
        }
        session.stateData.expel?.let {
            if (it.endTime != null) {
                expelService.extendExpelPollEndTime(session.guildId, pausedDuration)
                session.stateData.expel = it.copy(endTime = it.endTime + pausedDuration)
            }
        }

        session.stateData.paused = false
        session.stateData.pauseStartTime = null

        sessionService.saveSession(session)
        session.addLog(LogType.SYSTEM, "遊戲繼續")
        sessionService.broadcastSessionUpdate(session)
    }
}
