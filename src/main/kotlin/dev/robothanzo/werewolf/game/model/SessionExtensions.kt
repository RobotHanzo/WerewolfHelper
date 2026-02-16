package dev.robothanzo.werewolf.game.model

import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.roles.PredefinedRoles
import dev.robothanzo.werewolf.game.roles.RoleRegistry
import dev.robothanzo.werewolf.game.roles.actions.RoleAction
import dev.robothanzo.werewolf.game.roles.actions.RoleActionExecutor
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import dev.robothanzo.werewolf.game.model.Role as GameRole

private val log = LoggerFactory.getLogger("SessionExtensions")

fun Session.getAvailableActionsForPlayer(
    playerId: Int,
    roleRegistry: RoleRegistry,
    ignoreEffect: Boolean = false
): List<RoleAction> {
    val player = getPlayer(playerId) ?: return emptyList()

    val actions = mutableListOf<RoleAction>()

    // Get actions from hydrated roles
    for (roleName in player.roles) {
        val roleObj: GameRole? = hydratedRoles[roleName] ?: roleRegistry.getRole(roleName)
        if (roleObj != null) {
            for (action in roleObj.getActions()) {
                if (isActionAvailable(playerId, action.actionId, roleRegistry, ignoreEffect)) {
                    actions.add(action)
                }
            }
        }
    }

    // Gifted actions from playerOwnedActions (e.g., from Dark Merchant)
    stateData.playerOwnedActions[playerId]?.forEach { (actionIdStr, usesLeft) ->
        if (usesLeft > 0) {
            val actionId = ActionDefinitionId.fromString(actionIdStr)
            if (actionId != null) {
                val action = roleRegistry.getAction(actionId)
                if (action != null && isActionAvailable(playerId, actionId, roleRegistry, ignoreEffect)) {
                    actions.add(action)
                }
            }
        }
    }

    return actions
}

