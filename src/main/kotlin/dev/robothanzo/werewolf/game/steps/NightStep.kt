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
                    // Wolf Younger Brother (狼弟) only joins discussion if Brother is dead
                    val isBrotherAlive = session.alivePlayers().values.any { it.roles?.contains("狼兄") == true }
                    if (p.roles?.contains("狼弟") == true && isBrotherAlive) {
                        continue
                    }
                } else {
                    val actions = roleActionService.getAvailableActionsForPlayer(session, p.id)
                    p.actionSubmitted = false
                    if (actions.isNotEmpty()) {
                        session.stateData.submittedActions.add(
                            RoleActionInstance(
                                actor = p.id,
                                actorRole = (p.roles?.firstOrNull() ?: "未知"),
                                actionDefinitionId = "", // Not chosen yet
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
        // 1. Werewolf Voting Phase
        val session = gameSessionService.getSession(guildId).orElseThrow()
        val werewolves = session.players.values.filter { p ->
            p.alive && p.wolf && (p.roles?.contains("狼弟") != true || session.alivePlayers().values.none {
                it.roles?.contains(
                    "狼兄"
                ) == true
            })
        }.map { it.id }.sorted()

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
                var actions = roleActionService.getAvailableActionsForPlayer(lockedSession, pid)

                // Filter out standard werewolf kill as it's handled in the group phase
                if (player.wolf) {
                    actions = actions.filter { it.actionId != PredefinedRoles.WEREWOLF_KILL }
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
        val session = gameSessionService.getSession(guildId).orElse(null) ?: return true
        return session.stateData.submittedActions.filter { it.actorRole != "狼人" }.all {
            it.status == ActionStatus.SUBMITTED || it.status == ActionStatus.SKIPPED
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

            session.players.values.filter { it.alive && it.wolf }.forEach { it.channel?.sendMessage(msg)?.queue() }
            session.judgeTextChannel?.sendMessage(resultText)?.queue()
            session.addLog(LogType.SYSTEM, resultText.replace("**", "").replace("✓ ", "").replace("：", " → "))

            if (chosenTarget != null) {
                // Warning: submitAction has its own lock, but it's reentrant.
                // Find a valid alive wolf to be the actor
                val actorId = groupState.electorates.firstOrNull { pid ->
                    session.alivePlayers().containsKey(pid.toString())
                } ?: groupState.electorates.firstOrNull() ?: 0

                roleActionService.submitAction(
                    guildId,
                    PredefinedRoles.WEREWOLF_KILL,
                    actorId,
                    arrayListOf(chosenTarget),
                    "SYSTEM"
                )
            }
        }
    }

    private fun finalizeRoleActionsPhase(guildId: Long) {
        gameSessionService.withLockedSession(guildId) { session ->
            actionUIService.cleanupExpiredPrompts(guildId, session)
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
        actionUIService.cleanupExpiredPrompts(session.guildId, session)

        // Clear prompts
        // We do NOT clear submittedActions, wolfStates, or messages here to preserve them for review/dashboard
        // They will be cleared at the start of the next NightStep
        
        session.stateData.phaseType = null
        session.stateData.phaseStartTime = 0
        session.stateData.phaseEndTime = 0
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
