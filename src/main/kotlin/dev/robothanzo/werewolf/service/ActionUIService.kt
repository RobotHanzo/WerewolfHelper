package dev.robothanzo.werewolf.service

import dev.robothanzo.werewolf.database.documents.Player
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.RoleActionInstance
import dev.robothanzo.werewolf.game.model.WolvesActionState
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
        playerId: Int,
        availableActions: List<RoleAction>,
        timeoutSeconds: Int = 60
    ): RoleActionInstance?

    /**
     * Send group action UI for wolves to discuss and vote on target
     * Displays in guild text channel or dedicated voice channel
     * Default timeout: 90 seconds for group discussion
     */
    fun promptGroupForAction(
        guildId: Long,
        session: Session,
        actionId: String,
        participants: List<Int>, // These are playerIds
        durationSeconds: Int = 90
    ): WolvesActionState?

    /**
     * Update an action prompt with selected action
     */
    fun updateActionSelection(
        guildId: Long,
        playerId: Int,
        actionId: String,
        session: Session
    ): RoleActionInstance?

    /**
     * Record a target selection for an action prompt
     */
    fun submitTargetSelection(
        guildId: Long,
        playerId: Int,
        targetPlayerId: Int,
        session: Session
    ): Boolean

    fun getActionData(session: Session, playerId: Int): RoleActionInstance?

    fun updateTargetPromptId(session: Session, playerId: Int, promptId: Long)

    /**
     * Record a vote in a group action
     */
    fun submitGroupVote(
        player: Player,
        groupStateId: String,
        targetPlayerId: Int
    ): Boolean

    /**
     * Get the final target for a group action (majority vote or last vote)
     */
    fun resolveGroupVote(session: Session, groupState: WolvesActionState): Int?

    /**
     * Get a group action state by action ID
     */
    fun getGroupState(session: Session, actionId: String): WolvesActionState?

    /**
     * Clean up expired action prompts and group states
     * Sends timeout notification to players whose prompts have expired
     */
    fun cleanupExpiredPrompts(guildId: Long, session: Session? = null)

    /**
     * Send 30-second reminders to players about upcoming timeout
     */
    fun sendReminders(guildId: Long, session: Session)

    fun clearPrompt(session: Session, playerId: Int)
}
