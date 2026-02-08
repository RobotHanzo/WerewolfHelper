package dev.robothanzo.werewolf.game.model

import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.roles.PredefinedRoles
import dev.robothanzo.werewolf.game.roles.RoleRegistry
import dev.robothanzo.werewolf.game.roles.actions.RoleAction
import dev.robothanzo.werewolf.game.roles.actions.RoleActionExecutor
import org.slf4j.LoggerFactory
import dev.robothanzo.werewolf.game.model.Role as GameRole

private val log = LoggerFactory.getLogger("SessionExtensions")

fun Session.getAvailableActionsForPlayer(playerId: Int, roleRegistry: RoleRegistry): List<RoleAction> {
    val player = getPlayer(playerId) ?: return emptyList()

    if (!player.alive) {
        return emptyList()
    }

    val actions = mutableListOf<RoleAction>()

    // Get actions from hydrated roles
    for (roleName in player.roles ?: emptyList()) {
        val roleObj: GameRole? = hydratedRoles[roleName] ?: roleRegistry.getRole(roleName)
        if (roleObj != null) {
            for (action in roleObj.getActions()) {
                if (isActionAvailable(playerId, action.actionId, roleRegistry)) {
                    actions.add(action)
                }
            }
        }
    }

    // Gifted actions for DarkMerchantTradeRecipient
    if (stateData.darkMerchantTradeRecipientId == playerId) {
        val skillType = stateData.darkMerchantGiftedSkill
        val giftedActionId = when (skillType) {
            "SEER" -> ActionDefinitionId.MERCHANT_SEER_CHECK
            "POISON" -> ActionDefinitionId.MERCHANT_POISON
            "GUN" -> ActionDefinitionId.MERCHANT_GUN
            else -> null
        }
        giftedActionId?.let { id ->
            if (isActionAvailable(playerId, id, roleRegistry)) {
                roleRegistry.getAction(id)?.let { actions.add(it) }
            }
        }
    }

    return actions
}

fun Session.isActionAvailable(
    playerId: Int,
    actionDefinitionId: ActionDefinitionId,
    roleRegistry: RoleRegistry
): Boolean {
    val action = roleRegistry.getAction(actionDefinitionId) ?: return false

    // Check if current game state allows this action's timing
    val isNightPhase = currentState.contains("NIGHT", ignoreCase = true)

    // Filter out actions that don't match current timing
    when (action.timing) {
        ActionTiming.NIGHT -> if (!isNightPhase) return false
        ActionTiming.DAY -> if (isNightPhase) return false
        ActionTiming.ANYTIME -> {} // Always available
        ActionTiming.DEATH_TRIGGER -> {
            // Check if this player has a death trigger available
            if (!hasDeathTriggerAvailable(playerId, roleRegistry)) return false
        }
    }

    // Check usage limit
    if (action.usageLimit == -1) {
        return true
    }

    val usage = getActionUsageCount(playerId, actionDefinitionId, roleRegistry)
    if (usage >= action.usageLimit) return false

    // Wolf Younger Brother extra kill logic
    val currentPlayer = getPlayer(playerId)
    if (actionDefinitionId == ActionDefinitionId.WOLF_YOUNGER_BROTHER_EXTRA_KILL) {
        // Delegate to action's isAvailable method which now contains the logic
        return action.isAvailable(this, playerId)
    }

    if (actionDefinitionId == ActionDefinitionId.WEREWOLF_KILL && currentPlayer?.roles?.contains("狼弟") == true) {
        val isWolfBrotherAlive = alivePlayers().values.any { it.roles?.contains("狼兄") == true }
        // Younger Brother only gets to kill if Brother is dead
        if (isWolfBrotherAlive) return false
    }

    return true
}

fun Session.getActionUsageCount(
    playerId: Int,
    actionDefinitionId: ActionDefinitionId,
    roleRegistry: RoleRegistry
): Int {
    val action = roleRegistry.getAction(actionDefinitionId) ?: return 0
    return action.getUsageCount(this, playerId)
}

