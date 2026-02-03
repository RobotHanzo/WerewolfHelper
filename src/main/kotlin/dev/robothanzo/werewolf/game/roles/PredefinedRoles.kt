package dev.robothanzo.werewolf.game.roles

import dev.robothanzo.werewolf.game.model.*

object PredefinedRoles {

    // Priority constants
    const val WEREWOLF_PRIORITY = 100
    const val WITCH_ANTIDOTE_PRIORITY = 200
    const val WITCH_POISON_PRIORITY = 210
    const val SEER_PRIORITY = 300
    const val GUARD_PRIORITY = 150
    const val HUNTER_PRIORITY = 250
    const val POLICE_PRIORITY = 400

    // Action IDs
    const val WEREWOLF_KILL = "WEREWOLF_KILL"
    const val WITCH_ANTIDOTE = "WITCH_ANTIDOTE"
    const val WITCH_POISON = "WITCH_POISON"
    const val SEER_CHECK = "SEER_CHECK"
    const val GUARD_PROTECT = "GUARD_PROTECT"
    const val HUNTER_REVENGE = "HUNTER_REVENGE"

    private val actionRegistry = mapOf(
        // Werewolf
        WEREWOLF_KILL to RoleActionDefinition(
            actionId = WEREWOLF_KILL,
            roleName = "狼人",
            priority = WEREWOLF_PRIORITY,
            timing = ActionTiming.NIGHT,
            targetCount = 1,
            usageLimit = -1, // Unlimited
            requiresAliveTarget = true
        ),

        // Witch - Antidote (Save)
        WITCH_ANTIDOTE to RoleActionDefinition(
            actionId = WITCH_ANTIDOTE,
            roleName = "女巫",
            priority = WITCH_ANTIDOTE_PRIORITY,
            timing = ActionTiming.NIGHT,
            targetCount = 1,
            usageLimit = 1,
            requiresAliveTarget = false // Can save someone already dead
        ),

        // Witch - Poison
        WITCH_POISON to RoleActionDefinition(
            actionId = WITCH_POISON,
            roleName = "女巫",
            priority = WITCH_POISON_PRIORITY,
            timing = ActionTiming.NIGHT,
            targetCount = 1,
            usageLimit = 1,
            requiresAliveTarget = true
        ),

        // Seer - Check
        SEER_CHECK to RoleActionDefinition(
            actionId = SEER_CHECK,
            roleName = "預言家",
            priority = SEER_PRIORITY,
            timing = ActionTiming.NIGHT,
            targetCount = 1,
            usageLimit = -1, // Unlimited
            requiresAliveTarget = true
        ),

        // Guard - Protect
        GUARD_PROTECT to RoleActionDefinition(
            actionId = GUARD_PROTECT,
            roleName = "守衛",
            priority = GUARD_PRIORITY,
            timing = ActionTiming.NIGHT,
            targetCount = 1,
            usageLimit = -1, // Unlimited
            requiresAliveTarget = true
        ),

        // Hunter - Revenge (triggered on death, not directly submitted)
        HUNTER_REVENGE to RoleActionDefinition(
            actionId = HUNTER_REVENGE,
            roleName = "獵人",
            priority = HUNTER_PRIORITY,
            timing = ActionTiming.DAY,
            targetCount = 1,
            usageLimit = 1,
            requiresAliveTarget = true
        )
    )

    private val roleDefinitions = mapOf(
        "狼人" to RoleDefinition("狼人", Camp.WEREWOLF),
        "狼王" to RoleDefinition("狼王", Camp.WEREWOLF),
        "狼美人" to RoleDefinition("狼美人", Camp.WEREWOLF),
        "白狼王" to RoleDefinition("白狼王", Camp.WEREWOLF),
        "夢魘" to RoleDefinition("夢魘", Camp.WEREWOLF),
        "石像鬼" to RoleDefinition("石像鬼", Camp.WEREWOLF),
        "機械狼" to RoleDefinition("機械狼", Camp.WEREWOLF),

        "女巫" to RoleDefinition("女巫", Camp.GOD),
        "獵人" to RoleDefinition("獵人", Camp.GOD, listOf(RoleEventType.ON_DEATH)),
        "預言家" to RoleDefinition("預言家", Camp.GOD),
        "守衛" to RoleDefinition("守衛", Camp.GOD),
        "騎士" to RoleDefinition("騎士", Camp.GOD),
        "守墓人" to RoleDefinition("守墓人", Camp.GOD),
        "魔術師" to RoleDefinition("魔術師", Camp.GOD),
        "黑市商人" to RoleDefinition("黑市商人", Camp.GOD),
        "邱比特" to RoleDefinition("邱比特", Camp.GOD),
        "盜賊" to RoleDefinition("盜賊", Camp.GOD),
        "血月使者" to RoleDefinition("血月使者", Camp.GOD),
        "惡靈騎士" to RoleDefinition("惡靈騎士", Camp.GOD),
        "通靈師" to RoleDefinition("通靈師", Camp.GOD),
        "獵魔人" to RoleDefinition("獵魔人", Camp.GOD),

        "混血兒" to RoleDefinition("混血兒", Camp.VILLAGER),
        "白癡" to RoleDefinition("白癡", Camp.VILLAGER),
        "複製人" to RoleDefinition("複製人", Camp.VILLAGER),
        "狼兄" to RoleDefinition("狼兄", Camp.VILLAGER),
        "狼弟" to RoleDefinition("狼弟", Camp.VILLAGER),

        "平民" to RoleDefinition("平民", Camp.VILLAGER)
    )

    fun getActionDefinition(actionId: String): RoleActionDefinition? = actionRegistry[actionId]

    fun getRoleDefinition(roleName: String): RoleDefinition? = roleDefinitions[roleName]

    fun getRoleActions(roleName: String): List<RoleActionDefinition> {
        return actionRegistry.values.filter { it.roleName == roleName }
    }

    fun getAllPredefinedActions(): List<RoleActionDefinition> = actionRegistry.values.toList()

    fun getPredefinedActionsByTiming(timing: ActionTiming): List<RoleActionDefinition> {
        return actionRegistry.values.filter { it.timing == timing }
    }

    fun sortActionsByPriority(
        actions: List<RoleActionInstance>,
        customRolePriorities: Map<String, Int> = emptyMap()
    ): List<RoleActionInstance> {
        return actions.sortedBy { action ->
            val definition = getActionDefinition(action.actionDefinitionId) ?: return@sortedBy Int.MAX_VALUE
            customRolePriorities[definition.roleName] ?: definition.priority
        }
    }
}
