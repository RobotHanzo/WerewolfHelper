package dev.robothanzo.werewolf.game.steps

import dev.robothanzo.werewolf.audio.Audio
import dev.robothanzo.werewolf.audio.Audio.play
import dev.robothanzo.werewolf.database.documents.LogType
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.GameStep
import dev.robothanzo.werewolf.game.listeners.RoleEventListener
import dev.robothanzo.werewolf.game.model.*
import dev.robothanzo.werewolf.game.roles.PredefinedRoles
import dev.robothanzo.werewolf.game.roles.RoleRegistry
import dev.robothanzo.werewolf.game.roles.actions.RoleActionExecutor
import dev.robothanzo.werewolf.service.ActionUIService
import dev.robothanzo.werewolf.service.GameSessionService
import dev.robothanzo.werewolf.service.GameStateService
import dev.robothanzo.werewolf.service.SpeechService
import dev.robothanzo.werewolf.utils.CmdUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import java.util.ArrayDeque
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
) : GameStep, RoleEventListener {
    internal val phaseSignals =
        java.util.concurrent.ConcurrentHashMap<Long, Channel<Unit>>()

    override fun getInterestedEvents() = listOf(RoleEventType.ACTION_PROCESSED)

    override fun onEvent(
        session: Session,
        eventType: RoleEventType,
        metadata: Map<String, Any>
    ) {
        if (eventType == RoleEventType.ACTION_PROCESSED) {
            phaseSignals[session.guildId]?.trySend(Unit)
        }
    }

    override val id = "NIGHT_PHASE"
    override val name = "天黑請閉眼"

    private val log = LoggerFactory.getLogger(NightStep::class.java)
    internal val nightScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onStart(session: Session, service: GameStateService) {
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

            // Initialize night status. Start directly with Nightmare action phase implied.
            session.stateData.phaseType = NightPhase.NIGHTMARE_ACTION
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
        nightScope.launch {
            try {
                processNightPhases(guildId, service)
            } catch (e: Exception) {
                log.error("Error during night orchestration for guild $guildId", e)
            }
        }
    }

    // --- Queue Processing ---

    // --- Queue Processing ---

    internal suspend fun processNightPhases(guildId: Long, service: GameStateService, tasks: List<NightTask> = NightSequence.TASKS) {
        val taskQueue = ArrayDeque(tasks)
        log.info("Starting Night Phase Queue processing for guild $guildId with ${taskQueue.size} tasks")

        while (!taskQueue.isEmpty()) {
            val currentTask = taskQueue.poll()
            log.debug("Processing Night Task: {} for phase {}", currentTask.javaClass.simpleName, currentTask.phase)

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

        gameSessionService.getSession(guildId).getOrNull()?.addLog(LogType.SYSTEM, "夜晚結束，天亮了")
        gameSessionService.withLockedSession(guildId) { lockedSession ->
            service.nextStep(lockedSession)
        }
    }

    // --- Wait Helper ---
    internal suspend fun waitForCondition(guildId: Long, durationSeconds: Int, condition: () -> Boolean): Boolean {
        val signal = CompletableDeferred<Unit>()
        phaseSignals[guildId] = signal

        val timeoutTask = CmdUtils.schedule({
            signal.complete(Unit)
        }, durationSeconds * 1000L)

        // Check condition loop
        val checkJob = nightScope.launch {
            while (isActive) {
                if (condition()) {
                    signal.complete(Unit)
                    break
                }
                delay(1000)
            }
        }

        try {
           withTimeout((durationSeconds + 5) * 1000L) { // Safety timeout
               signal.await()
           }
        } catch (_: Exception) {
        } finally {
            timeoutTask.cancel()
            checkJob.cancel()
            phaseSignals.remove(guildId)
        }

        return condition()
    }

    override fun onEnd(session: Session, service: GameStateService) {
        actionUIService.cleanupExpiredPrompts(session)

        session.stateData.phaseType = null
        session.stateData.phaseStartTime = 0
        session.stateData.phaseEndTime = 0
        session.stateData.wolfBrotherAwakenedPlayerId = null
        gameSessionService.saveSession(session)
    }

    override fun handleInput(session: Session, input: Map<String, Any>): Map<String, Any> {
        return mapOf("success" to false)
    }

    override fun getDurationSeconds(session: Session): Int {
        return 150
    }
}

// --- Night Task Definitions ---

// --- Night Task Definitions ---

internal interface NightTask {
    val phase: NightPhase
    val isSkippable: Boolean get() = true
    suspend fun execute(step: NightStep, guildId: Long): Boolean
}

internal object NightSequence {
    // 0. Nightmare
    object NightmareStart : NightTask {
        override val phase = NightPhase.NIGHTMARE_ACTION
        override suspend fun execute(step: NightStep, guildId: Long): Boolean {
            return step.gameSessionService.withLockedSession(guildId) { lockedSession ->
                val nightmare = lockedSession.players.values.find {
                    it.roles.contains("夢魘") && it.alive
                }

                if (nightmare != null) {
                    val actions = lockedSession.getAvailableActionsForPlayer(nightmare.id, step.roleRegistry)
                    val fearAction = actions.find { it.actionId == ActionDefinitionId.NIGHTMARE_FEAR }

                    if (fearAction != null) {
                        lockedSession.stateData.phaseType = NightPhase.NIGHTMARE_ACTION
                        val startTime = System.currentTimeMillis()
                        lockedSession.stateData.phaseStartTime = startTime
                        lockedSession.stateData.phaseEndTime = startTime + 60_000 // 60s

                        step.actionUIService.promptPlayerForAction(
                            guildId,
                            lockedSession,
                            nightmare.id,
                            listOf(fearAction),
                            60
                        )
                        return@withLockedSession true
                    }
                }
                false
            }
        }
    }

    object NightmareWait : NightTask {
        override val phase = NightPhase.NIGHTMARE_ACTION
        override suspend fun execute(step: NightStep, guildId: Long): Boolean {
             val finishedEarly = step.waitForCondition(guildId, 60) {
                 val session = step.gameSessionService.getSession(guildId).orElse(null) ?: return@waitForCondition true
                 if (session.stateData.phaseType != NightPhase.NIGHTMARE_ACTION) return@waitForCondition true
                 val nmAction = session.stateData.submittedActions.find { it.actorRole == "夢魘" }
                 nmAction != null && (
                     nmAction.status == ActionStatus.SUBMITTED ||
                         nmAction.status == ActionStatus.SKIPPED ||
                         nmAction.status == ActionStatus.PROCESSED
                 )
             }
            return !finishedEarly
        }
    }

    object NightmareCleanup : NightTask {
        override val phase = NightPhase.NIGHTMARE_ACTION
        override val isSkippable = false
        override suspend fun execute(step: NightStep, guildId: Long): Boolean {
            step.gameSessionService.withLockedSession(guildId) { session ->
                step.actionUIService.cleanupExpiredPrompts(session)
                session.addLog(LogType.SYSTEM, "夢魘行動階段結束")
            }
            return false
        }
    }

    // 1. Wolf Younger Brother
    object WolfBrotherStart : NightTask {
        override val phase = NightPhase.WOLF_YOUNGER_BROTHER_ACTION
        override suspend fun execute(step: NightStep, guildId: Long): Boolean {
            return step.gameSessionService.withLockedSession(guildId) { lockedSession ->
                val wolfYoungerBrother = lockedSession.players.values.find {
                    it.roles.contains("狼弟") && it.alive
                }

                if (wolfYoungerBrother != null) {
                    val actions = lockedSession.getAvailableActionsForPlayer(wolfYoungerBrother.id, step.roleRegistry)
                    val extraKillAction = actions.find { it.actionId == ActionDefinitionId.WOLF_YOUNGER_BROTHER_EXTRA_KILL }

                    if (extraKillAction != null) {
                        lockedSession.stateData.phaseType = NightPhase.WOLF_YOUNGER_BROTHER_ACTION
                        val startTime = System.currentTimeMillis()
                        lockedSession.stateData.phaseStartTime = startTime
                        lockedSession.stateData.phaseEndTime = startTime + 60_000 // 60s

                        step.actionUIService.promptPlayerForAction(
                            guildId,
                            lockedSession,
                            wolfYoungerBrother.id,
                            listOf(extraKillAction),
                            60
                        )
                        return@withLockedSession true
                    }
                }
                false
            }
        }
    }

    object WolfBrotherWait : NightTask {
        override val phase = NightPhase.WOLF_YOUNGER_BROTHER_ACTION
        override suspend fun execute(step: NightStep, guildId: Long): Boolean {
             val finishedEarly = step.waitForCondition(guildId, 60) {
                 val session = step.gameSessionService.getSession(guildId).orElse(null) ?: return@waitForCondition true
                 if (session.stateData.phaseType != NightPhase.WOLF_YOUNGER_BROTHER_ACTION) return@waitForCondition true
                 val ybAction = session.stateData.submittedActions.find { it.actorRole == "狼弟" }
                 ybAction != null && (
                     ybAction.status == ActionStatus.SUBMITTED ||
                         ybAction.status == ActionStatus.SKIPPED ||
                         ybAction.status == ActionStatus.PROCESSED
                 )
             }
            return !finishedEarly
        }
    }

    object WolfBrotherCleanup : NightTask {
        override val phase = NightPhase.WOLF_YOUNGER_BROTHER_ACTION
        override val isSkippable = false
        override suspend fun execute(step: NightStep, guildId: Long): Boolean {
            step.gameSessionService.withLockedSession(guildId) { session ->
                step.actionUIService.cleanupExpiredPrompts(session)
                session.addLog(LogType.SYSTEM, "狼弟行動階段結束")
            }
            return false
        }
    }

    // 2. Werewolf Voting
    object WerewolfVotingStart : NightTask {
        override val phase = NightPhase.WEREWOLF_VOTING
        override suspend fun execute(step: NightStep, guildId: Long): Boolean {
            val session = step.gameSessionService.getSession(guildId).orElseThrow()

            // Nightmare Check
            val fearedId = session.stateData.nightmareFearTargets[session.day]
            val isAnyWolfFeared = fearedId != null && session.getPlayer(fearedId)?.wolf == true

            if (isAnyWolfFeared) {
                val msg = "⚠️ **警告**：由於一名狼人被夢魘恐懼，今晚全體狼人無法行動。"
                session.players.values.filter { it.alive && it.wolf }.forEach {
                    it.channel?.sendMessage(msg)?.queue()
                }
                session.addLog(LogType.SYSTEM, "由於一名狼人被恐懼，狼人陣營今晚無法行兇")
                step.gameSessionService.saveSession(session)
                return false
            }

            val werewolves = session.players.values.filter { p ->
                if (!p.alive || !p.wolf) return@filter false
                if (p.roles.contains("狼弟") && session.isCharacterAlive("狼兄")) return@filter false
                true
            }.map { it.id }.sorted()

            if (werewolves.isNotEmpty()) {
                step.gameSessionService.withLockedSession(guildId) { lockedSession ->
                    lockedSession.stateData.phaseType = NightPhase.WEREWOLF_VOTING
                    lockedSession.stateData.phaseStartTime = System.currentTimeMillis()
                    // Total time 90s usually (60s wait + 30s force) but UI prompt set to 90
                    step.actionUIService.promptGroupForAction(
                        guildId,
                        lockedSession,
                        PredefinedRoles.WEREWOLF_KILL,
                        werewolves,
                        90
                    )
                    lockedSession.addLog(LogType.SYSTEM, "狼人進行討論投票，時限90秒")
                    lockedSession.stateData.phaseEndTime = System.currentTimeMillis() + 90_000
                }
                return true
            }
            return false
        }
    }

    object WerewolfVotingWait : NightTask {
        override val phase = NightPhase.WEREWOLF_VOTING
        override suspend fun execute(step: NightStep, guildId: Long): Boolean {
            val finishedEarly = step.waitForCondition(guildId, 60) {
                 val session = step.gameSessionService.getSession(guildId).orElse(null) ?: return@waitForCondition true
                 val groupState = step.actionUIService.getGroupState(session, PredefinedRoles.WEREWOLF_KILL) ?: return@waitForCondition true
                 groupState.electorates.all { electorateId ->
                    groupState.votes.any { it.voterId == electorateId && it.targetId != null }
                 }
            }
            return !finishedEarly
        }
    }

    object WerewolfVotingWarning : NightTask {
        override val phase = NightPhase.WEREWOLF_VOTING
        override suspend fun execute(step: NightStep, guildId: Long): Boolean {
             step.gameSessionService.withLockedSession(guildId) { currentSession ->
                 val groupState = step.actionUIService.getGroupState(currentSession, PredefinedRoles.WEREWOLF_KILL)

                 val isFinished = groupState?.electorates?.all { electorateId ->
                    groupState.votes.any { it.voterId == electorateId && it.targetId != null }
                 } ?: true

                 if (!isFinished) {
                     groupState?.electorates?.forEach { pid ->
                         if (groupState.votes.none { it.voterId == pid }) {
                             currentSession.getPlayer(pid)?.channel?.sendMessage("⏱️ **還剩30秒！** 請投票，否則視為跳過")?.queue()
                         }
                     }
                 }
            }
            return true
        }
    }

    object WerewolfVotingFinalWait : NightTask {
        override val phase = NightPhase.WEREWOLF_VOTING
        override suspend fun execute(step: NightStep, guildId: Long): Boolean {
            val finishedEarly = step.waitForCondition(guildId, 30) {
                 val session = step.gameSessionService.getSession(guildId).orElse(null) ?: return@waitForCondition true
                 val groupState = step.actionUIService.getGroupState(session, PredefinedRoles.WEREWOLF_KILL) ?: return@waitForCondition true
                 groupState.electorates.all { electorateId ->
                    groupState.votes.any { it.voterId == electorateId && it.targetId != null }
                 }
            }
            return !finishedEarly
        }
    }

    object WerewolfVotingCleanup : NightTask {
        override val phase = NightPhase.WEREWOLF_VOTING
        override val isSkippable = false
        override suspend fun execute(step: NightStep, guildId: Long): Boolean {
            step.gameSessionService.withLockedSession(guildId) { session ->
                val groupState = session.stateData.wolfStates[PredefinedRoles.WEREWOLF_KILL] ?: return@withLockedSession
                val electorates = groupState.electorates
                electorates.forEach { pid ->
                    if (groupState.votes.none { it.voterId == pid }) {
                        groupState.votes.add(WolfVote(voterId = pid, targetId = SKIP_TARGET_ID))
                    }
                }

                val chosenTarget = step.actionUIService.resolveGroupVote(session, groupState)
                val resultText = if (chosenTarget == null || chosenTarget == SKIP_TARGET_ID) {
                    "✓ **投票結果**：全體選擇 **跳過**，本夜無人被擊殺"
                } else {
                    val targetPlayer = session.getPlayer(chosenTarget)
                    "✓ **投票結果**：擊殺 **${targetPlayer?.nickname ?: "未知"}**"
                }

                val msg = buildString {
                    appendLine("🐺 **狼人行動階段結束**")
                    appendLine(resultText)
                }

                session.players.values.filter { electorates.contains(it.id) }
                    .forEach { it.channel?.sendMessage(msg)?.queue() }
                session.judgeTextChannel?.sendMessage(resultText)?.queue()
                session.addLog(LogType.SYSTEM, resultText.replace("**", "").replace("✓ ", "").replace("：", " → "))

                if (chosenTarget != null) {
                    val actorId = groupState.electorates.firstOrNull { pid ->
                        session.alivePlayers().containsKey(pid.toString())
                    } ?: groupState.electorates.firstOrNull() ?: 0

                    session.validateAndSubmitAction(
                        ActionDefinitionId.WEREWOLF_KILL,
                        actorId,
                        arrayListOf(chosenTarget),
                        "SYSTEM",
                        step.roleRegistry,
                        step.roleActionExecutor
                    )
                }
                step.actionUIService.cleanupExpiredPrompts(session)
            }
            return false
        }
    }

    // 3. Role Actions
    object RoleActionsStart : NightTask {
        override val phase = NightPhase.ROLE_ACTIONS
        override suspend fun execute(step: NightStep, guildId: Long): Boolean {
             return step.gameSessionService.withLockedSession(guildId) { lockedSession ->
                lockedSession.stateData.phaseType = NightPhase.ROLE_ACTIONS
                val roleStartTime = System.currentTimeMillis()
                lockedSession.stateData.phaseStartTime = roleStartTime
                lockedSession.stateData.phaseEndTime = roleStartTime + 60_000 // 60s
                step.gameSessionService.broadcastSessionUpdate(lockedSession)

                val actors = mutableListOf<Int>()
                val currentFearedId = lockedSession.stateData.nightmareFearTargets[lockedSession.day]

                 for (player in lockedSession.players.values) {
                    if (!player.alive) continue
                    val pid = player.id

                    if (currentFearedId != null && currentFearedId == pid) {
                        if (player.channel != null) {
                            player.channel?.sendMessage(
                                "💤 **你被夢魘侵襲！**\n恐怖的夢境纏繞著你，今晚你無法執行任何行動..."
                            )?.queue()
                        }
                        continue
                    }

                    var actions = lockedSession.getAvailableActionsForPlayer(pid, step.roleRegistry)
                    if (player.wolf) actions = actions.filter { it.actionId != ActionDefinitionId.WEREWOLF_KILL }
                    if (player.roles.contains("夢魘")) actions = actions.filter { it.actionId != ActionDefinitionId.NIGHTMARE_FEAR }

                    if (actions.isNotEmpty()) {
                        actors.add(pid)
                        step.actionUIService.promptPlayerForAction(guildId, lockedSession, pid, actions, 60)
                    }
                }
                actors.isNotEmpty()
            }
        }
    }

    object RoleActionsWait : NightTask {
        override val phase = NightPhase.ROLE_ACTIONS
        override suspend fun execute(step: NightStep, guildId: Long): Boolean {
            val finishedEarly = step.waitForCondition(guildId, 60) {
                step.gameSessionService.withLockedSession(guildId) { session ->
                    session.stateData.submittedActions.all {
                        it.status == ActionStatus.SUBMITTED ||
                        it.status == ActionStatus.SKIPPED ||
                        it.status == ActionStatus.PROCESSED
                    }
                }
            }
            return !finishedEarly
        }
    }

    object RoleActionsCleanup : NightTask {
        override val phase = NightPhase.ROLE_ACTIONS
        override val isSkippable = false
        override suspend fun execute(step: NightStep, guildId: Long): Boolean {
            step.gameSessionService.withLockedSession(guildId) { session ->
                step.actionUIService.cleanupExpiredPrompts(session)
                session.addLog(LogType.SYSTEM, "角色行動階段結束")
            }
            return false
        }
    }

    val TASKS = listOf(
        NightmareStart,
        NightmareWait,
        NightmareCleanup,
        WolfBrotherStart,
        WolfBrotherWait,
        WolfBrotherCleanup,
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