fun Session.hasDeathTriggerAvailable(playerId: Int, roleRegistry: RoleRegistry): Boolean {
    // Get all death trigger actions from registry
    val deathTriggerActions = roleRegistry.getAllActions()
        .filter { it.timing == ActionTiming.DEATH_TRIGGER }

    // Check if any death trigger action is available for this player
    for (action in deathTriggerActions) {
        val availablePlayerId = stateData.deathTriggerAvailableMap[action.actionId]
        if (availablePlayerId == playerId) {
            return true
        }
    }

    return false
}

fun Session.executeDeathTriggers(roleRegistry: RoleRegistry, roleActionExecutor: RoleActionExecutor): List<Int> {
    val killedByTriggers = mutableListOf<Int>()

    // Execute all pending death trigger actions
    val pendingActions = stateData.submittedActions.filter { it.status == ActionStatus.SUBMITTED }
        .filter {
            val action = it.actionDefinitionId?.let { actionId -> roleRegistry.getAction(actionId) }
            action?.timing == ActionTiming.DEATH_TRIGGER
        }

    if (pendingActions.isEmpty()) {
        return emptyList()
    }

    // Execute actions using RoleActionExecutor
    val executionResult = roleActionExecutor.executeActions(this, pendingActions)

    // Collect all deaths from death triggers
    for ((_, deaths) in executionResult.deaths) {
        killedByTriggers.addAll(deaths)
    }

    // Clear pending death trigger actions from submittedActions
    stateData.submittedActions.removeIf {
        val action = it.actionDefinitionId?.let { actionId -> roleRegistry.getAction(actionId) }
        action?.timing == ActionTiming.DEATH_TRIGGER && it.status == ActionStatus.SUBMITTED
    }
    // Caller is responsible for saving session

    return killedByTriggers
}

/**
 * Updates the action status in the session state. 
 * Does NOT save session; caller must save.
 */
fun Session.updateActionStatus(
    actorPlayerId: Int,
    status: ActionStatus,
    actionId: ActionDefinitionId? = null,
    targetPlayerIds: List<Int> = emptyList()
) {
    val actorPlayer = getPlayer(actorPlayerId) ?: return
    val actorRole = actorPlayer.roles?.firstOrNull() ?: "未知"

    // Find match by actor only (reuse any pending action to avoid duplicates)
    val actionInstance = stateData.submittedActions.find {
        it.actor == actorPlayerId && it.status != ActionStatus.SUBMITTED
    }

    if (actionInstance == null) {
        // Create new only if absolutely needed
        val newInstance = RoleActionInstance(
            actor = actorPlayerId,
            actorRole = actorRole,
            actionDefinitionId = actionId,
            targets = if (targetPlayerIds.isNotEmpty()) targetPlayerIds.toMutableList() else mutableListOf(),
            submittedBy = ActionSubmissionSource.PLAYER,
            status = status
        )
        stateData.submittedActions.add(newInstance)
    } else {
        // Mutate existing
        if (targetPlayerIds.isNotEmpty()) {
            actionInstance.targets.clear()
            actionInstance.targets.addAll(targetPlayerIds)
        }
        actionInstance.status = status
    }
}

