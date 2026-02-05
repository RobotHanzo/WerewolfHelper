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
 * Represents an active action prompt sent to a player
 * Tracks UI state, voting, and timers
 */
data class ActionPrompt(
    val playerId: String,
    val userId: Long,
    val actions: List<ActionInfo>,
    val selectedAction: ActionInfo? = null,
    val selectedTargets: List<Long> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + 90000, // 90 second default timer
    var targetPromptId: Long? = null, // Discord message ID
    val isGroupAction: Boolean = false, // For wolf voting
    val groupVotes: List<GroupVote> = emptyList(), // userId -> targetId for wolves
    val finalTarget: Long? = null // Final selected target after voting
)

data class GroupVote(
    val userId: Long,
    val targetId: Long
)

/**
 * Group action state for coordinated actions like wolf kills
 */
data class GroupActionState(
    val actionId: String,
    val participants: List<Long>, // User IDs of players voting
    val votes: MutableList<GroupVote> = mutableListOf(),
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + 90000, // 90 second timer
    val messageId: Long? = null, // Discord message ID for the group channel
    val finished: Boolean = false
)

const val SKIP_TARGET_ID = -1L