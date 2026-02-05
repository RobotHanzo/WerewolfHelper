package dev.robothanzo.werewolf.game.steps

import dev.robothanzo.werewolf.database.documents.LogType
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.GameStep
import dev.robothanzo.werewolf.game.model.ActionStatus
import dev.robothanzo.werewolf.game.model.SKIP_TARGET_ID
import dev.robothanzo.werewolf.game.model.WolfVote
import dev.robothanzo.werewolf.game.roles.PredefinedRoles
import dev.robothanzo.werewolf.service.*
import dev.robothanzo.werewolf.utils.parseLong
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import kotlin.jvm.optionals.getOrNull

@Component
class NightStep(
    private val gameActionService: GameActionService,
    private val roleActionService: RoleActionService,
    private val actionUIService: ActionUIService,
    private val nightManager: NightManager,
    @param:Lazy
    private val gameSessionService: GameSessionService
) : GameStep {
    override val id = "NIGHT_PHASE"
    override val name = "天黑請閉眼"

    private val log = LoggerFactory.getLogger(NightStep::class.java)
    private val nightScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onStart(session: Session, service: GameStateService) {
        val guildId = session.guildId

        gameSessionService.withLockedSession(guildId) { lockedSession ->
            // Mute everyone
            gameActionService.muteAll(guildId, true)

            // Clear pending actions from previous night
            lockedSession.stateData.pendingActions.clear()

            // Reset actionSubmitted flag for all players
            for ((_, player) in lockedSession.players) {
                player.actionSubmitted = false
            }

            // Log night start
            lockedSession.addLog(LogType.SYSTEM, "夜晚開始，各職業請準備行動")

            // Initialize night status tracking
            lockedSession.stateData.phaseType = "WEREWOLF_VOTING"
            val now = System.currentTimeMillis()
            lockedSession.stateData.phaseStartTime = now
            lockedSession.stateData.phaseEndTime = now + 90_000 // Wolf phase lasts 90s
            lockedSession.stateData.werewolfMessages.clear()

            val werewolfVotes = mutableMapOf<String, WolfVote>()
            val actionStatuses = mutableMapOf<String, ActionStatus>()

            for ((_, p) in lockedSession.players) {
                if (!p.alive || p.user == null) continue
                val uid = p.user!!.idLong

                if (p.wolf) {
                    werewolfVotes[uid.toString()] = WolfVote(
                        voterId = uid.toString()
                    )
                } else {
                    val actions = roleActionService.getAvailableActionsForPlayer(lockedSession, uid)
                    if (actions.isNotEmpty()) {
                        actionStatuses[uid.toString()] = ActionStatus(
                            playerId = uid.toString(),
                            role = (p.roles?.firstOrNull() ?: "未知"),
                            status = "PENDING"
                        )
                    }
                }
            }

            lockedSession.stateData.werewolfVotes.clear()
            lockedSession.stateData.werewolfVotes.putAll(werewolfVotes)
            lockedSession.stateData.actionStatuses.clear()
            lockedSession.stateData.actionStatuses.putAll(actionStatuses)
        }

        // Orchestrate the night phases
        nightScope.launch {
            try {
                processNightPhases(guildId)
            } catch (e: Exception) {
                log.error("Error during night orchestration for guild $guildId", e)
            }
        }
    }

    private suspend fun processNightPhases(guildId: Long) {
        // 1. Werewolf Voting Phase
        val session = gameSessionService.getSession(guildId).orElseThrow()
        val werewolves = session.players.values.filter { it.alive && it.wolf }.mapNotNull { it.user?.idLong }

        if (werewolves.isNotEmpty()) {
            gameSessionService.withLockedSession(guildId) { lockedSession ->
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
                nightManager.broadcastNightStatus(lockedSession)

                nightScope.launch {
                    delay(60_000)
                    gameSessionService.withLockedSession(guildId) { currentSession ->
                        if (!allWolvesVoted(guildId)) {
                            werewolves.forEach { uid ->
                                currentSession.getPlayer(uid)?.channel?.sendMessage("⏱️ **還剩30秒！** 請投票，否則視為跳過")
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
            lockedSession.stateData.phaseType = "ROLE_ACTIONS"
            val roleStartTime = System.currentTimeMillis()
            lockedSession.stateData.phaseStartTime = roleStartTime
            lockedSession.stateData.phaseEndTime = roleStartTime + 60_000 // Role phase lasts 60s
            nightManager.broadcastNightStatus(lockedSession)

            val actors = mutableListOf<Long>()
            for ((playerId, player) in lockedSession.players) {
                if (!player.alive || player.user?.idLong == null || player.wolf) continue
                val uid = player.user!!.idLong
                val actions = roleActionService.getAvailableActionsForPlayer(lockedSession, uid)

                if (actions.isNotEmpty()) {
                    actors.add(uid)
                    actionUIService.promptPlayerForAction(guildId, lockedSession, uid, playerId, actions, 60)

                    // Set status to ACTING
                    updateStatusToActing(lockedSession, uid)
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
        gameSessionService.getSession(guildId).getOrNull()?.addLog(LogType.SYSTEM, "夜晚結束，天亮了")
        // Transition is usually triggered by the engine, but we notify dashboard
        nightManager.notifyPhaseUpdate(guildId)
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
                    // Sync votes to session state for dashboard inside lock
                    gameSessionService.withLockedSession(guildId) { lockedSession ->
                        syncWolfVotes(lockedSession)
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
        return groupState.votes.size >= groupState.participants.size
    }

    private fun allActorsSubmitted(guildId: Long): Boolean {
        val session = gameSessionService.getSession(guildId).orElse(null) ?: return true
        val statuses = session.stateData.actionStatuses
        return statuses.values.all { it.status == "SUBMITTED" || it.status == "SKIPPED" }
    }

    private fun syncWolfVotes(session: Session) {
        val groupState = actionUIService.getGroupState(session, PredefinedRoles.WEREWOLF_KILL) ?: return

        val votesMap = session.stateData.werewolfVotes
        groupState.votes.forEach { (voterId, targetId) ->
            val vote = votesMap.getOrPut(voterId.toString()) {
                WolfVote(voterId = voterId.toString())
            }
            vote.targetId = targetId.toString()
        }
    }

    private fun finalizeWerewolfPhase(guildId: Long) {
        gameSessionService.withLockedSession(guildId) { session ->
            val groupState =
                actionUIService.getGroupState(session, PredefinedRoles.WEREWOLF_KILL) ?: return@withLockedSession

            // Timeout handling: default missing votes to skip
            groupState.participants.forEach { uid ->
                if (groupState.votes.none { it.userId == uid }) {
                    groupState.votes.add(dev.robothanzo.werewolf.game.model.GroupVote(uid, SKIP_TARGET_ID))
                }
            }

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

            session.players.values.filter { it.alive && it.wolf }.forEach { it.channel?.sendMessage(msg)?.queue() }
            session.judgeTextChannel?.sendMessage(resultText)?.queue()
            session.addLog(LogType.SYSTEM, resultText.replace("**", "").replace("✓ ", "").replace("：", " → "))

            // Record wolf kill for Witch to see in UI (this current night)
            session.stateData.nightWolfKillTargetId =
                if (chosenTarget != null && chosenTarget != SKIP_TARGET_ID) chosenTarget else null

            if (chosenTarget != null) {
                // Warning: submitAction has its own lock, but it's reentrant.
                roleActionService.submitAction(
                    guildId,
                    PredefinedRoles.WEREWOLF_KILL,
                    groupState.participants.firstOrNull() ?: 0L,
                    listOf(chosenTarget),
                    "GROUP"
                )
            }

            syncWolfVotes(session)
            actionUIService.clearGroupState(session, PredefinedRoles.WEREWOLF_KILL)
        }
    }

    private fun updateStatusToActing(session: Session, uid: Long) {
        val statusesMap = session.stateData.actionStatuses
        val currentStatus = statusesMap[uid.toString()] ?: return
        if (currentStatus.status == "PENDING") {
            currentStatus.status = "ACTING"
            gameSessionService.saveSession(session)
            nightManager.broadcastNightStatus(session)
        }
    }

    override fun onEnd(session: Session, service: GameStateService) {
        // Clean up expired prompts and send timeout notifications
        actionUIService.cleanupExpiredPrompts(session.guildId, session)

        // Clear prompts from session state (Persistent state cleanup)
        session.stateData.actionPrompts.clear()
        session.stateData.groupStates.clear()

        // Clear night-specific data to save storage
        session.stateData.werewolfMessages.clear()
        session.stateData.werewolfVotes.clear()
        session.stateData.actionStatuses.clear()
        session.stateData.phaseType = null
        session.stateData.phaseStartTime = 0
        session.stateData.phaseEndTime = 0
        session.stateData.nightWolfKillTargetId = null
        gameSessionService.saveSession(session)
    }

    override fun handleInput(session: Session, input: Map<String, Any>): Map<String, Any> {
        val action = input["action"] as? String

        return when (action) {
            "submit_action" -> {
                val actionDefinitionId = input["actionDefinitionId"] as? String
                val actorUserId = parseLong(input["actorUserId"])

                @Suppress("UNCHECKED_CAST")
                val targetUserIds = (input["targetUserIds"] as? List<*>)?.mapNotNull { parseLong(it) } ?: emptyList()
                val submittedBy = input["submittedBy"] as? String ?: "PLAYER"

                if (actionDefinitionId == null || actorUserId == null) {
                    return mapOf("success" to false, "error" to "Missing required parameters")
                }

                roleActionService.submitAction(
                    session.guildId,
                    actionDefinitionId,
                    actorUserId,
                    targetUserIds,
                    submittedBy
                )

                mapOf("success" to true)
            }

            else -> mapOf("success" to true)
        }
    }

    override fun getDurationSeconds(session: Session): Int {
        return 120 // Allow extra time for wolf discussion and UI interaction
    }
}
