package dev.robothanzo.werewolf.game.roles

import dev.robothanzo.werewolf.game.model.*
import dev.robothanzo.werewolf.game.roles.actions.*

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
    const val WOLF_KING_REVENGE = "WOLF_KING_REVENGE"

    // Special death causes
    const val DOUBLE_PROTECTION = "DOUBLE_PROTECTION"

    // Action instances
    private val werewolfKillAction = WerewolfKillAction()
    private val witchAntidoteAction = WitchAntidoteAction()
    private val witchPoisonAction = WitchPoisonAction()
    private val seerCheckAction = SeerCheckAction()
    private val guardProtectAction = GuardProtectAction()
    private val hunterRevengeAction = HunterRevengeAction()
    private val wolfKingRevengeAction = WolfKingRevengeAction()

    private val roleDefinitions = mapOf(
        "狼人" to RoleDefinition(
            name = "狼人",
            camp = Camp.WEREWOLF,
            actions = listOf(werewolfKillAction)
        ),
        "狼王" to RoleDefinition(
            name = "狼王",
            camp = Camp.WEREWOLF,
            eventListeners = listOf(RoleEventType.ON_DEATH),
            actions = listOf(wolfKingRevengeAction)
        ),
        "狼美人" to RoleDefinition("狼美人", Camp.WEREWOLF),
        "白狼王" to RoleDefinition("白狼王", Camp.WEREWOLF),
        "夢魘" to RoleDefinition("夢魘", Camp.WEREWOLF),
        "石像鬼" to RoleDefinition("石像鬼", Camp.WEREWOLF),
        "機械狼" to RoleDefinition("機械狼", Camp.WEREWOLF),

        "女巫" to RoleDefinition(
            name = "女巫",
            camp = Camp.GOD,
            actions = listOf(witchAntidoteAction, witchPoisonAction)
        ),
        "獵人" to RoleDefinition(
            name = "獵人",
            camp = Camp.GOD,
            eventListeners = listOf(RoleEventType.ON_DEATH),
            actions = listOf(hunterRevengeAction)
        ),
        "預言家" to RoleDefinition(
            name = "預言家",
            camp = Camp.GOD,
            actions = listOf(seerCheckAction)
        ),
        "守衛" to RoleDefinition(
            name = "守衛",
            camp = Camp.GOD,
            actions = listOf(guardProtectAction)
        ),
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

    fun getAction(actionId: String): RoleAction? {
        return roleDefinitions.values.flatMap { it.actions }.find { it.actionId == actionId }
    }

    fun getRoleDefinition(roleName: String): RoleDefinition? = roleDefinitions[roleName]

    fun getRoleActions(roleName: String): List<RoleAction> {
        return roleDefinitions[roleName]?.actions ?: emptyList()
    }

    fun getAllPredefinedActions(): List<RoleAction> {
        return roleDefinitions.values.flatMap { it.actions }
    }

    fun getPredefinedActionsByTiming(timing: ActionTiming): List<RoleAction> {
        return getAllPredefinedActions().filter { it.timing == timing }
    }

    fun sortActionsByPriority(
        actions: List<RoleActionInstance>,
        customRolePriorities: Map<String, Int> = emptyMap()
    ): List<RoleActionInstance> {
        return actions.sortedBy { action ->
            val roleAction = getAction(action.actionDefinitionId) ?: return@sortedBy Int.MAX_VALUE
            customRolePriorities[roleAction.roleName] ?: roleAction.priority
        }
    }

    // Backward compatibility: convert RoleAction to RoleActionDefinition
    @Deprecated("Use RoleAction directly instead", ReplaceWith("action"))
    fun getActionDefinition(actionId: String): RoleActionDefinition? {
        val action = getAction(actionId) ?: return null
        return RoleActionDefinition(
            actionId = action.actionId,
            roleName = action.roleName,
            priority = action.priority,
            timing = action.timing,
            targetCount = action.targetCount,
            usageLimit = action.usageLimit,
            requiresAliveTarget = action.requiresAliveTarget
        )
    }
}