fun Session.isActionAvailable(
    playerId: Int,
    actionDefinitionId: ActionDefinitionId,
    roleRegistry: RoleRegistry,
    ignoreEffect: Boolean = false
): Boolean {
    val action = roleRegistry.getAction(actionDefinitionId) ?: return false
    val player = getPlayer(playerId) ?: return false

    // Only death triggers are allowed if the player is dead
    if (!player.alive && action.timing != ActionTiming.DEATH_TRIGGER) return false

    // Ownership check: must have the action from role OR from playerOwnedActions
    val hasFromRole = player.roles.any { roleName ->
        (hydratedRoles[roleName] ?: roleRegistry.getRole(roleName))?.getActions()
            ?.any { it.actionId == actionDefinitionId } == true
    }

    val hasGifted =
        stateData.playerOwnedActions[playerId]?.containsKey(actionDefinitionId.toString()) == true && !player.wolf

    if (!hasFromRole && !hasGifted) return false

    // Check if current game state allows this action's timing
    val isNightPhase = currentState.contains("NIGHT", ignoreCase = true)

    // Nightmare Fear Check: Feared players cannot use skills at night
    if (!ignoreEffect) {
        val fearedId = stateData.nightmareFearTargets[day]
        if (fearedId == playerId && isNightPhase) {
            return false
        }
    }

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
        val isWolfBrotherAlive = alivePlayers().values.any { it.roles.contains("狼兄") }
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
    val player = getPlayer(playerId) ?: return false
    val roles = player.roles

    return roles.any { roleName ->
        val roleObj = hydratedRoles[roleName] ?: roleRegistry.getRole(roleName)
        roleObj?.getActions()?.any { it.timing == ActionTiming.DEATH_TRIGGER && it.isAvailable(this, playerId) } == true
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

    if (actionsToProcess.isEmpty() && stateData.submittedActions.none { it.status == ActionStatus.PROCESSED }) {
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

    // Add both the actions we just processed AND those that were processed immediately (isImmediate)
    val processedImmediateActions = stateData.submittedActions.filter { it.status == ActionStatus.PROCESSED }
    history.addAll(actionsToProcess)
    history.addAll(processedImmediateActions)

    // Mark as PROCESSED or remove from submitted list
    stateData.submittedActions.removeIf {
        it.status.executed
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

    val action =
        stateData.submittedActions.find { it.actor == actorPlayerId && it.actionDefinitionId == actionDefinitionId }
            ?: stateData.submittedActions.find { it.actor == actorPlayerId && it.status == ActionStatus.PENDING }
            ?: if (submittedBy != "PLAYER" || actionDef.isImmediate) {
                // For non-player submissions (SYSTEM/JUDGE), we can be more lenient and create the instance if missing.
                // Immediate actions also skip the initial PENDING stage often.
                val newInstance = RoleActionInstance(
                    actor = actorPlayerId,
                    actorRole = actor.roles.firstOrNull() ?: "未知",
                    actionDefinitionId = actionDefinitionId,
                    targets = mutableListOf(),
                    submittedBy = source,
                    status = ActionStatus.PENDING
                )
                stateData.submittedActions.add(newInstance)
                log.warn("Created new action instance for non-player submission or immediate action: {}", newInstance)
                newInstance
            } else {
                return mapOf("success" to false, "error" to "Action instance not found for actor")
            }
    action.targets.clear()
    action.targets.addAll(targetPlayerIds)
    action.status = if (targetPlayerIds.contains(SKIP_TARGET_ID)) ActionStatus.SKIPPED else ActionStatus.SUBMITTED

    // Mark action as submitted (for players only)
    if (submittedBy == "PLAYER" && !actionDef.allowMultiplePerPhase) {
        actor.actionSubmitted = true
    }

    log.info("[ActionSubmit] Stored action. Submitted actions now: {}", stateData.submittedActions.size)

    // Execute immediately if requested (e.g., Seer, Hunter Revenge)
    if (actionDef.isImmediate) {
        val executionResult = roleActionExecutor.executeActionInstance(this, action)

        // Handle immediate deaths if any (e.g. Hunter revenge)
        for ((cause, deaths) in executionResult.deaths) {
            for (userId in deaths) {
                val player = this.getPlayer(userId) ?: continue
                // Handle immediate deaths if any (e.g. Hunter revenge)
                @OptIn(DelicateCoroutinesApi::class)
                GlobalScope.launch {
                    try {
                        player.processDeath(cause, false)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        // For death triggers, we need to finalize the actor's death status in Discord
        if (actionDef.timing == ActionTiming.DEATH_TRIGGER) {
            // Explicitly consume to ensure discordDeath() proceeds
            stateData.playerOwnedActions[actorPlayerId]?.remove(actionDefinitionId.toString())
            actor.discordDeath()
        }

        // Mark as PROCESSED so it's not processed again in batch resolution
        action.status = ActionStatus.PROCESSED

        // Record to history immediately if it's a death trigger or we're past night resolution
        val currentDay = day
        if (actionDef.timing == ActionTiming.DEATH_TRIGGER || currentState != "NIGHT_PHASE") {
            val history = stateData.executedActions.getOrPut(currentDay) { mutableListOf() }
            if (!history.contains(action)) {
                history.add(action)
            }
        }
    }

    // Call submission hook
    actionDef.onSubmitted(this, actorPlayerId, targetPlayerIds)

    // Update UI status using our extension method
    if (action.status != ActionStatus.PROCESSED) {
        action.status = if (targetPlayerIds.contains(SKIP_TARGET_ID)) ActionStatus.SKIPPED else ActionStatus.SUBMITTED
        action.targets.clear()
        action.targets.addAll(targetPlayerIds)
    }

    // Notify NightManager of activity
    WerewolfApplication.nightManager.notifyPhaseUpdate(guildId)

    // Notify ActionProcessed event for callback-based step advancement
    WerewolfApplication.roleEventService.notifyListeners(
        this, RoleEventType.ACTION_PROCESSED, mapOf(
            "playerId" to actorPlayerId,
            "actionId" to actionDefinitionId,
            "submittedBy" to submittedBy
        )
    )

    return mapOf("success" to true, "message" to "Action submitted")
}
