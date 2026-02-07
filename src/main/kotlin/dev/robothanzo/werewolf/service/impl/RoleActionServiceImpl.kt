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
        targetPlayerIds: MutableList<Int>,
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
                log.warn(
                    "Action validation failed for action {} by actor {}: {}",
                    actionDefinitionId,
                    actorPlayerId,
                    validationError
                )
                return@withLockedSession mapOf("success" to false, "error" to validationError)
            }

            // Create action instance
            val source = when (submittedBy) {
                "JUDGE" -> ActionSubmissionSource.JUDGE
                "SYSTEM" -> ActionSubmissionSource.SYSTEM
                else -> ActionSubmissionSource.PLAYER
            }

            val action = RoleActionInstance(
                actor = actorPlayerId,
                actorRole = (actor.roles?.firstOrNull() ?: "未知"),
                actionDefinitionId = actionDefinitionId,
                targets = targetPlayerIds,
                submittedBy = source,
                status = if (targetPlayerIds.contains(SKIP_TARGET_ID)) ActionStatus.SKIPPED else ActionStatus.SUBMITTED
            )

            // Store in submitted actions (centralized list)
            // Remove existing action of same definition from same actor to prevent duplicates
            session.stateData.submittedActions.removeIf { it.actor == actorPlayerId && it.actionDefinitionId == actionDefinitionId }
            session.stateData.submittedActions.add(action)

            // Mark action as submitted (for players only)
            if (submittedBy == "PLAYER") {
                actor.actionSubmitted = true
            }

            log.info(
                "[ActionSubmit] Stored action for guild {}. Submitted actions now: {}",
                guildId,
                session.stateData.submittedActions.size
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

    override fun resolveNightActions(session: Session): NightResolutionResult {
        val actionsToProcess = session.stateData.submittedActions
            .filter { it.status == ActionStatus.SUBMITTED }
            .toMutableList()

        log.info(
            "NightResolution: Initial actions to process: {}",
            actionsToProcess.map { "${it.actionDefinitionId} by ${it.actor}" })

        // Self-Healing: Check if WEREWOLF_KILL is missing but valid votes exist
        val wolfKillAction = actionsToProcess.find { it.actionDefinitionId == PredefinedRoles.WEREWOLF_KILL }
        if (wolfKillAction == null) {
            val wolfState = session.stateData.wolfStates[PredefinedRoles.WEREWOLF_KILL]
            if (wolfState != null && wolfState.votes.any { it.targetId != null && it.targetId != SKIP_TARGET_ID }) {
                log.info("NightResolution: WEREWOLF_KILL action missing/skipped! Attempting to reconstruct from votes...")
                // Re-resolve vote
                val voteCounts = wolfState.votes
                    .filter { it.targetId != null && it.targetId != SKIP_TARGET_ID }
                    .groupingBy { it.targetId!! }
                    .eachCount()

                if (voteCounts.isNotEmpty()) {
                    val maxVotes = voteCounts.maxOf { it.value }
                    val topTargets = voteCounts.filterValues { it == maxVotes }.keys.toList()
                    val chosenTarget = topTargets.randomOrNull()

                    if (chosenTarget != null) {
                        val reconstructedAction = RoleActionInstance(
                            actor = wolfState.electorates.firstOrNull() ?: 0,
                            actorRole = "WEREWOLF",
                            actionDefinitionId = PredefinedRoles.WEREWOLF_KILL,
                            targets = arrayListOf(chosenTarget),
                            submittedBy = ActionSubmissionSource.SYSTEM,
                            status = ActionStatus.SUBMITTED
                        )
                        log.info("NightResolution: Reconstructed WEREWOLF_KILL: {}", reconstructedAction)
                        actionsToProcess.add(reconstructedAction)
                    }
                }
            }
        }

        if (actionsToProcess.isEmpty()) {
            log.warn("NightResolution: No actions to process!")
            return NightResolutionResult(emptyMap(), emptyList())
        }

        // Execute all actions using the new action executor
        val executionResult = roleActionExecutor.executeActions(session, actionsToProcess)
        log.info(
            "NightResolution: ExecutionResult - Deaths={}, Saved={}, Protected={}",
            executionResult.deaths, executionResult.saved, executionResult.protectedPlayers
        )

        // Move processed actions to executedActions record
        val currentDay = session.day
        val history = session.stateData.executedActions.getOrPut(currentDay) { mutableListOf() }
        history.addAll(actionsToProcess)

        // Mark as PROCESSED or remove from submitted list
        session.stateData.submittedActions.removeIf {
            it.status == ActionStatus.SUBMITTED || it.status == ActionStatus.SKIPPED
        }

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
        return session.stateData.submittedActions.filter { it.status == ActionStatus.SUBMITTED }
    }

    override fun getActionUsageCount(session: Session, playerId: Int, actionDefinitionId: String): Int {
        val action = roleRegistry.getAction(actionDefinitionId) ?: return 0
        return action.getUsageCount(session, playerId)
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

        // Clear pending death trigger actions from submittedActions
        session.stateData.submittedActions.removeIf {
            val action = roleRegistry.getAction(it.actionDefinitionId)
            action?.timing == ActionTiming.DEATH_TRIGGER && it.status == ActionStatus.SUBMITTED
        }
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
        val actorRole = actorPlayer.roles?.firstOrNull() ?: "未知"

        // Find match by actor only (reuse any pending action)
        val actionInstance = activeSession.stateData.submittedActions.find {
            it.actor == actorPlayerId && it.status != ActionStatus.SUBMITTED
        }

        if (actionInstance == null) {
            // Create new only if absolutely needed
            val newInstance = RoleActionInstance(
                actor = actorPlayerId,
                actorRole = actorRole,
                actionDefinitionId = actionId ?: "",
                targets = if (targetPlayerIds.isNotEmpty()) targetPlayerIds.toMutableList() else mutableListOf(),
                submittedBy = ActionSubmissionSource.PLAYER,
                status = status
            )
            activeSession.stateData.submittedActions.add(newInstance)
        } else {
            // Mutate existing
            if (actionId != null) actionInstance.actionDefinitionId = actionId
            if (targetPlayerIds.isNotEmpty()) {
                actionInstance.targets.clear()
                actionInstance.targets.addAll(targetPlayerIds)
            }
            actionInstance.status = status
        }

        // session save handled by gameSessionService
        WerewolfApplication.gameSessionService.saveSession(activeSession)

        // Broadcast update via NightManager
        WerewolfApplication.gameSessionService.broadcastSessionUpdate(activeSession)
        nightManager.notifyPhaseUpdate(guildId)
    }
}
