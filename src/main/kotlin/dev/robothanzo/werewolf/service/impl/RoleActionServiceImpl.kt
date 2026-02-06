package dev.robothanzo.werewolf.service.impl

import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.*
import dev.robothanzo.werewolf.game.roles.PredefinedRoles
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
        actorPlayerId: Int,
        targetPlayerIds: List<Int>,
        submittedBy: String,
        metadata: Map<String, Any>
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
            val actor = session.getPlayer(actorPlayerId)
                ?: return@withLockedSession mapOf("success" to false, "error" to "Actor not found")

            if (submittedBy == "PLAYER" && actor.actionSubmitted && !actionDef.allowMultiplePerPhase) {
                return@withLockedSession mapOf("success" to false, "error" to "你已經提交過行動，無法再次選擇")
            }

            // Perform centralized validation
            val validationError = actionDef.validate(session, actorPlayerId, targetPlayerIds)
            if (validationError != null) {
                return@withLockedSession mapOf("success" to false, "error" to validationError)
            }

            // Create action instance
            val source = if (submittedBy == "JUDGE") ActionSubmissionSource.JUDGE else ActionSubmissionSource.PLAYER
            val action = RoleActionInstance(
                actor = actorPlayerId,
                actionDefinitionId = actionDefinitionId,
                targets = targetPlayerIds,
                submittedBy = source,
                metadata = metadata
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
            actionDef.onSubmitted(session, actorPlayerId, targetPlayerIds)

            // Update UI status (Passing session ensures it uses the locked one)
            updateActionStatus(
                guildId,
                actorPlayerId,
                if (targetPlayerIds.contains(SKIP_TARGET_ID)) ActionStatus.SKIPPED else ActionStatus.SUBMITTED,
                actionDefinitionId,
                targetPlayerIds,
                session
            )

            mapOf("success" to true, "message" to "Action submitted")
        }
    }

    override fun getAvailableActionsForPlayer(session: Session, playerId: Int): List<RoleAction> {
        val player = session.getPlayer(playerId)
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
                    if (isActionAvailable(session, playerId, action.actionId)) {
                        actions.add(action)
                    }
                }
            }
        }

        // Gifted actions for DarkMerchantTradeRecipient
        if (session.stateData.darkMerchantTradeRecipientId == playerId) {
            val skillType = session.stateData.darkMerchantGiftedSkill
            val giftedActionId = when (skillType) {
                "SEER" -> PredefinedRoles.MERCHANT_SEER_CHECK
                "POISON" -> PredefinedRoles.MERCHANT_POISON
                "GUN" -> PredefinedRoles.MERCHANT_GUN
                else -> null
            }
            giftedActionId?.let { id ->
                if (isActionAvailable(session, playerId, id)) {
                    roleRegistry.getAction(id)?.let { actions.add(it) }
                }
            }
        }

        return actions
    }

    override fun getAvailableActionsForJudge(session: Session): Map<Int, List<RoleAction>> {
        val result = mutableMapOf<Int, List<RoleAction>>()
        for (player in session.alivePlayers().values) {
            val playerId = player.id
            val actions = getAvailableActionsForPlayer(session, playerId)
            if (actions.isNotEmpty()) {
                result[playerId] = actions
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

    override fun isActionAvailable(session: Session, playerId: Int, actionDefinitionId: String): Boolean {
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
                // Check if this player has a death trigger available
                if (!hasDeathTriggerAvailable(session, playerId)) return false
            }
        }

        // Check usage limit
        if (action.usageLimit == -1) {
            return true
        }

        val usage = getActionUsageCount(session, playerId, actionDefinitionId)
        if (usage >= action.usageLimit) return false

        // Wolf Younger Brother extra kill logic
        val currentPlayer = session.getPlayer(playerId)
        if (actionDefinitionId == PredefinedRoles.WOLF_YOUNGER_BROTHER_EXTRA_KILL) {
            // Delegate to action's isAvailable method which now contains the logic
            return action.isAvailable(session, playerId)
        }

        if (actionDefinitionId == PredefinedRoles.WEREWOLF_KILL && currentPlayer?.roles?.contains("狼弟") == true) {
            val isWolfBrotherAlive = session.alivePlayers().values.any { it.roles?.contains("狼兄") == true }
            // Younger Brother only gets to kill if Brother is dead
            if (isWolfBrotherAlive) return false
        }

        return true
    }

    override fun getPendingActions(session: Session): List<RoleActionInstance> {
        return session.stateData.pendingActions
    }

    override fun getActionUsageCount(session: Session, playerId: Int, actionDefinitionId: String): Int {
        return session.stateData.actionData[playerId.toString()]?.usage?.get(actionDefinitionId) ?: 0
    }

    override fun executeDeathTriggers(session: Session): List<Int> {
        val killedByTriggers = mutableListOf<Int>()

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

        session.stateData.pendingActions.clear()
        session.stateData.pendingActions.addAll(remainingActions)
        WerewolfApplication.gameSessionService.saveSession(session)

        return killedByTriggers
    }

    override fun hasDeathTriggerAvailable(session: Session, playerId: Int): Boolean {
        // Get all death trigger actions from registry
        val deathTriggerActions = roleRegistry.getAllActions()
            .filter { it.timing == ActionTiming.DEATH_TRIGGER }

        // Check if any death trigger action is available for this player
        for (action in deathTriggerActions) {
            val availablePlayerId = session.stateData.deathTriggerAvailableMap[action.actionId]
            if (availablePlayerId == playerId) {
                return true
            }
        }

        return false
    }

    override fun updateActionStatus(
        guildId: Long,
        actorPlayerId: Int,
        status: ActionStatus,
        actionId: String?,
        targetPlayerIds: List<Int>,
        session: Session?
    ) {
        if (session != null) {
            performUpdateActionStatus(guildId, actorPlayerId, status, actionId, targetPlayerIds, session)
        } else {
            WerewolfApplication.gameSessionService.withLockedSession(guildId) { lockedSession ->
                performUpdateActionStatus(guildId, actorPlayerId, status, actionId, targetPlayerIds, lockedSession)
            }
        }
    }

    private fun performUpdateActionStatus(
        guildId: Long,
        actorPlayerId: Int,
        status: ActionStatus,
        actionId: String?,
        targetPlayerIds: List<Int>,
        activeSession: Session
    ) {
        val actorPlayer = activeSession.getPlayer(actorPlayerId) ?: return

        val actionData = activeSession.stateData.actionData.getOrPut(actorPlayerId.toString()) {
            ActionData(
                playerId = actorPlayerId,
                role = (actorPlayer.roles?.firstOrNull() ?: "未知")
            )
        }

        actionData.status = status
        if (actionId != null) {
            val actionDef = roleRegistry.getAction(actionId)
            if (actionDef != null) {
                actionData.selectedAction = ActionInfo(
                    actionDef.actionId,
                    actionDef.actionName,
                    actionDef.timing
                )
            }
        }

        if (targetPlayerIds.isNotEmpty()) {
            actionData.selectedTargets = targetPlayerIds
        }

        actionData.submittedAt = System.currentTimeMillis()

        // session save handled by gameSessionService
        WerewolfApplication.gameSessionService.saveSession(activeSession)

        // Broadcast update via NightManager
        nightManager.broadcastNightStatus(activeSession)
        nightManager.notifyPhaseUpdate(guildId)
    }
}
