package dev.robothanzo.werewolf.game.assign

import dev.robothanzo.werewolf.domain.Faction
import dev.robothanzo.werewolf.game.roles.RoleIds.CLONE
import dev.robothanzo.werewolf.game.roles.RoleIds.IDIOT
import dev.robothanzo.werewolf.game.roles.RoleIds.SEER
import dev.robothanzo.werewolf.game.roles.RoleIds.VILLAGER
import dev.robothanzo.werewolf.game.roles.RoleIds.WITCH
import dev.robothanzo.werewolf.game.roles.RoleIds.WOLF
import dev.robothanzo.werewolf.support.TestFixtures.registry
import dev.robothanzo.werewolf.support.TestFixtures.session
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class AssignmentServiceTest {

    private val reg = registry()
    private val service = AssignmentService(reg)

    private fun members(n: Int) = (1L..n).toList()

    @Test
    fun `single mode deals one card per seat and flags the idiot`() {
        val s = session(playerCount = 4).apply {
            pool = mutableMapOf(WOLF to 1, SEER to 1, IDIOT to 1, VILLAGER to 1)
        }
        service.assign(s, members(4), Random(42))

        assertTrue(s.assigned)
        assertEquals(4, s.seats.size)
        s.seats.forEach { assertEquals(1, it.cards.size) }
        // exactly the pool was dealt
        assertEquals(
            listOf(IDIOT, SEER, VILLAGER, WOLF),
            s.seats.flatMap { seat -> seat.cards.map { it.roleId } }.sorted(),
        )
        // idiot flag follows the idiot card
        val idiotSeat = s.seats.first { seat -> seat.cards.any { it.roleId == IDIOT } }
        assertTrue(idiotSeat.idiot)
        s.seats.filter { seat -> seat.cards.none { it.roleId == IDIOT } }
            .forEach { assertTrue(!it.idiot) }
    }

    @Test
    fun `double mode forms golden babies from villager pairs`() {
        // N=2 double, 4 villagers → both seats are 平民+平民 → 2 golden babies (== cap).
        val s = session(doubleIdentity = true, playerCount = 2).apply {
            pool = mutableMapOf(VILLAGER to 4)
        }
        service.assign(s, members(2), Random(1))

        val gbabies = s.seats.filter { it.goldenBaby }
        assertEquals(2, gbabies.size)
        gbabies.forEach { seat ->
            assertEquals(2, seat.cards.size)
            assertTrue(seat.cards.all { reg.factionOf(it.roleId) == Faction.VILLAGER })
        }
    }

    @Test
    fun `golden baby count never exceeds the cap across many seeds`() {
        repeat(50) { seed ->
            val s = session(doubleIdentity = true, playerCount = 4).apply {
                pool = mutableMapOf(VILLAGER to 4, SEER to 2, WITCH to 1, WOLF to 1)
            }
            service.assign(s, members(4), Random(seed.toLong()))
            assertTrue(s.seats.count { it.goldenBaby } <= AssignmentService.GOLDEN_BABY_CAP)
        }
    }

    @Test
    fun `always forms at least one golden baby when villagers exist`() {
        // Villager-scarce pool (only 2 平民 among 8 cards): a greedy deal can hand both villagers
        // out as *second* cards (paired with non-villager firsts) and produce zero 金寶寶, which
        // makes the wolves' win target vacuous. Across many seeds there must always be ≥1.
        repeat(100) { seed ->
            val s = session(doubleIdentity = true, playerCount = 4).apply {
                pool = mutableMapOf(VILLAGER to 2, WOLF to 3, SEER to 2, WITCH to 1)
            }
            service.assign(s, members(4), Random(seed.toLong()))
            val gbabies = s.seats.filter { it.goldenBaby }
            assertTrue(gbabies.isNotEmpty(), "seed $seed produced no golden baby")
            assertTrue(gbabies.size <= AssignmentService.GOLDEN_BABY_CAP)
            gbabies.forEach { seat ->
                assertEquals(2, seat.cards.size)
                assertTrue(seat.cards.all { reg.factionOf(it.roleId) == Faction.VILLAGER })
            }
        }
    }

    @Test
    fun `clone copies its partner identity and flags the seat`() {
        // N=1 double, pool = clone + seer → clone copies seer → two seers, flagged clone.
        val s = session(doubleIdentity = true, playerCount = 1).apply {
            pool = mutableMapOf(CLONE to 1, SEER to 1)
        }
        service.assign(s, members(1), Random(7))

        val seat = s.seats.single()
        assertTrue(seat.clone)
        assertEquals(listOf(SEER, SEER), seat.cards.map { it.roleId })
    }

    @Test
    fun `wolf card is ordered second`() {
        // N=1 double, pool = wolf + villager → wolf must end up as the second card.
        repeat(20) { seed ->
            val s = session(doubleIdentity = true, playerCount = 1).apply {
                pool = mutableMapOf(WOLF to 1, VILLAGER to 1)
            }
            service.assign(s, members(1), Random(seed.toLong()))
            val seat = s.seats.single()
            assertEquals(Faction.WOLF, reg.factionOf(seat.cards[1].roleId))
            assertEquals(Faction.VILLAGER, reg.factionOf(seat.cards[0].roleId))
        }
    }

    @Test
    fun `refuses to assign twice`() {
        val s = session(playerCount = 2).apply { pool = mutableMapOf(WOLF to 1, VILLAGER to 1) }
        service.assign(s, members(2), Random(1))
        val ex = assertThrows(AssignmentException::class.java) {
            service.assign(s, members(2), Random(1))
        }
        assertEquals("assign.error.already", ex.messageKey)
    }

    @Test
    fun `rejects member-count mismatch`() {
        val s = session(playerCount = 4).apply {
            pool = mutableMapOf(WOLF to 2, VILLAGER to 2)
        }
        val ex = assertThrows(AssignmentException::class.java) {
            service.assign(s, members(3), Random(1))
        }
        assertEquals("assign.error.count_mismatch", ex.messageKey)
    }

    @Test
    fun `rejects pool-size mismatch`() {
        val s = session(playerCount = 4).apply {
            pool = mutableMapOf(WOLF to 1, VILLAGER to 1) // only 2, need 4
        }
        val ex = assertThrows(AssignmentException::class.java) {
            service.assign(s, members(4), Random(1))
        }
        assertEquals("assign.error.pool_single", ex.messageKey)
    }
}
