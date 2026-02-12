package dev.robothanzo.werewolf.game.steps

import dev.robothanzo.werewolf.database.documents.LogType
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.GameStep
import dev.robothanzo.werewolf.game.model.*
import dev.robothanzo.werewolf.game.roles.PredefinedRoles
import dev.robothanzo.werewolf.service.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import kotlin.jvm.optionals.getOrNull

@Component
class NightStep(
    private val speechService: SpeechService,
    private val actionUIService: ActionUIService,
    private val nightManager: NightManager,
    private val roleRegistry: dev.robothanzo.werewolf.game.roles.RoleRegistry,
    private val roleActionExecutor: dev.robothanzo.werewolf.game.roles.actions.RoleActionExecutor,
    @param:Lazy
    private val gameSessionService: GameSessionService
) : GameStep {
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
            session.stateData.phaseType = NightPhase.WEREWOLF_VOTING
            val now = System.currentTimeMillis()
            session.stateData.phaseStartTime = now
            session.stateData.phaseEndTime = now + 90_000 // Wolf phase lasts 90s

            for (p in session.alivePlayers().values) {
                if (p.wolf) {
                    // Wolf Younger Brother (狼弟) joins the pack ONLY IF the Brother has died.
                    val wolfBrotherDiedDay = session.stateData.wolfBrotherDiedDay
                    if (p.roles.contains("狼弟") && wolfBrotherDiedDay == null) {
                        continue
                    }
                } else {
                    val actions = session.getAvailableActionsForPlayer(p.id, roleRegistry)
                    p.actionSubmitted = false
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

                    // Set status to ACTING
                    updateStatusToActing(lockedSession, wolfYoungerBrother.id)
                    return@withLockedSession true
                }
            }
            false
        }

        val session = gameSessionService.getSession(guildId).orElseThrow()
        if (wolfYoungerBrotherPhase) waitForWolfYoungerBrotherPhase(session, 60)

        // 1. Werewolf Voting Phase
        val werewolves = session.players.values.filter { p ->
            if (!p.alive || !p.wolf) return@filter false

            if (p.roles.contains("狼弟") && session.isCharacterAlive("狼兄")) {
                return@filter false
            }

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

                nightScope.launch {
                    delay(60_000)
                    gameSessionService.withLockedSession(guildId) { currentSession ->
                        if (!allWolvesVoted(guildId)) {
                            werewolves.forEach { pid ->
                                currentSession.getPlayer(pid)?.channel?.sendMessage("⏱️ **還剩30秒！** 請投票，否則視為跳過")
                                    ?.queue()
                            }
                        }
                    }
                }
            }

            waitForWerewolfPhase(guildId, 90)
        }

        // 2. Role Actions Phase
        gameSessionService.withLockedSession(guildId) { lockedSession ->
            lockedSession.stateData.phaseType = NightPhase.ROLE_ACTIONS
            val roleStartTime = System.currentTimeMillis()
            lockedSession.stateData.phaseStartTime = roleStartTime
            lockedSession.stateData.phaseEndTime = roleStartTime + 60_000 // Role phase lasts 60s
            gameSessionService.broadcastSessionUpdate(lockedSession)

            val actors = mutableListOf<Int>()
            for (player in lockedSession.players.values) {
                if (!player.alive) continue
                val pid = player.id
                var actions = lockedSession.getAvailableActionsForPlayer(pid, roleRegistry)

                // Filter out standard werewolf kill as it's handled in the group phase
                if (player.wolf) {
                    actions = actions.filter { it.actionId != ActionDefinitionId.WEREWOLF_KILL }
                }

                if (actions.isNotEmpty()) {
                    actors.add(pid)
                    actionUIService.promptPlayerForAction(guildId, lockedSession, pid, actions, 60)

                    // Set status to ACTING
                    updateStatusToActing(lockedSession, pid)
                }
            }

            if (actors.isNotEmpty()) {
                nightScope.launch {
                    delay(30_000)
                    gameSessionService.withLockedSession(guildId) { currentSession ->
                        actionUIService.sendReminders(guildId, currentSession)
                    }
                }
            }
        }

        waitForRoleActionsPhase(guildId, 60)
        finalizeRoleActionsPhase(guildId)
        gameSessionService.getSession(guildId).getOrNull()?.addLog(LogType.SYSTEM, "夜晚結束，天亮了")
        // Transition to next step automatically
        gameSessionService.withLockedSession(guildId) { lockedSession ->
            service.nextStep(lockedSession)
        }
    }

    private suspend fun waitForWolfYoungerBrotherPhase(session: Session, timeoutSeconds: Int) {
        // Double check phase to avoid waiting if logic didn't trigger
        if (session.stateData.phaseType != NightPhase.WOLF_YOUNGER_BROTHER_ACTION) return

        val timeoutMs = timeoutSeconds * 1000L

        withTimeoutOrNull(timeoutMs) {
            if (isWolfYoungerBrotherFinished(session)) return@withTimeoutOrNull

            nightManager.getUpdateFlow()
                .filter { it == session.guildId }
                .firstOrNull {
                    isWolfYoungerBrotherFinished(session)
                }
        }

        finalizeWolfYoungerBrotherPhase(session)
    }

    private fun isWolfYoungerBrotherFinished(session: Session): Boolean {

        val ybAction = session.stateData.submittedActions.find {
            it.actionDefinitionId == ActionDefinitionId.WOLF_YOUNGER_BROTHER_EXTRA_KILL
        }
        // If action doesn't exist, we are finished (or never started)
        // If action exists, check status
        return ybAction == null ||
            ybAction.status == ActionStatus.SUBMITTED ||
            ybAction.status == ActionStatus.SKIPPED ||
            ybAction.status == ActionStatus.PROCESSED
    }

    private fun finalizeWolfYoungerBrotherPhase(session: Session) {
        actionUIService.cleanupExpiredPrompts(session)
        session.addLog(LogType.SYSTEM, "狼弟行動階段結束")
    }

    private suspend fun waitForWerewolfPhase(guildId: Long, timeoutSeconds: Int) {
        val timeoutMs = timeoutSeconds * 1000L

        withTimeoutOrNull(timeoutMs) {
            // Check initial state
            if (allWolvesVoted(guildId)) return@withTimeoutOrNull

            // Wait for updates and exit early if all voted
            nightManager.getUpdateFlow()
                .filter { it == guildId }
                .firstOrNull {
                    // Update dashboard status for group inside lock
                    gameSessionService.withLockedSession(guildId) { lockedSession ->
                        gameSessionService.broadcastSessionUpdate(lockedSession)
                    }
                    allWolvesVoted(guildId)
                }
        }

        // Final Result Sync and Resolution
        finalizeWerewolfPhase(guildId)
    }

    private suspend fun waitForRoleActionsPhase(guildId: Long, timeoutSeconds: Int) {
        val timeoutMs = timeoutSeconds * 1000L

        withTimeoutOrNull(timeoutMs) {
            if (allActorsSubmitted(guildId)) return@withTimeoutOrNull

            nightManager.getUpdateFlow()
                .filter { it == guildId }
                .firstOrNull {
                    allActorsSubmitted(guildId)
                }
        }
    }

    private fun allWolvesVoted(guildId: Long): Boolean {
        val session = gameSessionService.getSession(guildId).orElse(null) ?: return true
        val groupState = actionUIService.getGroupState(session, PredefinedRoles.WEREWOLF_KILL) ?: return true
        return groupState.votes.filter { it.targetId != null }.size >= groupState.electorates.size
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

    private fun updateStatusToActing(session: Session, pid: Int) {
        val actionInstance = session.stateData.submittedActions.find { it.actor == pid } ?: return
        if (actionInstance.status == ActionStatus.PENDING) {
            actionInstance.status = ActionStatus.ACTING
            gameSessionService.saveSession(session)
            gameSessionService.broadcastSessionUpdate(session)
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
