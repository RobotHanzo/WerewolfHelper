package dev.robothanzo.werewolf.discord

/** A selectable target in a night prompt (a seat + its display label). */
data class SeatOption(val seat: Int, val label: String)

/** The reply shown (ephemerally) to a player after they use a night button/menu. */
data class InteractionReply(val ack: String)

/**
 * Receives Discord component interactions (night-action select menus, wolf-kill vote buttons) and
 * routes them into the game engine. The gateway owns the JDA wiring; this handler owns the meaning.
 * Custom ids are namespaced `wh:...` and parsed by the implementation.
 */
fun interface DiscordInteractionHandler {
    fun handle(guildId: Long, userId: Long, customId: String, values: List<String>): InteractionReply?
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
    /** Night action select menu: `wh:night:act:<abilityId>` ; selected value = target seat or SKIP. */
    const val NIGHT_ACTION = "wh:night:act"
    /** Wolf-kill vote button: `wh:night:wolf:<targetSeat|SKIP>`. */
    const val WOLF_VOTE = "wh:night:wolf"
    const val SKIP = "SKIP"
    /** Witch "save the wolves' target" option value. */
    const val WITCH_SAVE = "SAVE"
}
