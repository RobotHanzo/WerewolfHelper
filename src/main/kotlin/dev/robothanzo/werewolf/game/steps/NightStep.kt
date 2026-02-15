package dev.robothanzo.werewolf.game.steps

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
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import kotlin.jvm.optionals.getOrNull

@Component
class NightStep(
    private val speechService: SpeechService,
    @param:Lazy
    private val actionUIService: ActionUIService,
    private val roleRegistry: RoleRegistry,
    private val roleActionExecutor: RoleActionExecutor,
    @param:Lazy
    private val gameSessionService: GameSessionService
) : GameStep, RoleEventListener {
    private val phaseSignals =
        java.util.concurrent.ConcurrentHashMap<Long, CompletableDeferred<Unit>>()

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
    private val nightScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onStart(session: Session, service: GameStateService) {
        val guildId = session.guildId

        gameSessionService.withLockedSession(guildId) { session ->
            // Mute everyone
            speechService.setAllMute(guildId, true)

            // Reset night data
            session.stateData.submittedActions.clear()
            session.stateData.wolfStates.clear()
            session.stateData.werewolfMessages.clear()

            // Initialize night status tracking
            session.stateData.phaseType = NightPhase.NIGHTMARE_ACTION
            val now = System.currentTimeMillis()
            session.stateData.phaseStartTime = now
            session.stateData.phaseEndTime = now + 90_000 // Wolf phase lasts 90s

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

    private suspend fun processNightPhases(guildId: Long, service: GameStateService) {
        // -1. Nightmare Phase (Solo Action before Wolf Younger Brother Phase)
        val nightmarePhase = gameSessionService.withLockedSession(guildId) { lockedSession ->
            val nightmare = lockedSession.players.values.find {
                it.roles.contains("夢魘") && it.alive
            }

            if (nightmare != null) {
                val actions = lockedSession.getAvailableActionsForPlayer(nightmare.id, roleRegistry)
                val fearAction = actions.find { it.actionId == ActionDefinitionId.NIGHTMARE_FEAR }

                if (fearAction != null) {
                    lockedSession.stateData.phaseType = NightPhase.NIGHTMARE_ACTION
                    val startTime = System.currentTimeMillis()
                    lockedSession.stateData.phaseStartTime = startTime
                    lockedSession.stateData.phaseEndTime = startTime + 60_000 // 60s for fear action

                    // Prompt the player
                    actionUIService.promptPlayerForAction(
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

        if (nightmarePhase) {
            waitForPhaseWithReminders(guildId, 60) { isNightmarePhaseFinished(guildId) }
            finalizeNightmarePhase(guildId)
        }

        // 0. Wolf Younger Brother Phase (Solo Action before Wolf Phase)
        val wolfYoungerBrotherPhase = gameSessionService.withLockedSession(guildId) { lockedSession ->
            val wolfYoungerBrother = lockedSession.players.values.find {
                it.roles.contains("狼弟") && it.alive
            }

            if (wolfYoungerBrother != null) {
                val actions = lockedSession.getAvailableActionsForPlayer(wolfYoungerBrother.id, roleRegistry)
                val extraKillAction = actions.find { it.actionId == ActionDefinitionId.WOLF_YOUNGER_BROTHER_EXTRA_KILL }

                if (extraKillAction != null) {
                    lockedSession.stateData.phaseType = NightPhase.WOLF_YOUNGER_BROTHER_ACTION
                    val startTime = System.currentTimeMillis()
                    lockedSession.stateData.phaseStartTime = startTime
                    lockedSession.stateData.phaseEndTime = startTime + 60_000 // 60s for extra kill

                    // Prompt the player
                    actionUIService.promptPlayerForAction(
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

        if (wolfYoungerBrotherPhase) {
            waitForPhaseWithReminders(guildId, 60) { isWolfYoungerBrotherFinished(guildId) }
            finalizeWolfYoungerBrotherPhase(guildId)
        }

        // 1. Werewolf Voting Phase
        val session = gameSessionService.getSession(guildId).orElseThrow()

        // Nightmare Check: If any wolf is feared, the entire camp cannot act tonight
        val fearedId = session.stateData.nightmareFearTargets[session.day]
        val isAnyWolfFeared = fearedId != null && session.getPlayer(fearedId)?.wolf == true

        if (isAnyWolfFeared) {
            val msg = "⚠️ **警告**：由於一名狼人被夢魘恐懼，今晚全體狼人無法行動。"
            session.players.values.filter { it.alive && it.wolf }.forEach {
                it.channel?.sendMessage(msg)?.queue()
            }
            session.addLog(LogType.SYSTEM, "由於一名狼人被恐懼，狼人陣營今晚無法行兇")
            gameSessionService.saveSession(session)
        }

        val werewolves = if (isAnyWolfFeared) emptyList() else session.players.values.filter { p ->
            if (!p.alive || !p.wolf) return@filter false
            if (p.roles.contains("狼弟") && session.isCharacterAlive("狼兄")) return@filter false
            true
        }.map { it.id }.sorted()

        if (werewolves.isNotEmpty()) {
            gameSessionService.withLockedSession(guildId) { lockedSession ->
                lockedSession.stateData.phaseType = NightPhase.WEREWOLF_VOTING
                lockedSession.stateData.phaseStartTime = System.currentTimeMillis()
                actionUIService.promptGroupForAction(
                    guildId,
                    lockedSession,
                    PredefinedRoles.WEREWOLF_KILL,
                    werewolves,
                    90
                )
                lockedSession.addLog(LogType.SYSTEM, "狼人進行討論投票，時限90秒")

                // Ensure phase info is synced
                lockedSession.stateData.phaseEndTime = System.currentTimeMillis() + 90_000

                CmdUtils.schedule({
                    gameSessionService.withLockedSession(guildId) { currentSession ->
                        if (!allWolvesVoted(guildId)) {
                            werewolves.forEach { pid ->
                                currentSession.getPlayer(pid)?.channel?.sendMessage("⏱️ **還剩30秒！** 請投票，否則視為跳過")
                                    ?.queue()
                            }
                        }
                    }
                }, 60000)
            }

            waitForPhaseWithReminders(guildId, 90, false) { allWolvesVoted(guildId) }
            finalizeWerewolfPhase(guildId)
        }

        // 2. Role Actions Phase
        val hasActors = gameSessionService.withLockedSession(guildId) { lockedSession ->
            lockedSession.stateData.phaseType = NightPhase.ROLE_ACTIONS
            val roleStartTime = System.currentTimeMillis()
            lockedSession.stateData.phaseStartTime = roleStartTime
            lockedSession.stateData.phaseEndTime = roleStartTime + 60_000 // Role phase lasts 60s
            gameSessionService.broadcastSessionUpdate(lockedSession)

            val actors = mutableListOf<Int>()

            // Refresh fearedId from the locked session to ensure latest state
            val currentFearedId = lockedSession.stateData.nightmareFearTargets[lockedSession.day]
            log.info("NightStep RoleActions: Day={}, FearedId={}", lockedSession.day, currentFearedId)

            for (player in lockedSession.players.values) {
                if (!player.alive) continue
                val pid = player.id

                // Check if player is nightmared - Early exit before actions check
                if (currentFearedId != null && currentFearedId == pid) {
                    log.info("NightStep: Player {} is nightmared. Sending notification and skipping.", pid)
                    if (player.channel != null) {
                        player.channel?.sendMessage(
                            "💤 **你被夢魘侵襲！**\n恐怖的夢境纏繞著你，今晚你無法執行任何行動..."
                        )?.queue()
                    } else {
                        log.warn("NightStep: Player {} channel is null! Cannot send nightmare notification.", pid)
                    }
                    continue
                }

                var actions = lockedSession.getAvailableActionsForPlayer(pid, roleRegistry)

                // Filter out standard werewolf kill as it's handled in the group phase
                if (player.wolf) {
                    actions = actions.filter { it.actionId != ActionDefinitionId.WEREWOLF_KILL }
                }

                // Filter out Nightmare action as it's handled in its own phase
                if (player.roles.contains("夢魘")) {
                    actions = actions.filter { it.actionId != ActionDefinitionId.NIGHTMARE_FEAR }
                }

                if (actions.isNotEmpty()) {
                    actors.add(pid)
                    actionUIService.promptPlayerForAction(guildId, lockedSession, pid, actions, 60)
                }
            }
            actors.isNotEmpty()
        }

        if (hasActors) {
            waitForPhaseWithReminders(guildId, 60) { allActorsSubmitted(guildId) }
        }

        finalizeRoleActionsPhase(guildId)
        gameSessionService.getSession(guildId).getOrNull()?.addLog(LogType.SYSTEM, "夜晚結束，天亮了")
        gameSessionService.withLockedSession(guildId) { lockedSession ->
            service.nextStep(lockedSession)
        }
    }

    private fun isNightmarePhaseFinished(guildId: Long): Boolean {
        val session = gameSessionService.getSession(guildId).orElse(null) ?: return true
        if (session.stateData.phaseType != NightPhase.NIGHTMARE_ACTION) return true

        val nmAction = session.stateData.submittedActions.find {
            it.actorRole == "夢魘"
        }
        return nmAction != null && (
            nmAction.status == ActionStatus.SUBMITTED ||
                nmAction.status == ActionStatus.SKIPPED ||
                nmAction.status == ActionStatus.PROCESSED
            )
    }

    private fun finalizeNightmarePhase(guildId: Long) {
        gameSessionService.withLockedSession(guildId) { session ->
            actionUIService.cleanupExpiredPrompts(session)
            session.addLog(LogType.SYSTEM, "夢魘行動階段結束")
        }
    }

    private fun isWolfYoungerBrotherFinished(guildId: Long): Boolean {
        val session = gameSessionService.getSession(guildId).orElse(null) ?: return true
        if (session.stateData.phaseType != NightPhase.WOLF_YOUNGER_BROTHER_ACTION) return true

        val ybAction = session.stateData.submittedActions.find {
            it.actorRole == "狼弟"
        }
        return ybAction != null && (
            ybAction.status == ActionStatus.SUBMITTED ||
                ybAction.status == ActionStatus.SKIPPED ||
                ybAction.status == ActionStatus.PROCESSED
            )
    }

    private fun finalizeWolfYoungerBrotherPhase(guildId: Long) {
        gameSessionService.withLockedSession(guildId) { session ->
            actionUIService.cleanupExpiredPrompts(session)
            session.addLog(LogType.SYSTEM, "狼弟行動階段結束")
        }
    }

    private suspend fun waitForPhaseWithReminders(
        guildId: Long,
        timeoutSeconds: Int,
        sendGenericReminders: Boolean = true,
        isFinished: () -> Boolean
    ) {
        val signal = CompletableDeferred<Unit>()
        phaseSignals[guildId] = signal

        val reminderTask = if (sendGenericReminders) {
            CmdUtils.schedule({
                gameSessionService.withLockedSession(guildId) { session ->
                    actionUIService.sendReminders(guildId, session)
                }
            }, 30000)
        } else null

        val timeoutTask = CmdUtils.schedule({
            signal.complete(Unit)
        }, timeoutSeconds * 1000L)

        // Wait until finished or timeout
        while (!isFinished() && !signal.isCompleted) {
            try {
                withTimeoutOrNull(5000) { signal.await() }
            } catch (_: Exception) {
                // Ignore and re-check isFinished
            }
        }

        reminderTask?.cancel()
        timeoutTask.cancel()
        phaseSignals.remove(guildId)
    }

    private fun allWolvesVoted(guildId: Long): Boolean {
        val session = gameSessionService.getSession(guildId).orElse(null) ?: return true
        val groupState = actionUIService.getGroupState(session, PredefinedRoles.WEREWOLF_KILL) ?: return true

        // Electorates approach: Ensure EVERY electorate has cast a valid vote
        // This considers Nightmare as a voting wolf if they are in the electorate list
        return groupState.electorates.all { electorateId ->
            groupState.votes.any { it.voterId == electorateId && it.targetId != null }
        }
    }

    private fun allActorsSubmitted(guildId: Long): Boolean {
        return gameSessionService.withLockedSession(guildId) { session ->
            // Check all actions currently in submittedActions to ensure they are finalized
            session.stateData.submittedActions.all {
                it.status == ActionStatus.SUBMITTED ||
                    it.status == ActionStatus.SKIPPED ||
                    it.status == ActionStatus.PROCESSED
            }
        }
    }

    private fun finalizeWerewolfPhase(guildId: Long) {
        gameSessionService.withLockedSession(guildId) { session ->
            val groupState = session.stateData.wolfStates[PredefinedRoles.WEREWOLF_KILL]
                ?: return@withLockedSession

            // Timeout handling: default missing votes to skip
            val electorates = groupState.electorates
            electorates.forEach { pid ->
                if (groupState.votes.none { it.voterId == pid }) {
                    // Add skip vote directly to mutable list
                    groupState.votes.add(WolfVote(voterId = pid, targetId = SKIP_TARGET_ID))
                }
            }
            // Sync is automatic as we modified the mutable object inside session


            val chosenTarget = actionUIService.resolveGroupVote(session, groupState)
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
                // Warning: submitAction has its own lock, but it's reentrant.
                // Find a valid alive wolf to be the actor
                val actorId = groupState.electorates.firstOrNull { pid ->
                    session.alivePlayers().containsKey(pid.toString())
                } ?: groupState.electorates.firstOrNull() ?: 0

                session.validateAndSubmitAction(
                    ActionDefinitionId.WEREWOLF_KILL,
                    actorId,
                    arrayListOf(chosenTarget),
                    "SYSTEM",
                    roleRegistry,
                    roleActionExecutor
                )
            }
        }
    }

    private fun finalizeRoleActionsPhase(guildId: Long) {
        gameSessionService.withLockedSession(guildId) { session ->
            actionUIService.cleanupExpiredPrompts(session)
            session.addLog(LogType.SYSTEM, "角色行動階段結束，清理未完成的行動")
        }
    }

    override fun onEnd(session: Session, service: GameStateService) {
        // Clean up expired prompts and send timeout notifications
        actionUIService.cleanupExpiredPrompts(session)

        // Clear prompts
        // We do NOT clear submittedActions, wolfStates, or messages here to preserve them for review/dashboard
        // They will be cleared at the start of the next NightStep

        session.stateData.phaseType = null
        session.stateData.phaseStartTime = 0
        session.stateData.phaseEndTime = 0
        session.stateData.wolfBrotherAwakenedPlayerId = null
        gameSessionService.saveSession(session)
    }

    override fun handleInput(session: Session, input: Map<String, Any>): Map<String, Any> {
        val action = input["action"] as? String

        return when (action) {
            else -> mapOf("success" to false)
        }
    }

    override fun getDurationSeconds(session: Session): Int {
        return 150
    }
}
