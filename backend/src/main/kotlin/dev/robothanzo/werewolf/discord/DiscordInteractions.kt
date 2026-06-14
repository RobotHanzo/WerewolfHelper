package dev.robothanzo.werewolf.discord

/** A selectable target in a night prompt (a seat + its display label). */
data class SeatOption(val seat: Int, val label: String)

/** The reply shown (ephemerally) to a player after they use a night button/menu. */
data class InteractionReply(val ack: String)

/** Neutral button style so the gateway interface stays free of JDA types (mapped in the JDA impl). */
enum class ButtonStyle { PRIMARY, SECONDARY, SUCCESS, DANGER }

/** One button on a court prompt (day speech controls, poll enroll/withdraw/vote, direction choice). */
data class CourtButton(val customId: String, val label: String, val style: ButtonStyle = ButtonStyle.SECONDARY)

/**
 * Receives Discord component interactions (night-action select menus, wolf-kill vote buttons) and
 * routes them into the game engine. The gateway owns the JDA wiring; this handler owns the meaning.
 * Custom ids are namespaced `wh:...` and parsed by the implementation.
 *
 * [channelId] is the channel the interaction was clicked in. Seat channels are bound 1:1 to a seat,
 * so a judge (who can see every seat channel) clicking a night prompt acts **on behalf of** the seat
 * that channel belongs to — that's how the judge votes/acts for players.
 */
fun interface DiscordInteractionHandler {
    fun handle(guildId: Long, userId: Long, channelId: Long, customId: String, values: List<String>): InteractionReply?
}

/**
 * Receives wolf-team chat lines relayed from the seat channels during the night, so they can be
 * surfaced on the judge night board. The gateway gates on night-active + wolf-chat membership and
 * formats the author label; the handler owns persistence + snapshot broadcast. Registered
 * post-construct (like the interaction handler) to avoid a constructor cycle with the gateway.
 */
fun interface WolfChatHandler {
    fun onWolfChat(guildId: Long, seat: Int, author: String, avatar: String?, content: String)
}

/**
 * Routes the `/server` slash command and bot-join events into the provisioning service. As with the
 * interaction handler, the gateway owns the JDA wiring and this owns the meaning.
 */
interface DiscordCommandHandler {
    /** `/server create` — returns the zh-TW reply (instructions + invite link, or an error). */
    fun onServerCreate(creatorId: Long, playerCount: Int, doubleIdentity: Boolean): String

    /** `/server delete` — returns the zh-TW reply. */
    fun onServerDelete(creatorId: Long, guildId: Long): String

    /** The bot joined (or became ready in) a guild — provision it if a pending config matches. */
    fun onGuildJoined(guildId: Long, ownerId: Long)
}

/** Custom-id constants shared between the orchestrator (parsing) and the gateway (building). */
object InteractionIds {
    const val PREFIX = "wh"

    /** The namespace segment (`wh:<ns>:...`) used by [dev.robothanzo.werewolf.service.InteractionRouter]. */
    const val NS_NIGHT = "night"
    const val NS_DAY = "day"

    /** Night action select menu: `wh:night:act:<abilityId>` ; selected value = target seat or SKIP. */
    const val NIGHT_ACTION = "wh:night:act"
    /** Wolf-kill vote button: `wh:night:wolf:<targetSeat|SKIP>`. */
    const val WOLF_VOTE = "wh:night:wolf"
    const val SKIP = "SKIP"
    /** Witch "save the wolves' target" option value. */
    const val WITCH_SAVE = "SAVE"

    // --- day-side (court) interactions, namespace `wh:day:...` ---
    /** Speech: the current speaker ends their own turn. */
    const val SPEECH_SKIP = "wh:day:speech:skip"
    /** Speech: a player votes to force the current speaker off the stage (下台). */
    const val SPEECH_INTERRUPT = "wh:day:speech:interrupt"
    /** Police election: toggle enrollment. */
    const val POLICE_ENROLL = "wh:day:police:enroll"
    /** Police election: a candidate withdraws. */
    const val POLICE_WITHDRAW = "wh:day:police:withdraw"
    /** Police election: the badge-holder picks the speech direction — `wh:day:police:dir:<UP|DOWN>`. */
    const val POLICE_DIR = "wh:day:police:dir"
    /** Police election vote button — `wh:day:police:vote:<candidateSeat>`. */
    const val POLICE_VOTE = "wh:day:police:vote"
    /** Expel vote button — `wh:day:expel:<targetSeat>`. */
    const val EXPEL_VOTE = "wh:day:expel"
}
