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
) {
    /** Zero-padded seat name, e.g. seat 3 → "03" (used as 玩家03). */
    val paddedNumber: String get() = number.toString().padStart(2, '0')

    /** A seat is alive while it has at least one living card; an empty (unassigned) seat is not "alive". */
    val alive: Boolean get() = cards.any { !it.dead }

    val assigned: Boolean get() = memberId != null

    /** Count of living cards — used by win-condition faction tallies. */
    fun livingCards(): List<IdentityCard> = cards.filter { !it.dead }
}
