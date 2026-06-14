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
    val spectator: Boolean = false,
)

/** Which shared channel a message targets. */
enum class ChannelKind { COURT, JUDGE, SPECTATOR }

/** A single embed field: [name] is the bold heading, [value] the body, [inline] packs it side-by-side. */
data class EmbedField(val name: String, val value: String, val inline: Boolean = false)

/**
 * A minimal rich-embed spec (keeps JDA out of the seam). [color] is packed RGB, null → default.
 * [description] may be blank when the content is carried entirely by [fields].
 */
data class EmbedSpec(
    val title: String,
    val description: String = "",
    val color: Int? = null,
    val fields: List<EmbedField> = emptyList(),
)

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
    /** Whether [memberId] is a judge of this guild (the owner, the judge role, or a server admin) —
     *  judges may drive night prompts on a seat's behalf from that seat's channel. */
    fun isJudge(guildId: Long, memberId: Long): Boolean
    /** Role-hierarchy preflight: can the bot actually act on this member? */
    fun canInteract(guildId: Long, memberId: Long): Boolean
    fun getGuildName(guildId: Long): String?
    fun getGuildIconUrl(guildId: Long): String?

    // --- provisioning (long-running; driven through the bulk engine) ---
    suspend fun provisionGuild(session: GameSession)
    suspend fun resizeGuild(session: GameSession, newCount: Int)
    suspend fun deleteGuild(guildId: Long)

    // --- per-member mutations (isolated for the bulk engine) ---
    // `await = true` blocks until Discord confirms, so the assignment bulk phase can apply role then
    // nickname strictly one player at a time (FEATURES §10.2); the default fire-and-forget `queue()`
    // keeps the live single-seat nickname syncs off the request thread.
    fun grantSeatRole(guildId: Long, memberId: Long, seatNumber: Int, await: Boolean = false)
    fun setNickname(guildId: Long, memberId: Long, nickname: String, await: Boolean = false)
    fun grantSpectatorRole(guildId: Long, memberId: Long)
    /** Reset side-effects, split so the bulk engine reports them as distinct progress steps. */
    fun removeSeatRoles(guildId: Long, memberId: Long)
    fun clearNickname(guildId: Long, memberId: Long)

    // --- messaging ---
    fun sendChannelMessage(guildId: Long, channel: ChannelKind, text: String)
    fun sendChannelEmbed(guildId: Long, channel: ChannelKind, embed: EmbedSpec)
    fun sendSeatMessage(guildId: Long, seatNumber: Int, text: String)
    fun sendSeatEmbed(guildId: Long, seatNumber: Int, embed: EmbedSpec)

    /**
     * Post an interactive prompt with [buttons] into the COURT channel. Covers every day-side flow
     * (speech 跳過/下台, police enroll/withdraw/vote, expel vote, direction choice). Custom ids are
     * namespaced `wh:day:...` and routed back through the interaction handler.
     */
    fun sendCourtButtons(guildId: Long, text: String, buttons: List<CourtButton>)

    /**
     * Open every channel in the guild for viewing by everyone (grants `VIEW_CHANNEL` to @everyone on
     * each channel without lifting the send-message restrictions). Used when the judge confirms the
     * win banner so spectators/players can read all the seat channels post-game.
     */
    fun revealAllChannels(guildId: Long)

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

    /** Register the handler that records relayed wolf-chat lines onto the live night (judge board). */
    fun setWolfChatHandler(handler: WolfChatHandler)

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

    /**
     * Post the witch's potion choice into [seatNumber]'s channel: a 解藥 button, a 毒藥 button, and a
     * skip. Each button opens its own target select menu (via [promptNightAction]); the choice stays
     * editable until a target is committed. The two-step split is what lets the witch flip between
     * potions before locking in a target.
     */
    fun promptWitchChoice(guildId: Long, seatNumber: Int, prompt: String)
}
