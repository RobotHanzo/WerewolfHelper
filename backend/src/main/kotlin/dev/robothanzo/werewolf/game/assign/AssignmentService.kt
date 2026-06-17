package dev.robothanzo.werewolf.game.assign

import dev.robothanzo.werewolf.domain.Faction
import dev.robothanzo.werewolf.domain.GameSession
import dev.robothanzo.werewolf.domain.IdentityCard
import dev.robothanzo.werewolf.domain.Seat
import dev.robothanzo.werewolf.game.roles.RoleIds
import dev.robothanzo.werewolf.game.roles.RoleRegistry
import org.springframework.stereotype.Service
import kotlin.random.Random

/** Thrown when assignment cannot proceed; [messageKey]/[args] render an actionable zh-TW message. */
class AssignmentException(val messageKey: String, val args: List<Any?> = emptyList()) :
    RuntimeException(messageKey)

/**
 * Deals the identity pool onto seats (FEATURES §5) — the most failure-prone operation, so the
 * algorithm is pure and deterministic given an injected [Random] (the production caller passes a
 * fresh one; tests pass a seeded one).
 *
 * Double-identity special rules handled here: 金寶寶 (with the hard cap), 複製人 copy,
 * wolf-card-second ordering, and the 白癡 flag.
 */
@Service
class AssignmentService(private val roles: RoleRegistry) {

    companion object {
        /** Max number of 金寶寶 a board may produce (a historical bug allowed >2). */
        const val GOLDEN_BABY_CAP = 2
    }

    /**
     * Validate and assign. On success, mutates [session] seats/flags and sets `assigned = true`.
     * [eligibleMembers] are the member ids that should be seated (already filtered by the caller).
     */
    fun assign(session: GameSession, eligibleMembers: List<Long>, random: Random = Random.Default) {
        validate(session, eligibleMembers)

        val members = eligibleMembers.shuffled(random)
        val deck = buildDeck(session).toMutableList().apply { shuffle(random) }

        // Reuse the already-provisioned seats so their Discord ids (roleId/channelId) survive the
        // re-deal; recreating Seat objects here would reset those to 0 and break role grants and
        // seat-channel notifications. Only the per-game fields are cleared.
        val seats = (1..session.playerCount).map { n ->
            session.seat(n)?.copy(
                memberId = null,
                cards = mutableListOf(),
                police = false,
                goldenBaby = false,
                clone = false,
                idiot = false,
                orderLocked = false,
            ) ?: Seat(number = n)
        }

        if (session.settings.doubleIdentity) {
            dealDouble(seats, members, deck, random)
        } else {
            dealSingle(seats, members, deck)
        }

        session.seats = seats.toMutableList()
        session.assigned = true
    }

    private fun validate(session: GameSession, members: List<Long>) {
        if (session.assigned) throw AssignmentException("assign.error.already")
        if (members.size != session.playerCount) {
            throw AssignmentException(
                "assign.error.count_mismatch",
                listOf(members.size, session.playerCount),
            )
        }
        val poolSize = session.pool.values.sum()
        val required = session.settings.requiredPoolSize
        if (poolSize != required) {
            val key = if (session.settings.doubleIdentity) "assign.error.pool_double" else "assign.error.pool_single"
            throw AssignmentException(key, listOf(poolSize, required))
        }
    }

    /** Flatten the pool multiset (roleId → count) into a deck of individual roleIds. */
    private fun buildDeck(session: GameSession): List<String> =
        session.pool.flatMap { (roleId, count) -> List(count) { roleId } }

    private fun dealSingle(seats: List<Seat>, members: List<Long>, deck: MutableList<String>) {
        seats.forEachIndexed { i, seat ->
            seat.memberId = members[i]
            val roleId = deck.removeAt(0)
            seat.cards = mutableListOf(IdentityCard(roleId))
            applyFlags(seat)
        }
    }

