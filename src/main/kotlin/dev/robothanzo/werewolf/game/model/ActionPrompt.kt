package dev.robothanzo.werewolf.game.model

import dev.robothanzo.werewolf.game.roles.actions.RoleAction

/**
 * Represents an active action prompt sent to a player
 * Tracks UI state, voting, and timers
 */
data class ActionPrompt(
    val playerId: String,
    val userId: Long,
    val actions: List<RoleAction>,
    val selectedAction: RoleAction? = null,
    val selectedTargets: List<Long> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + 90000, // 90 second default timer
    val messageId: Long? = null, // Discord message ID
    val isGroupAction: Boolean = false, // For wolf voting
    val groupVotes: Map<Long, Long> = emptyMap(), // userId -> targetId for wolves
    val finalTarget: Long? = null // Final selected target after voting
)

/**
 * Group action state for coordinated actions like wolf kills
 */
data class GroupActionState(
    val actionId: String,
    val participants: List<Long>, // User IDs of players voting
    val votes: MutableMap<Long, Long> = mutableMapOf(), // userId -> targetId
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + 90000, // 90 second timer
    val messageId: Long? = null, // Discord message ID for the group channel
    val finished: Boolean = false
)

const val SKIP_TARGET_ID = -1L