package dev.robothanzo.werewolf.game.model

/**
 * Static information about a role action for UI and persistence
 */
data class ActionInfo(
    val actionId: String,
    val actionName: String,
    val roleName: String,
    val timing: ActionTiming
)

/**
 * Status of a player's action during a phase
 */
enum class ActionStatus {
    PENDING,
    ACTING,
    SUBMITTED,
    SKIPPED
}

/**
 * Unified data class for tracking player actions, prompts, and status.
 * Replaces ActionUsage, ActionPrompt, and ActionStatus (data class).
 */
data class ActionData(
    val playerId: Int,
    var role: String = "未知",
    val usage: MutableMap<String, Int> = mutableMapOf(), // actionId -> total count used
    var status: ActionStatus = ActionStatus.PENDING,
    var submittedAt: Long? = null,
    var availableActions: List<ActionInfo> = emptyList(),
    var selectedAction: ActionInfo? = null,
    var selectedTargets: List<Int> = emptyList(),
    var expiresAt: Long? = null,
    var targetPromptId: Long? = null // Discord message ID for target selection
)

/**
 * Group action state for coordinated actions like wolf kills
 */
data class GroupActionState(
    val actionId: String,
    val participants: List<Int>, // Player IDs of players voting
    val votes: MutableList<GroupVote> = mutableListOf(),
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + 90000, // 90 second timer
    val messageId: Long? = null, // Discord message ID for the group channel
    val finished: Boolean = false
)

data class GroupVote(
    val playerId: Int,
    val targetPlayerId: Int
)

const val SKIP_TARGET_ID = -1
