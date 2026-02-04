package dev.robothanzo.werewolf.service

import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.ActionPrompt
import dev.robothanzo.werewolf.game.model.GroupActionState
import dev.robothanzo.werewolf.game.roles.actions.RoleAction

interface ActionUIService {
    /**
     * Send action selection UI to a player via Discord DM
     * Shows available actions and prompts to select and target
     * Default timeout: 60 seconds
     */
    fun promptPlayerForAction(
        guildId: Long,
        session: Session,
        userId: Long,
        playerId: String,
        availableActions: List<RoleAction>,
        timeoutSeconds: Int = 60
    ): ActionPrompt?

    /**
     * Send group action UI for wolves to discuss and vote on target
     * Displays in guild text channel or dedicated voice channel
     * Default timeout: 90 seconds for group discussion
     */
    fun promptGroupForAction(
        guildId: Long,
        session: Session,
        actionId: String,
        participants: List<Long>,
        durationSeconds: Int = 90
    ): GroupActionState?

    /**
     * Update an action prompt with selected action
     */
    fun updateActionSelection(
        guildId: Long,
        promptId: String,
        actionId: String,
        session: Session
    ): ActionPrompt?

    /**
     * Record a target selection for an action prompt
     */
    fun submitTargetSelection(
        guildId: Long,
        promptId: String,
        userId: Long,
        targetUserId: Long,
        session: Session
    ): Boolean

    /**
     * Get an active prompt by id
     */
    fun getPrompt(promptId: String): ActionPrompt?

    /**
     * Record a vote in a group action
     */
    fun submitGroupVote(
        guildId: Long,
        groupStateId: String,
        userId: Long,
        targetUserId: Long,
        session: Session
    ): Boolean

    /**
     * Get the final target for a group action (majority vote or last vote)
     */
    fun resolveGroupVote(groupState: GroupActionState): Long?

    /**
     * Get a group action state by action ID
     */
    fun getGroupState(actionId: String): GroupActionState?

    /**
     * Clear a group action state after resolution
     */
    fun clearGroupState(actionId: String)

    /**
     * Clean up expired action prompts and group states
     * Sends timeout notification to players whose prompts have expired
     */
    fun cleanupExpiredPrompts(guildId: Long, session: Session? = null)

    /**
     * Send 30-second reminders to players about upcoming timeout
     */
    fun sendReminders(guildId: Long, session: Session)

    /**
     * Clear a prompt (when player submits action or skips)
     * This prevents reminder from being sent
     */
    fun clearPrompt(playerId: String)
}