fun Session.resolveNightActions(
    roleActionExecutor: RoleActionExecutor,
    roleRegistry: RoleRegistry
): NightResolutionResult {
    val actionsToProcess = stateData.submittedActions
        .filter { it.status == ActionStatus.SUBMITTED }
        .toMutableList()

    log.info(
        "NightResolution: Initial actions to process: {}",
        actionsToProcess.map { "${it.actionDefinitionId} by ${it.actor}" })

    // Self-Healing: Check if WEREWOLF_KILL is missing but valid votes exist
    val wolfKillAction = actionsToProcess.find { it.actionDefinitionId == ActionDefinitionId.WEREWOLF_KILL }
    if (wolfKillAction == null) {
        val wolfState = stateData.wolfStates[PredefinedRoles.WEREWOLF_KILL]
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
                        actionDefinitionId = ActionDefinitionId.WEREWOLF_KILL,
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
    // Filter out immediate actions (like Seer) that have already been executed upon submission
    val actionsToExecute = actionsToProcess.filter {
        val actionDef = it.actionDefinitionId?.let { actionId -> roleRegistry.getAction(actionId) }
        actionDef?.isImmediate != true
    }

    val executionResult = roleActionExecutor.executeActions(this, actionsToExecute)
    log.info(
        "NightResolution: ExecutionResult - Deaths={}, Saved={}, Protected={}",
        executionResult.deaths, executionResult.saved, executionResult.protectedPlayers
    )

    // Move processed actions to executedActions record
    val currentDay = day
    val history = stateData.executedActions.getOrPut(currentDay) { mutableListOf() }
    history.addAll(actionsToProcess)

    // Mark as PROCESSED or remove from submitted list
    stateData.submittedActions.removeIf {
        it.status == ActionStatus.SUBMITTED || it.status == ActionStatus.SKIPPED
    }

    return NightResolutionResult(
        deaths = executionResult.deaths,
        saved = executionResult.saved
    )
}

fun Session.validateAndSubmitAction(
    actionDefinitionId: ActionDefinitionId,
    actorPlayerId: Int,
    targetPlayerIds: MutableList<Int>,
    submittedBy: String,
    roleRegistry: RoleRegistry,
    roleActionExecutor: RoleActionExecutor
): Map<String, Any> {
    val actionDef = roleRegistry.getAction(actionDefinitionId)
        ?: return mapOf("success" to false, "error" to "Action not found")

    val isNightPhase = currentState.contains("NIGHT", ignoreCase = true)
    if (actionDef.timing == ActionTiming.NIGHT && !isNightPhase) {
        return mapOf("success" to false, "error" to "目前不是夜晚階段")
    }
    if (actionDef.timing == ActionTiming.DAY && isNightPhase) {
        return mapOf("success" to false, "error" to "目前不是白天階段")
    }

    // Prevent multiple actions per phase (for player submissions only)
    val actor = getPlayer(actorPlayerId)
        ?: return mapOf("success" to false, "error" to "Actor not found")

    if (submittedBy == "PLAYER" && actor.actionSubmitted && !actionDef.allowMultiplePerPhase) {
        return mapOf("success" to false, "error" to "你已經提交過行動，無法再次選擇")
    }

    // Perform centralized validation
    val validationError = actionDef.validate(this, actorPlayerId, targetPlayerIds)
    if (validationError != null) {
        log.warn(
            "Action validation failed for action {} by actor {}: {}",
            actionDefinitionId,
            actorPlayerId,
            validationError
        )
        return mapOf("success" to false, "error" to validationError)
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
    stateData.submittedActions.removeIf { it.actor == actorPlayerId && it.actionDefinitionId == actionDefinitionId }
    stateData.submittedActions.add(action)

    // Mark action as submitted (for players only)
    if (submittedBy == "PLAYER") {
        actor.actionSubmitted = true
    }

    log.info(
        "[ActionSubmit] Stored action. Submitted actions now: {}",
        stateData.submittedActions.size
    )

    // Execute immediately if requested (e.g., Seer)
    if (actionDef.isImmediate) {
        roleActionExecutor.executeActionInstance(this, action)
    }

    // Call submission hook
    actionDef.onSubmitted(this, actorPlayerId, targetPlayerIds)

    // Update UI status using our extension method
    updateActionStatus(
        actorPlayerId,
        if (targetPlayerIds.contains(SKIP_TARGET_ID)) ActionStatus.SKIPPED else ActionStatus.SUBMITTED,
        actionDefinitionId,
        targetPlayerIds
    )

    return mapOf("success" to true, "message" to "Action submitted")
}
