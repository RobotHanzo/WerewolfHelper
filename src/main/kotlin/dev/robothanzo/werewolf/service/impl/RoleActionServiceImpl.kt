package dev.robothanzo.werewolf.service.impl

import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.*
import dev.robothanzo.werewolf.game.roles.actions.RoleAction
import dev.robothanzo.werewolf.game.roles.actions.RoleActionExecutor
import dev.robothanzo.werewolf.service.NightManager
import dev.robothanzo.werewolf.service.NightResolutionResult
import dev.robothanzo.werewolf.service.RoleActionService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import dev.robothanzo.werewolf.game.model.Role as GameRole

@Service
class RoleActionServiceImpl(
    private val roleActionExecutor: RoleActionExecutor,
    private val nightManager: NightManager,
    private val roleRegistry: dev.robothanzo.werewolf.game.roles.RoleRegistry
) : RoleActionService {
    private val log = LoggerFactory.getLogger(RoleActionServiceImpl::class.java)

    override fun submitAction(
        guildId: Long,
        actionDefinitionId: String,
        actorUserId: Long,
        targetUserIds: List<Long>,
        submittedBy: String
    ): Map<String, Any> {
        return WerewolfApplication.gameSessionService.withLockedSession(guildId) { session ->
            val actionDef = roleRegistry.getAction(actionDefinitionId)
                ?: return@withLockedSession mapOf("success" to false, "error" to "Action not found")

            val currentState = session.currentState
            val isNightPhase = currentState.contains("NIGHT", ignoreCase = true)
            if (actionDef.timing == ActionTiming.NIGHT && !isNightPhase) {
                return@withLockedSession mapOf("success" to false, "error" to "目前不是夜晚階段")
            }
            if (actionDef.timing == ActionTiming.DAY && isNightPhase) {
                return@withLockedSession mapOf("success" to false, "error" to "目前不是白天階段")
            }

            // Prevent multiple actions per phase (for player submissions only)
            val actor = session.getPlayer(actorUserId)!!
            if (submittedBy == "PLAYER" && actor.actionSubmitted && !actionDef.allowMultiplePerPhase) {
                return@withLockedSession mapOf("success" to false, "error" to "你已經提交過行動，無法再次選擇")
            }

            // Perform centralized validation
            val validationError = actionDef.validate(session, actorUserId, targetUserIds)
            if (validationError != null) {
                return@withLockedSession mapOf("success" to false, "error" to validationError)
            }

            // Create action instance
            val source = if (submittedBy == "JUDGE") ActionSubmissionSource.JUDGE else ActionSubmissionSource.PLAYER
            val action = RoleActionInstance(
                actor = actorUserId,
                actionDefinitionId = actionDefinitionId,
                targets = targetUserIds,
                submittedBy = source
            )

            // Store in pending actions
            session.stateData.pendingActions.add(action)

            // Mark action as submitted (for players only, not judges)
            if (submittedBy == "PLAYER") {
                actor.actionSubmitted = true
            }

            log.info(
                "[ActionSubmit] Stored action for guild {}. Pending actions now: {}",
                guildId,
                session.stateData.pendingActions.size
            )

            // Execute immediately if requested (e.g., Seer)
            if (actionDef.isImmediate) {
                roleActionExecutor.executeActionInstance(session, action)
            }

            // Call submission hook
            actionDef.onSubmitted(session, actorUserId, targetUserIds)

            // Update UI status (Passing session ensures it uses the locked one)
            updateActionStatus(
                guildId,
                actorUserId,
                if (targetUserIds.contains(SKIP_TARGET_ID)) "SKIPPED" else "SUBMITTED",
                actionDefinitionId,
                targetUserIds,
                session
            )

            mapOf("success" to true, "message" to "Action submitted")
        }
    }

    override fun getAvailableActionsForPlayer(session: Session, userId: Long): List<RoleAction> {
        val player = session.players.values.find { it.user?.idLong == userId }
            ?: return emptyList()

        if (!player.alive) {
            return emptyList()
        }

        val actions = mutableListOf<RoleAction>()

        // Get actions from hydrated roles
        for (roleName in player.roles ?: emptyList()) {
            val roleObj: GameRole? = session.hydratedRoles[roleName] ?: roleRegistry.getRole(roleName)
            if (roleObj != null) {
                for (action in roleObj.getActions()) {
                    if (isActionAvailable(session, userId, action.actionId)) {
                        actions.add(action)
                    }
                }
            }
        }

        return actions
    }

    override fun getAvailableActionsForJudge(session: Session): Map<Long, List<RoleAction>> {
        val result = mutableMapOf<Long, List<RoleAction>>()
        for ((_, player) in session.players) {
            if (player.alive) {
                player.user?.idLong?.let { uid ->
                    val actions = getAvailableActionsForPlayer(session, uid)
                    if (actions.isNotEmpty()) {
                        result[uid] = actions
                    }
                }
            }
        }
        return result
    }

    override fun resolveNightActions(session: Session): NightResolutionResult {
        val pendingActions = session.stateData.pendingActions

        // Execute all actions using the new action executor
        val executionResult = roleActionExecutor.executeActions(session, pendingActions)

        return NightResolutionResult(
            deaths = executionResult.deaths,
            saved = executionResult.saved
        )
    }

    override fun isActionAvailable(session: Session, userId: Long, actionDefinitionId: String): Boolean {
        val action = roleRegistry.getAction(actionDefinitionId)
            ?: return false

        // Check if current game state allows this action's timing
        val currentState = session.currentState
        val isNightPhase = currentState.contains("NIGHT", ignoreCase = true)

        // Filter out actions that don't match current timing
        when (action.timing) {
            ActionTiming.NIGHT -> if (!isNightPhase) return false
            ActionTiming.DAY -> if (isNightPhase) return false
            ActionTiming.ANYTIME -> {} // Always available
            ActionTiming.DEATH_TRIGGER -> {
                // Check if this user has a death trigger available
                if (!hasDeathTriggerAvailable(session, userId)) return false
            }
        }

        // Check usage limit
        if (action.usageLimit == -1) {
            return true
        }

        val usage = getActionUsageCount(session, userId, actionDefinitionId)
        return usage < action.usageLimit
    }

    override fun getPendingActions(session: Session): List<RoleActionInstance> {
        return session.stateData.pendingActions
    }

    override fun getActionUsageCount(session: Session, userId: Long, actionDefinitionId: String): Int {
        val userUsage = session.stateData.actionUsage[userId.toString()] ?: return 0
        return userUsage.getOrDefault(actionDefinitionId, 0)
    }

    override fun executeDeathTriggers(session: Session): List<Long> {
        val killedByTriggers = mutableListOf<Long>()

        // Execute all pending death trigger actions
        val pendingActions = getPendingActions(session)
            .filter {
                val action = roleRegistry.getAction(it.actionDefinitionId)
                action?.timing == ActionTiming.DEATH_TRIGGER
            }

        if (pendingActions.isEmpty()) {
            return emptyList()
        }

        // Execute actions using RoleActionExecutor
        val executionResult = roleActionExecutor.executeActions(session, pendingActions)

        // Collect all deaths from death triggers
        for ((_, deaths) in executionResult.deaths) {
            killedByTriggers.addAll(deaths)
        }

        // Clear pending death trigger actions
        val remainingActions = getPendingActions(session)
            .filter {
                val action = roleRegistry.getAction(it.actionDefinitionId)
                action?.timing != ActionTiming.DEATH_TRIGGER
            }

        session.stateData.pendingActions.addAll(remainingActions)
        WerewolfApplication.gameSessionService.saveSession(session)

        return killedByTriggers
    }

    override fun hasDeathTriggerAvailable(session: Session, userId: Long): Boolean {
        // Get all death trigger actions from registry
        val deathTriggerActions = roleRegistry.getAllActions()
            .filter { it.timing == ActionTiming.DEATH_TRIGGER }

        // Check if any death trigger action is available for this user
        for (action in deathTriggerActions) {
            val availableUserId = session.stateData.deathTriggerAvailableMap[action.actionId]
            if (availableUserId == userId) {
                return true
            }
        }

        return false
    }

    override fun updateActionStatus(
        guildId: Long,
        actorUserId: Long,
        status: String,
        actionId: String?,
        targetUserIds: List<Long>,
        session: Session?
    ) {
        if (session != null) {
            performUpdateActionStatus(guildId, actorUserId, status, actionId, targetUserIds, session)
        } else {
            WerewolfApplication.gameSessionService.withLockedSession(guildId) { lockedSession ->
                performUpdateActionStatus(guildId, actorUserId, status, actionId, targetUserIds, lockedSession)
            }
        }
    }

    private fun performUpdateActionStatus(
        guildId: Long,
        actorUserId: Long,
        status: String,
        actionId: String?,
        targetUserIds: List<Long>,
        activeSession: Session
    ) {
        val actorPlayer = activeSession.getPlayer(actorUserId) ?: return

        val statuses = activeSession.stateData.actionStatuses

        val currentStatus = statuses.getOrPut(actorUserId.toString()) {
            ActionStatus(
                playerId = actorUserId.toString(),
                role = (actorPlayer.roles?.firstOrNull() ?: "未知"),
                status = "PENDING"
            )
        }

        currentStatus.status = status
        if (actionId != null) {
            currentStatus.actionType = roleRegistry.getAction(actionId)?.actionName ?: actionId
        }

        if (targetUserIds.isNotEmpty()) {
            currentStatus.targetId = targetUserIds.firstOrNull()?.toString() ?: ""
        }

        currentStatus.submittedAt = System.currentTimeMillis()

        // session save handled by gameSessionService
        WerewolfApplication.gameSessionService.saveSession(activeSession)

        // Broadcast update via NightManager
        nightManager.broadcastNightStatus(activeSession)
        nightManager.notifyPhaseUpdate(guildId)
    }
}
