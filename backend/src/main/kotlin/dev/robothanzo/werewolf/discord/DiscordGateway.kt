package dev.robothanzo.werewolf.discord

import dev.robothanzo.werewolf.domain.GameSession

/** A guild member as the dashboard pickers and assignment need it. */
data class GuildMember(
    val id: Long,
    val name: String,
    val displayName: String,
    val avatarUrl: String?,
    val bot: Boolean = false,
    val owner: Boolean = false,
)

/** Which shared channel a message targets. */
enum class ChannelKind { COURT, JUDGE, SPECTATOR }

/**
 * Every Discord/JDA interaction goes through this seam so the rest of the app is testable and so the
 * bot layer **degrades gracefully** when no token is configured (the [NoOpDiscordGateway] is used and
 * the REST API + WebSocket hub still serve fully).
 *
 * Mutating calls return success/failure rather than throwing, so the [dev.robothanzo.werewolf.ops]
 * bulk engine can isolate per-item faults.
 */
interface DiscordGateway {

    /** Whether a real Discord connection backs this gateway. */
    val available: Boolean

    // --- queries ---
    fun listMembers(guildId: Long): List<GuildMember>
    fun isOwner(guildId: Long, memberId: Long): Boolean
    /** Role-hierarchy preflight: can the bot actually act on this member? */
    fun canInteract(guildId: Long, memberId: Long): Boolean
    fun getGuildName(guildId: Long): String?
    fun getGuildIconUrl(guildId: Long): String?

    // --- provisioning (long-running; driven through the bulk engine) ---
    suspend fun provisionGuild(session: GameSession)
    suspend fun resizeGuild(session: GameSession, newCount: Int)
    suspend fun deleteGuild(guildId: Long)

    // --- per-member mutations (non-blocking via the library's queue; isolated for the bulk engine) ---
    fun grantSeatRole(guildId: Long, memberId: Long, seatNumber: Int)
    fun setNickname(guildId: Long, memberId: Long, nickname: String)
    fun grantSpectatorRole(guildId: Long, memberId: Long)
    fun resetMember(guildId: Long, memberId: Long)

    // --- messaging ---
    fun sendChannelMessage(guildId: Long, channel: ChannelKind, text: String)
    fun sendSeatMessage(guildId: Long, seatNumber: Int, text: String)

    // --- voice ---
    fun muteAll(guildId: Long)
    fun unmuteAll(guildId: Long)
    fun muteMember(guildId: Long, memberId: Long, muted: Boolean)
    fun playSound(guildId: Long, cue: SoundCue)

    // --- wolf-chat relay (one cached webhook per channel) ---
    fun relayWolfChat(guildId: Long, fromSeat: Int, authorName: String, authorAvatar: String?, content: String)

    // --- night interactions (button / select-menu prompts) ---
    /** Register the handler that routes component interactions into the engine. */
    fun setInteractionHandler(handler: DiscordInteractionHandler)

    /** Register the handler for the `/server` slash command and bot-join provisioning. */
    fun setCommandHandler(handler: DiscordCommandHandler)

    /** Grant a member the provisioned judge role (re-granted to the owner on join, §3). */
    fun grantJudgeRole(guildId: Long, memberId: Long)

    /** Revoke a member's provisioned judge role. */
    fun revokeJudgeRole(guildId: Long, memberId: Long)

    /**
     * Post a single-select night-action prompt in [seatNumber]'s private channel. [customId] carries
     * the ability; [options] are the targetable seats; [extraValues] are non-seat choices (e.g. the
     * witch's "save"); a Skip choice is added when [allowSkip].
     */
    fun promptNightAction(
        guildId: Long,
        seatNumber: Int,
        customId: String,
        prompt: String,
        options: List<SeatOption>,
        extraValues: List<Pair<String, String>> = emptyList(),
        allowSkip: Boolean = true,
        maxValues: Int = 1,
    )

    /** Post wolf-kill vote buttons (one per option, plus skip) into each wolf participant's channel. */
    fun promptWolfVote(guildId: Long, voterSeats: List<Int>, options: List<SeatOption>)
}
