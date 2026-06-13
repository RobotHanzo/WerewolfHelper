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

    // --- provisioning (long-running; driven through the bulk engine) ---
    suspend fun provisionGuild(session: GameSession)
    suspend fun resizeGuild(session: GameSession, newCount: Int)
    suspend fun deleteGuild(guildId: Long)

    // --- per-member mutations (each isolated for the bulk engine) ---
    suspend fun grantSeatRole(guildId: Long, memberId: Long, seatNumber: Int)
    suspend fun setNickname(guildId: Long, memberId: Long, nickname: String)
    suspend fun grantSpectatorRole(guildId: Long, memberId: Long)
    suspend fun resetMember(guildId: Long, memberId: Long)

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
}
