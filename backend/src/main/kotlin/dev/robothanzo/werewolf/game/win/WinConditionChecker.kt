package dev.robothanzo.werewolf.game.win

import dev.robothanzo.werewolf.domain.Faction
import dev.robothanzo.werewolf.domain.GameSession
import dev.robothanzo.werewolf.game.roles.RoleRegistry
import org.springframework.stereotype.Service

/** Living-identity tally per faction (doubles so the police +0.5 parity bonus fits). */
data class FactionTally(val wolf: Double, val god: Double, val vill: Double) {
    val good: Double get() = god + vill
}

/** Outcome of a win check. [reasonKey] is an i18n key for the announcement. */
data class WinResult(
    val over: Boolean,
    val winner: Faction? = null,
    val reasonKey: String? = null,
) {
    companion object {
        val NOT_OVER = WinResult(false)
    }
}

/**
 * Win conditions per FEATURES §2, checked after every death. Counts **living identities** (not
 * players) per faction across all seats; supports all-god setups, 金寶寶 in double mode, and the
 * police +0.5 parity bonus in single mode.
 */
@Service
class WinConditionChecker(private val roles: RoleRegistry) {

    fun tally(session: GameSession): FactionTally {
        var wolf = 0.0
        var god = 0.0
        var vill = 0.0
        for (seat in session.seats) {
            if (!seat.assigned) continue
            for (card in seat.livingCards()) {
                // A living 複製人 counts toward gods regardless of the copied identity.
                val faction = if (seat.clone) Faction.GOD else roles.factionOf(card.roleId)
                when (faction) {
                    Faction.WOLF -> wolf += 1
                    Faction.GOD -> god += 1
                    Faction.VILLAGER -> vill += 1
                }
            }
        }
        return FactionTally(wolf, god, vill)
    }

    fun check(session: GameSession): WinResult {
        val t = tally(session)

        // Good wins only by eliminating every wolf identity.
        if (t.wolf == 0.0) {
            return WinResult(true, Faction.GOD, "game.over.reason.wolves")
        }

        // 屠神 — all gods dead.
        if (t.god == 0.0 && hasFaction(session, Faction.GOD)) {
            return WinResult(true, Faction.WOLF, "game.over.reason.gods")
        }

        if (session.settings.doubleIdentity) {
            // Double-identity: wolves win when every 金寶寶 is fully dead.
            val gbabySeats = session.seats.filter { it.goldenBaby && it.assigned }
            if (gbabySeats.isNotEmpty() && gbabySeats.none { it.alive }) {
                return WinResult(true, Faction.WOLF, "game.over.reason.gbaby")
            }
        } else {
            // Single-identity: 屠民 — all villagers dead (only if the board ever had villagers).
            if (hadVillagers(session) && t.vill == 0.0) {
                return WinResult(true, Faction.WOLF, "game.over.reason.villagers")
            }
            // Parity — wolves ≥ gods + villagers, with the police +0.5 on its own faction's side.
            val (wolfSide, goodSide) = applyPoliceBonus(session, t)
            if (wolfSide >= goodSide) {
                return WinResult(true, Faction.WOLF, "game.over.reason.parity")
            }
        }

        return WinResult.NOT_OVER
    }

    private fun applyPoliceBonus(session: GameSession, t: FactionTally): Pair<Double, Double> {
        var wolfSide = t.wolf
        var goodSide = t.good
        val policeSeat = session.policeSeat?.let { session.seat(it) }
        if (policeSeat != null && policeSeat.alive) {
            val card = policeSeat.livingCards().firstOrNull()
            val faction = if (policeSeat.clone) Faction.GOD else card?.let { roles.factionOf(it.roleId) }
            when (faction) {
                Faction.WOLF -> wolfSide += 0.5
                Faction.GOD, Faction.VILLAGER -> goodSide += 0.5
                null -> {}
            }
        }
        return wolfSide to goodSide
    }

    private fun hasFaction(session: GameSession, faction: Faction): Boolean =
        session.seats.any { seat ->
            seat.assigned && seat.cards.any {
                (if (seat.clone) Faction.GOD else roles.factionOf(it.roleId)) == faction
            }
        }

    /** Whether the board was dealt any villager at all (alive or dead) — enables all-god setups. */
    private fun hadVillagers(session: GameSession): Boolean =
        session.seats.any { seat ->
            seat.assigned && seat.cards.any { roles.factionOf(it.roleId) == Faction.VILLAGER }
        }
}
