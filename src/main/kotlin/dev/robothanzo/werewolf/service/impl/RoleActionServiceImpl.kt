package dev.robothanzo.werewolf.service.impl

import dev.robothanzo.werewolf.database.SessionRepository
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.*
import dev.robothanzo.werewolf.game.roles.PredefinedRoles
import dev.robothanzo.werewolf.game.roles.actions.RoleAction
import dev.robothanzo.werewolf.game.roles.actions.RoleActionExecutor
import dev.robothanzo.werewolf.service.DiscordService
import dev.robothanzo.werewolf.service.NightResolutionResult
import dev.robothanzo.werewolf.service.RoleActionService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class RoleActionServiceImpl(
    private val sessionRepository: SessionRepository,
    private val discordService: DiscordService,
    private val roleActionExecutor: RoleActionExecutor
) : RoleActionService {
    private val log = LoggerFactory.getLogger(RoleActionServiceImpl::class.java)

    override fun submitAction(
        guildId: Long,
        actionDefinitionId: String,
        actorUserId: Long,
        targetUserIds: List<Long>,
        submittedBy: String
    ): Map<String, Any> {
        val session = sessionRepository.findByGuildId(guildId)
            .orElseThrow { IllegalArgumentException("Session not found for guild $guildId") }

        // Validate action exists
        val actionDef = PredefinedRoles.getActionDefinition(actionDefinitionId)
            ?: getCustomActionDefinition(session, actionDefinitionId)
            ?: return mapOf("success" to false, "error" to "Action not found")

        // Validate actor exists and is alive
        val actor = session.players.values.find { it.userId == actorUserId }
        if (actor == null || !actor.isAlive) {
            return mapOf("success" to false, "error" to "Actor not alive")
        }

        // Prevent multiple actions per phase (for player submissions only)
        if (submittedBy == "PLAYER" && actor.actionSubmitted) {
            return mapOf("success" to false, "error" to "You have already submitted an action this phase")
        }

        // Validate actor has the role
        if (actor.roles?.contains(actionDef.roleName) != true) {
            return mapOf("success" to false, "error" to "Actor does not have role ${actionDef.roleName}")
        }

        // Check usage limit
        if (!isActionAvailable(session, actorUserId, actionDefinitionId)) {
            return mapOf("success" to false, "error" to "Action usage limit reached")
        }

        // Validate targets
        if (targetUserIds.size != actionDef.targetCount && actionDef.targetCount > 0) {
            return mapOf("success" to false, "error" to "Invalid target count")
        }

        // Validate targets are valid
        for (targetId in targetUserIds) {
            val target = session.players.values.find { it.userId == targetId }
            if (target == null) {
                return mapOf("success" to false, "error" to "Target player not found")
            }
            if (actionDef.requiresAliveTarget && !target.isAlive) {
                return mapOf("success" to false, "error" to "Target must be alive")
            }
        }

        // Execute seer check immediately and post result to player's channel
        if (actionDefinitionId == PredefinedRoles.SEER_CHECK && targetUserIds.isNotEmpty()) {
            val targetId = targetUserIds[0]
            val target = session.players.values.find { it.userId == targetId }
            val isWolf = target?.roles?.any { role ->
                PredefinedRoles.getRoleDefinition(role)?.camp == Camp.WEREWOLF
            } == true

            try {
                val targetName = target?.nickname ?: "玩家 $targetId"
                val resultText = if (isWolf) "狼人" else "好人"
                // Send result via player's channel send helper
                actor.send("🔮 查驗結果：$targetName 是 $resultText")
            } catch (_: Exception) {
                // Ignore DM failures to avoid blocking action submission
            }
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
        @Suppress("UNCHECKED_CAST")
        val pendingActions =
            (session.stateData.getOrPut("pendingActions") { mutableListOf<Map<String, Any>>() } as MutableList<Map<String, Any>>)
        pendingActions.add(action.toMap())

        // Mark action as submitted (for players only, not judges)
        if (submittedBy == "PLAYER") {
            actor.actionSubmitted = true
        }

        log.info("[ActionSubmit] Stored action for guild {}. Pending actions now: {}", guildId, pendingActions.size)

        // Update usage count
        updateActionUsage(session, actorUserId, actionDefinitionId)

        sessionRepository.save(session)

        return mapOf("success" to true, "message" to "Action submitted")
    }

    override fun getAvailableActionsForPlayer(session: Session, userId: Long): List<RoleAction> {
        val player = session.players.values.find { it.userId == userId }
            ?: return emptyList()

        if (!player.isAlive) {
            return emptyList()
        }

        val actions = mutableListOf<RoleAction>()

        // Get predefined actions
        for (role in player.roles ?: emptyList()) {
            val roleActions = PredefinedRoles.getRoleActions(role)
            for (action in roleActions) {
                if (isActionAvailable(session, userId, action.actionId)) {
                    actions.add(action)
                }
            }
        }

        // Get custom actions
        @Suppress("UNCHECKED_CAST")
        val customRoles =
            (session.stateData.getOrDefault("customRoles", mapOf<String, Any>()) as? Map<String, Any>) ?: emptyMap()
        for (role in player.roles ?: emptyList()) {
            val customRole = customRoles[role]
            if (customRole is Map<*, *>) {
                @Suppress("UNCHECKED_CAST")
                val customActions = (customRole as Map<String, Any>).getOrDefault(
                    "actions",
                    emptyList<Map<String, Any>>()
                ) as? List<Map<String, Any>> ?: emptyList()
                for (actionMap in customActions) {
                    val actionId = actionMap["actionId"] as? String ?: continue
                    if (isActionAvailable(session, userId, actionId)) {
                        // Convert map to custom action definition
                        val actionDef = mapToActionDefinition(actionMap, role)
                        if (actionDef != null) {
                            // TODO: Create custom RoleAction implementation from actionDef
                            // For now, skip custom actions as they need to be refactored to RoleAction
                        }
                    }
                }
            }
        }

        return actions
    }

    override fun getAvailableActionsForJudge(session: Session): Map<Long, List<RoleAction>> {
        val result = mutableMapOf<Long, List<RoleAction>>()
        for ((_, player) in session.players) {
            if (player.isAlive) {
                player.userId?.let { uid ->
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
        @Suppress("UNCHECKED_CAST")
        val pendingActionsData =
            (session.stateData.getOrDefault("pendingActions", emptyList<Map<String, Any>>()) as? List<Map<String, Any>>)
                ?: emptyList()

        val pendingActions = pendingActionsData.map { mapToActionInstance(it) }

        // Execute all actions using the new action executor
        val executionResult = roleActionExecutor.executeActions(session, pendingActions)

        // Extract seer checks from metadata
        @Suppress("UNCHECKED_CAST")
        val seerChecks = (executionResult.metadata["seerChecks"] as? Map<Long, String>) ?: emptyMap()

        return NightResolutionResult(
            deaths = executionResult.deaths,
            saved = executionResult.saved,
            checked = seerChecks
        )
    }

    override fun isActionAvailable(session: Session, userId: Long, actionDefinitionId: String): Boolean {
        val action = PredefinedRoles.getAction(actionDefinitionId)
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
        @Suppress("UNCHECKED_CAST")
        val pendingActionsData =
            (session.stateData.getOrDefault("pendingActions", emptyList<Map<String, Any>>()) as? List<Map<String, Any>>)
                ?: emptyList()
        return pendingActionsData.map { mapToActionInstance(it) }
    }

    override fun getActionUsageCount(session: Session, userId: Long, actionDefinitionId: String): Int {
        @Suppress("UNCHECKED_CAST")
        val actionUsage =
            (session.stateData.getOrPut("actionUsage") { mutableMapOf<String, Any>() } as? MutableMap<String, Any>)
                ?: return 0

        @Suppress("UNCHECKED_CAST")
        val userUsage =
            (actionUsage.getOrPut(userId.toString()) { mutableMapOf<String, Int>() } as? Map<String, Int>) ?: emptyMap()

        return userUsage.getOrDefault(actionDefinitionId, 0)
    }

    private fun updateActionUsage(session: Session, userId: Long, actionDefinitionId: String) {
        @Suppress("UNCHECKED_CAST")
        val actionUsage =
            (session.stateData.getOrPut("actionUsage") { mutableMapOf<String, Any>() } as MutableMap<String, Any>)

        @Suppress("UNCHECKED_CAST")
        val userUsage =
            (actionUsage.getOrPut(userId.toString()) { mutableMapOf<String, Int>() } as MutableMap<String, Int>)

        val currentCount = userUsage.getOrDefault(actionDefinitionId, 0)
        userUsage[actionDefinitionId] = currentCount + 1
    }

    private fun mapToActionInstance(map: Map<String, Any>): RoleActionInstance {
        return RoleActionInstance(
            actor = (map["actor"] as? Number)?.toLong() ?: 0L,
            actionDefinitionId = map["actionDefinitionId"] as? String ?: "",
            targets = @Suppress("UNCHECKED_CAST") (map["targets"] as? List<Number>)?.map { it.toLong() } ?: emptyList(),
            submittedBy = if (map["submittedBy"] as? String == "JUDGE") ActionSubmissionSource.JUDGE else ActionSubmissionSource.PLAYER,
            timestamp = (map["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
        )
    }

    private fun RoleActionInstance.toMap(): Map<String, Any> {
        return mapOf(
            "actor" to actor,
            "actionDefinitionId" to actionDefinitionId,
            "targets" to targets,
            "submittedBy" to submittedBy.name,
            "timestamp" to timestamp
        )
    }

    private fun getCustomActionDefinition(session: Session, actionId: String): RoleActionDefinition? {
        @Suppress("UNCHECKED_CAST")
        val customRoles =
            (session.stateData.getOrDefault("customRoles", emptyMap<String, Any>()) as? Map<String, Any>) ?: emptyMap()

        for ((roleName, roleConfig) in customRoles) {
            if (roleConfig is Map<*, *>) {
                @Suppress("UNCHECKED_CAST")
                val actions = (roleConfig as Map<String, Any>).getOrDefault(
                    "actions",
                    emptyList<Map<String, Any>>()
                ) as? List<Map<String, Any>> ?: emptyList()
                for (actionMap in actions) {
                    if (actionMap["actionId"] as? String == actionId) {
                        return mapToActionDefinition(actionMap, roleName)
                    }
                }
            }
        }
        return null
    }

    private fun mapToActionDefinition(map: Map<String, Any>, roleName: String): RoleActionDefinition? {
        val timing = when (map["timing"] as? String) {
            "NIGHT" -> ActionTiming.NIGHT
            "DAY" -> ActionTiming.DAY
            "ANYTIME" -> ActionTiming.ANYTIME
            else -> return null
        }

        return RoleActionDefinition(
            actionId = map["actionId"] as? String ?: return null,
            roleName = roleName,
            priority = (map["priority"] as? Number)?.toInt() ?: 500,
            timing = timing,
            targetCount = (map["targetCount"] as? Number)?.toInt() ?: 1,
            usageLimit = (map["usageLimit"] as? Number)?.toInt() ?: -1,
            requiresAliveTarget = map["requiresAliveTarget"] as? Boolean ?: true
        )
    }

    override fun executeDeathTriggers(session: Session): List<Long> {
        val killedByTriggers = mutableListOf<Long>()

        // Execute all pending death trigger actions
        val pendingActions = getPendingActions(session)
            .filter {
                val action = PredefinedRoles.getAction(it.actionDefinitionId)
                action?.timing == ActionTiming.DEATH_TRIGGER
            }

        if (pendingActions.isEmpty()) {
            return emptyList()
        }

        // Execute actions using RoleActionExecutor
        val executionResult = roleActionExecutor.executeActions(session, pendingActions)

        // Collect all deaths from death triggers
        for ((cause, deaths) in executionResult.deaths) {
            killedByTriggers.addAll(deaths)
        }

        // Clear pending death trigger actions
        val remainingActions = getPendingActions(session)
            .filter {
                val action = PredefinedRoles.getAction(it.actionDefinitionId)
                action?.timing != ActionTiming.DEATH_TRIGGER
            }

        session.stateData["pendingActions"] = remainingActions.map { it.toMap() }
        sessionRepository.save(session)

        return killedByTriggers
    }

    override fun hasDeathTriggerAvailable(session: Session, userId: Long): Boolean {
        // Get all death trigger actions from predefined roles
        val deathTriggerActions = PredefinedRoles.getAllPredefinedActions()
            .filter { it.timing == ActionTiming.DEATH_TRIGGER }

        // Check if any death trigger action is available for this user
        for (action in deathTriggerActions) {
            val stateKey = "${action.actionId}Available"
            val availableUserId = session.stateData[stateKey] as? Long
            if (availableUserId == userId) {
                return true
            }
        }

        return false
    }
}
