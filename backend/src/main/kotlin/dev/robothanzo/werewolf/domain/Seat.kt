package dev.robothanzo.werewolf.domain

/**
 * A numbered seat (1..N), displayed as a zero-padded name 玩家NN. Owns a dedicated Discord role
 * and private channel (managed by the Discord layer), plus one or two identity cards.
 *
 * The police badge is **not** stored here as a role — it is a flag whose only visible
 * representation is the nickname suffix [警長] (FEATURES §2/§12).
 */
data class Seat(
    val number: Int,
    var memberId: Long? = null,
    var cards: MutableList<IdentityCard> = mutableListOf(),
    // Discord-provisioned ids for this seat (0 until provisioned).
    var roleId: Long = 0,
    var channelId: Long = 0,
    // flags
    var police: Boolean = false,
    var goldenBaby: Boolean = false,
    var clone: Boolean = false,
    var idiot: Boolean = false,
    /** Whether the identity-order swap is locked (120 s after assignment). */
    var orderLocked: Boolean = false,
    // --- ROLES.md behavioural state (default-safe, Mongo-additive) ---
    /** 邱比特 lover bond (bidirectional); the two lovers 殉情 together. */
    var loverSeat: Int? = null,
    /** 狼美人 current charm victim — persisted so daytime 殉情 can cascade. */
    var charmedSeat: Int? = null,
    /** An armed but unfired DEATH_REVENGE shot (獵人 / 狼王 / 白狼王). */
    var revengePending: Boolean = false,
    /** Whether the 開槍 select menu has already been posted for this armed shot (so the day flow
     *  prompts each armed seat exactly once, after its 遺言, and blocks advancement until it fires). */
    var revengePrompted: Boolean = false,
    /** 白癡 has flipped its card on expel: stays alive but loses its vote. */
    var idiotRevealed: Boolean = false,
    /** 機械狼 learned identity (its abilities/reads follow this id). */
    var learnedRoleId: String? = null,
    /** 石像鬼 / 隱狼 has inherited the wolf knife (the chat wolves are gone). */
    var knifeArmed: Boolean = false,
    /** 騎士 has spent its one-shot day duel. */
    var duelUsed: Boolean = false,
    /** 血月使徒 has used its one-shot 最後一狼 survival on expel. */
    var bloodMoonRevived: Boolean = false,
) {
    /** Zero-padded seat name, e.g. seat 3 → "03" (used as 玩家03). */
    val paddedNumber: String get() = number.toString().padStart(2, '0')

    /** A seat is alive while it has at least one living card; an empty (unassigned) seat is not "alive". */
    val alive: Boolean get() = cards.any { !it.dead }

    /**
     * The **current** identity: the first living card. In double-identity mode a seat may only
     * act at night as this identity — a still-alive second identity stays dormant until the first
     * one dies (so a seer+witch seat acts only as the seer while both live). Null for an
     * empty/dead seat. Single-identity seats have exactly one card, so this is just that card.
     */
    val activeCard: IdentityCard? get() = cards.firstOrNull { !it.dead }

    val assigned: Boolean get() = memberId != null

    /** Count of living cards — used by win-condition faction tallies. */
    fun livingCards(): List<IdentityCard> = cards.filter { !it.dead }
}
