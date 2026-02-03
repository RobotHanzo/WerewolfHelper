package dev.robothanzo.werewolf.database.documents

enum class LogType {
    // Player Events
    PLAYER_DIED,
    PLAYER_REVIVED,
    ROLE_ASSIGNED,
    POLICE_TRANSFERRED,
    POLICE_DESTROYED,
    POLICE_FORCED,

    // Speech Events
    SPEECH_STARTED,
    SPEECH_ENDED,
    SPEAKER_CHANGED,
    SPEECH_SKIPPED,
    SPEECH_INTERRUPTED,
    SPEECH_ORDER_SET,

    // Poll Events - Police
    POLICE_ENROLLMENT_STARTED,
    POLICE_ENROLLED,
    POLICE_UNENROLLED,
    POLICE_VOTING_STARTED,
    POLICE_ELECTED,
    POLICE_BADGE_DESTROYED,

    // Poll Events - Expel
    EXPEL_POLL_STARTED,
    VOTE_CAST,
    VOTE_RESULT,
    PLAYER_EXPELLED,

    // System Events
    GAME_STARTED,
    GAME_ENDED,
    GAME_RESET,
    COMMAND_EXECUTED,

    // Judge Actions
    PLAYER_PROMOTED_JUDGE,
    PLAYER_DEMOTED;

    /**
     * Get the display category for UI grouping
     */
    fun getCategory(): String {
        val name = this.name
        if (name.startsWith("PLAYER_")) return "player"
        if (name.startsWith("SPEECH_")) return "speech"
        if (name.startsWith("POLICE_")) return "police"
        if (name.startsWith("EXPEL_") || name.startsWith("VOTE_")) return "vote"
        return "system"
    }

    /**
     * Get the severity level for UI styling
     */
    fun getSeverity(): String {
        return when (this) {
            PLAYER_DIED, PLAYER_EXPELLED, POLICE_BADGE_DESTROYED, SPEECH_INTERRUPTED -> "alert"
            POLICE_ELECTED, PLAYER_REVIVED, ROLE_ASSIGNED, SPEECH_STARTED, GAME_STARTED, GAME_RESET -> "action"
            else -> "info"
        }
    }
}
