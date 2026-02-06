package dev.robothanzo.werewolf.game.model

/**
 * Represents a message sent in the werewolf chat during the night phase.
 */
data class WerewolfMessage(
    val senderId: Int,
    val senderName: String,
    val avatarUrl: String? = null,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Represents the action submission state for a player with an eligible role.
 */
data class ActionSubmissionStatus(
    val playerId: Int,
    val role: String,
    val status: ActionStatus, // PENDING, ACTING, SUBMITTED, SKIPPED
    val actionType: String? = null, // The action chosen (e.g., "KILL", "HEAL", "CHECK")
    val targetId: String? = null,
    val submittedAt: Long? = null
)

/**
 * Complete night phase status for real-time spectator/judge viewing.
 */
data class NightStatus(
    val day: Int,
    val phaseType: String, // "WEREWOLF_VOTING" or "ROLE_ACTIONS"
    val startTime: Long,
    val endTime: Long,

    // Werewolf voting data (shown in first screen)
    val werewolfMessages: List<WerewolfMessage> = emptyList(),
    val werewolfVotes: List<GroupVote> = emptyList(),

    // Role action statuses (shown in second screen)
    val actionStatuses: List<ActionSubmissionStatus> = emptyList()
)