    private fun dealDouble(
        seats: List<Seat>,
        members: List<Long>,
        deck: MutableList<String>,
        random: Random,
    ) {
        // FEATURES §5 / §4: the board must contain at least one 金寶寶, otherwise the wolves'
        // "kill all 金寶寶" win target is vacuous. The greedy deal below only forms a 金寶寶 when a
        // 平民 happens to land as a seat's *first* card before villagers run out — but a shuffle
        // can hand every 平民 out as a *second* card (paired with a non-villager first), yielding
        // zero 金寶寶. Reserve a 平民+平民 pair out of the deck up front (so earlier seats can't
        // consume the villagers) and hand it to a randomly chosen seat. Skips only when the pool
        // genuinely can't form one (fewer than two villager-faction cards).
        val reservedPair = reserveVillagerPair(deck, random)
        val guaranteedSeat = reservedPair?.let { seats.indices.random(random) }
        // The guaranteed seat already consumes one slot of the 金寶寶 cap budget.
        var goldenBabies = if (reservedPair != null) 1 else 0

        seats.forEachIndexed { i, seat ->
            seat.memberId = members[i]
            if (i == guaranteedSeat) {
                seat.cards = mutableListOf(IdentityCard(reservedPair!!.first), IdentityCard(reservedPair.second))
                seat.goldenBaby = true
                applyFlags(seat)
                return@forEachIndexed
            }

            val first = deck.removeAt(0)
            val second: String

            val firstIsVillager = roles.factionOf(first) == Faction.VILLAGER
            if (firstIsVillager && goldenBabies < GOLDEN_BABY_CAP && deck.any { roles.factionOf(it) == Faction.VILLAGER }) {
                // 金寶寶: force the second card to be a villager too (平民+平民).
                second = takeWhere(deck, random) { roles.factionOf(it) == Faction.VILLAGER }!!
            } else if (firstIsVillager && goldenBabies >= GOLDEN_BABY_CAP) {
                // Cap reached: re-deal the second card from the remaining non-civilians if possible.
                second = takeWhere(deck, random) { roles.factionOf(it) != Faction.VILLAGER }
                    ?: deck.removeAt(0)
            } else {
                second = deck.removeAt(0)
            }

            seat.cards = mutableListOf(IdentityCard(first), IdentityCard(second))
            resolveClone(seat)
            applyWolfOrdering(seat)
            applyFlags(seat)
            // Strictly cap the golden-baby flag: even if a two-villager seat is unavoidable
            // (degenerate all-villager tail), never flag more than the cap.
            if (goldenBabies < GOLDEN_BABY_CAP && isGoldenBaby(seat)) {
                seat.goldenBaby = true
                goldenBabies++
            }
        }
    }

    /** 複製人 copies its partner identity → seat holds two copies of the other identity. */
    private fun resolveClone(seat: Seat) {
        val cloneIdx = seat.cards.indexOfFirst { it.roleId == RoleIds.CLONE }
        if (cloneIdx < 0) return
        val otherIdx = if (cloneIdx == 0) 1 else 0
        val copied = seat.cards[otherIdx].roleId
        seat.cards[cloneIdx] = IdentityCard(copied)
        seat.clone = true
    }

    /** If the first card is a wolf identity, swap so the wolf sits second (consistent reveal order). */
    private fun applyWolfOrdering(seat: Seat) {
        if (seat.cards.size < 2) return
        val firstWolf = roles.factionOf(seat.cards[0].roleId) == Faction.WOLF
        val secondWolf = roles.factionOf(seat.cards[1].roleId) == Faction.WOLF
        if (firstWolf && !secondWolf) {
            seat.cards.reverse()
        }
    }

    private fun applyFlags(seat: Seat) {
        if (seat.cards.any { it.roleId == RoleIds.IDIOT }) seat.idiot = true
    }

    /** A 金寶寶 seat is two villagers (平民+平民, or 平民+複製人 resolved to a villager). */
    private fun isGoldenBaby(seat: Seat): Boolean =
        seat.cards.size == 2 && seat.cards.all { roles.factionOf(it.roleId) == Faction.VILLAGER }

    /**
     * Pull two villager-faction cards out of [deck] to seed a guaranteed 金寶寶, or null if the
     * pool holds fewer than two (no 金寶寶 is possible). 複製人 is GOD faction, so it is never
     * picked here — the reserved pair is always a real 平民+平民.
     */
    private fun reserveVillagerPair(deck: MutableList<String>, random: Random): Pair<String, String>? {
        val first = takeWhere(deck, random) { roles.factionOf(it) == Faction.VILLAGER } ?: return null
        val second = takeWhere(deck, random) { roles.factionOf(it) == Faction.VILLAGER }
        if (second == null) {
            deck.add(first) // put the lone villager back; can't form a pair
            return null
        }
        return first to second
    }

    /** Remove and return the first deck entry matching [predicate], or null if none match. */
    private fun takeWhere(deck: MutableList<String>, random: Random, predicate: (String) -> Boolean): String? {
        val indices = deck.indices.filter { predicate(deck[it]) }
        if (indices.isEmpty()) return null
        return deck.removeAt(indices.random(random))
    }
}
