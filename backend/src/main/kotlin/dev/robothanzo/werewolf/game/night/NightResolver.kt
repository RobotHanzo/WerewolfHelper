package dev.robothanzo.werewolf.game.night

import dev.robothanzo.werewolf.domain.Faction
import dev.robothanzo.werewolf.domain.GameSession
import dev.robothanzo.werewolf.game.roles.RoleRegistry
import org.springframework.stereotype.Service

/** An action with its actor(s) and (resolved) target. */
data class NightAction(val actors: Set<Int>, val target: Int?)

/**
 * Structured view of one night's declared actions, derived from the [NightLedger]. Kept as a flat
 * value object so the resolver — where the GAMEPLAY.md interaction rules live — is pure and easy to
 * test against the full matrix.
 */
data class NightDeclarations(
    val swap: Pair<Int, Int>? = null,
    val fearActor: Int? = null,
    val fearTarget: Int? = null,
    val wolfKill: NightAction? = null,
    val guard: NightAction? = null,
    val witchActor: Int? = null,
    val witchSave: Int? = null,
    val witchPoison: Int? = null,
    /** 狼美人 charm; [NightAction.actors] holds the 狼美人 seat. */
    val charm: NightAction? = null,
    /** 獵魔人 hunt; [NightAction.actors] holds the 獵魔人 seat. */
    val hunt: NightAction? = null,
)

/** A death produced by night resolution. [suppressRevenge] = poison/charm denies 獵人/狼王 triggers. */
data class DeathRecord(
    val seat: Int,
    val causeKey: String,
    val suppressRevenge: Boolean = false,
)

data class NightResolution(
    val deaths: List<DeathRecord>,
) {
    val deadSeats: Set<Int> get() = deaths.map { it.seat }.toSet()
}

/**
 * Applies the night interaction rules (GAMEPLAY.md) after every wave has declared, producing the
 * death list: swap redirection (魔術師), fear voiding (夢魘), 同守同救=死, witch save/poison, charm
 * 殉情 (狼美人), poison-blocks-revenge, and 獵魔人 hunt.
 */
@Service
class NightResolver(private val roles: RoleRegistry) {

    fun resolve(decls: NightDeclarations, session: GameSession): NightResolution {
        // 1. 魔術師 — swap redirects every ability targeting either swapped seat.
        val remap: (Int?) -> Int? = { seat ->
            when {
                decls.swap == null || seat == null -> seat
                seat == decls.swap.first -> decls.swap.second
                seat == decls.swap.second -> decls.swap.first
                else -> seat
            }
        }

        val fearTarget = remap(decls.fearTarget)
        val wolfKill = decls.wolfKill?.copy(target = remap(decls.wolfKill.target))
        val guard = decls.guard?.copy(target = remap(decls.guard.target))
        val charm = decls.charm?.copy(target = remap(decls.charm.target))
        val hunt = decls.hunt?.copy(target = remap(decls.hunt.target))
        val witchSave = remap(decls.witchSave)
        val witchPoison = remap(decls.witchPoison)

        // 2. 夢魘 — a feared actor's ability is voided this night.
        fun voided(action: NightAction?): Boolean =
            action != null && fearTarget != null && fearTarget in action.actors
        val witchVoided = fearTarget != null && decls.witchActor == fearTarget

        val deaths = LinkedHashMap<Int, DeathRecord>()
        fun addDeath(seat: Int?, causeKey: String, suppressRevenge: Boolean = false) {
            if (seat == null) return
            val existing = deaths[seat]
            if (existing == null) {
                deaths[seat] = DeathRecord(seat, causeKey, suppressRevenge)
            } else if (suppressRevenge && !existing.suppressRevenge) {
                deaths[seat] = existing.copy(suppressRevenge = true)
            }
        }

        // 3. Wolf knife vs guard vs witch save.
        if (wolfKill != null && !voided(wolfKill) && wolfKill.target != null) {
            val t = wolfKill.target
            val guarded = guard != null && !voided(guard) && guard.target == t
            val saved = !witchVoided && witchSave == t
            // 同守同救 = 死: protection and antidote on the same seat cancel; the seat still dies.
            val dies = if (guarded && saved) true else !(guarded || saved)
            if (dies) addDeath(t, "night.death")
        }

        // 4. 女巫 poison — cannot be guarded; blocks the victim's revenge.
        if (!witchVoided) {
            addDeath(witchPoison, "night.death", suppressRevenge = true)
        }

        // 5. 獵魔人 hunt — good target ⇒ hunter dies; wolf target ⇒ target dies.
        if (hunt != null && !voided(hunt) && hunt.target != null) {
            val targetFaction = factionOfSeat(session, hunt.target)
            if (targetFaction == Faction.WOLF) {
                addDeath(hunt.target, "night.death")
            } else {
                hunt.actors.forEach { addDeath(it, "night.death") }
            }
        }

        // 6. 狼美人 殉情 — if the 狼美人 dies tonight, the charmed seat dies too, before her.
        if (charm != null && charm.target != null) {
            val beautyDied = charm.actors.any { it in deaths }
            if (beautyDied) {
                val ordered = LinkedHashMap<Int, DeathRecord>()
                addDeathTo(ordered, charm.target, "night.charm_died", suppressRevenge = true)
                deaths.values.forEach { addDeathTo(ordered, it.seat, it.causeKey, it.suppressRevenge) }
                return NightResolution(ordered.values.toList())
            }
        }

        return NightResolution(deaths.values.toList())
    }

    private fun addDeathTo(
        map: LinkedHashMap<Int, DeathRecord>,
        seat: Int,
        causeKey: String,
        suppressRevenge: Boolean,
    ) {
        val existing = map[seat]
        if (existing == null) map[seat] = DeathRecord(seat, causeKey, suppressRevenge)
        else if (suppressRevenge && !existing.suppressRevenge) map[seat] = existing.copy(suppressRevenge = true)
    }

    private fun factionOfSeat(session: GameSession, seat: Int): Faction? {
        val s = session.seat(seat) ?: return null
        val card = s.livingCards().firstOrNull() ?: s.cards.firstOrNull() ?: return null
        return if (s.clone) Faction.GOD else roles.factionOf(card.roleId)
    }
}
