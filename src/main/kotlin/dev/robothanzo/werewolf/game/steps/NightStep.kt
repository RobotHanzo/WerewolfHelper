package dev.robothanzo.werewolf.game.steps

import dev.robothanzo.werewolf.audio.Audio
import dev.robothanzo.werewolf.audio.Audio.play
import dev.robothanzo.werewolf.database.documents.LogType
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.GameStep
import dev.robothanzo.werewolf.game.listeners.RoleEventListener
import dev.robothanzo.werewolf.game.model.*
import dev.robothanzo.werewolf.game.roles.RoleRegistry
import dev.robothanzo.werewolf.game.roles.actions.RoleActionExecutor
import dev.robothanzo.werewolf.game.steps.tasks.*
import dev.robothanzo.werewolf.service.ActionUIService
import dev.robothanzo.werewolf.service.GameSessionService
import dev.robothanzo.werewolf.service.GameStateService
import dev.robothanzo.werewolf.service.SpeechService
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import kotlin.jvm.optionals.getOrNull

@Component
class NightStep(
    internal val speechService: SpeechService,
    @param:Lazy
    internal val actionUIService: ActionUIService,
    internal val roleRegistry: RoleRegistry,
    internal val roleActionExecutor: RoleActionExecutor,
    @param:Lazy
    internal val gameSessionService: GameSessionService
) : GameStep(), RoleEventListener {
    internal val phaseSignals =
        ConcurrentHashMap<Long, CompletableDeferred<Unit>>()

    override fun getInterestedEvents() = listOf(RoleEventType.ACTION_PROCESSED)

    override fun onEvent(
        session: Session,
        eventType: RoleEventType,
        metadata: Map<String, Any>
    ) {
        if (eventType == RoleEventType.ACTION_PROCESSED) {
            phaseSignals[session.guildId]?.complete(Unit)
        }
    }

    override val id = "NIGHT_PHASE"
    override val name = "天黑請閉眼"

    private val log = LoggerFactory.getLogger(NightStep::class.java)
    internal val nightScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val orchestrationJobs = ConcurrentHashMap<Long, Job>()
    internal val activeQueues = ConcurrentHashMap<Long, Deque<NightTask>>()

    override fun onStart(session: Session, service: GameStateService) {
        super.onStart(session, service)
        val guildId = session.guildId

        gameSessionService.withLockedSession(guildId) { session ->
            // Mute everyone
            speechService.setAllMute(guildId, true)
            session.courtTextChannel?.sendMessage("# **:crescent_moon: 天黑請閉眼**")?.queue()
            session.courtVoiceChannel?.play(Audio.Resource.NIGHT)

            // Reset night data
            session.stateData.submittedActions.clear()
            session.stateData.wolfStates.clear()
            session.stateData.werewolfMessages.clear()

            // Initialize night status.
            val now = System.currentTimeMillis()
            session.stateData.phaseStartTime = now
            session.stateData.phaseEndTime = now + 90_000 // Placeholder

            for (p in session.players.values) {
                p.actionSubmitted = false
                if (p.alive) {
                    if (p.wolf) {
                        // Wolf Younger Brother (狼弟) joins the pack ONLY IF the Brother has died.
                        val wolfBrotherDiedDay = session.stateData.wolfBrotherDiedDay
                        if (p.roles.contains("狼弟") && wolfBrotherDiedDay == null) {
                            continue
                        }
                    } else {
                        val actions = session.getAvailableActionsForPlayer(p.id, roleRegistry)
                        if (actions.isNotEmpty()) {
                            session.stateData.submittedActions.add(
                                RoleActionInstance(
                                    actor = p.id,
                                    actorRole = (p.roles.firstOrNull() ?: "未知"),
                                    actionDefinitionId = null, // Not chosen yet
                                    targets = arrayListOf(),
                                    submittedBy = ActionSubmissionSource.PLAYER,
                                    status = ActionStatus.PENDING
                                )
                            )
                        }
                    }
                }
            }
        }

        // Orchestrate the night phases
        orchestrationJobs[guildId]?.cancel()
        orchestrationJobs[guildId] = nightScope.launch {
            try {
                processNightPhases(guildId, service)
            } catch (_: CancellationException) {
                log.info("Night orchestration for guild $guildId was cancelled")
            } catch (e: Exception) {
                log.error("Error during night orchestration for guild $guildId", e)
            } finally {
                orchestrationJobs.remove(guildId, coroutineContext[Job])
            }
        }
    }

    internal suspend fun processNightPhases(
        guildId: Long,
        service: GameStateService,
        tasks: List<NightTask> = NightSequence.TASKS
    ) {
        val session = gameSessionService.getSession(guildId).orElse(null)
        val filteredTasks = if (session != null) tasks.filter { it.shouldExecute(session) } else tasks
        val taskQueue = ConcurrentLinkedDeque(filteredTasks)
        activeQueues[guildId] = taskQueue
        log.info("Starting Night Phase Queue processing for guild $guildId with ${taskQueue.size} tasks")

        try {
            while (!taskQueue.isEmpty()) {
                val currentTask = taskQueue.poll() ?: break
                log.debug(
                    "Processing Night Task: {} for phase {}",
                    currentTask.javaClass.simpleName,
                    currentTask.phase
                )

                // Execute the task
                val shouldContinuePhase = try {
                    currentTask.execute(this, guildId)
                } catch (e: Exception) {
                    log.error("Error executing night task ${currentTask.phase}", e)
                    false // Stop phase on error
                }

                // If the task signals to stop the phase (e.g., done early), remove remaining tasks of this phase
                if (!shouldContinuePhase) {
                    log.info("Phase ${currentTask.phase} finished early or skipped. Removing remaining tasks for this phase.")
                    taskQueue.removeIf { it.phase == currentTask.phase && it.isSkippable }
                }
            }
        } finally {
            activeQueues.remove(guildId)
        }

        gameSessionService.getSession(guildId).getOrNull()?.addLog(LogType.SYSTEM, "夜晚結束，天亮了")
        gameSessionService.withLockedSession(guildId) { lockedSession ->
            service.nextStep(lockedSession)
        }
    }

    internal suspend fun waitForCondition(guildId: Long, durationSeconds: Int, condition: () -> Boolean): Boolean {
        val timeoutAt = System.currentTimeMillis() + durationSeconds * 1000L

        while (System.currentTimeMillis() < timeoutAt) {
            if (condition()) return true

            val signal = CompletableDeferred<Unit>()
            phaseSignals[guildId] = signal

            val remainingMs = timeoutAt - System.currentTimeMillis()
            if (remainingMs <= 0) break

            try {
                // Wait for either onEvent (signal) or 1 second poll via timeout
                withTimeout(minOf(remainingMs, 1000L)) {
                    signal.await()
                }
            } catch (_: Exception) {
                // Ignore timeout (which acts as a poll) or other exceptions, loop and re-check condition
            } finally {
                phaseSignals.remove(guildId)
            }
        }

        return condition()
    }

    override fun onEnd(session: Session, service: GameStateService) {
        val guildId = session.guildId
        log.info("Ending NightStep for guild $guildId. Cleaning up state.")

        // 1. Cancel orchestration coroutine
        orchestrationJobs.remove(guildId)?.cancel()

        // 2. Clear phase signals
        phaseSignals.remove(guildId)

        // 3. Cleanup UI prompts
        actionUIService.cleanupExpiredPrompts(session)

        // 4. Clear transient night state
        session.stateData.phaseType = null
        session.stateData.phaseStartTime = 0
        session.stateData.phaseEndTime = 0
        session.stateData.wolfBrotherAwakenedPlayerId = null

        // 5. Clear any PENDING or ACTING actions (Keep only SUBMITTED/SKIPPED/PROCESSED for resolution)
        session.stateData.submittedActions.removeIf { !it.status.executed }

        // 6. Clear wolf data
        session.stateData.wolfStates.clear()
        session.stateData.werewolfMessages.clear()

        gameSessionService.saveSession(session)
    }

    override fun handleInput(session: Session, input: Map<String, Any>): Map<String, Any> {
        return mapOf("success" to false)
    }

    override fun getEndTime(session: Session): Long {
        val now = System.currentTimeMillis()
        val phaseType = session.stateData.phaseType
        val queue = activeQueues[session.guildId] ?: return now

        var remainingTime = 0L

        // 1. Current phase remaining time (if any)
        if (phaseType != null && session.stateData.phaseEndTime > now) {
            remainingTime += (session.stateData.phaseEndTime - now)
        }

        val futurePhases = queue.map { it.phase }
            .distinct()
            .filter { it != phaseType }

        for (p in futurePhases) {
            remainingTime += p.defaultDurationMs
        }

        return now + remainingTime
    }
}

internal interface NightTask {
    val phase: NightPhase
    val isSkippable: Boolean get() = true
    suspend fun execute(step: NightStep, guildId: Long): Boolean
    fun shouldExecute(session: Session): Boolean = true
}

internal object NightSequence {
    val TASKS = listOf(
        NightmareStart,
        NightmareWait,
        NightmareCleanup,
        MagicianStart,
        MagicianWait,
        MagicianCleanup,
        WolfYoungerBrotherStart,
        WolfYoungerBrotherWait,
        WolfYoungerBrotherCleanup,
        WerewolfVotingStart,
        WerewolfVotingWait,
        WerewolfVotingWarning,
        WerewolfVotingFinalWait,
        WerewolfVotingCleanup,
        RoleActionsStart,
        RoleActionsWait,
        RoleActionsCleanup
    )
}
