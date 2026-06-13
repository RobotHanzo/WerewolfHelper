package dev.robothanzo.werewolf.game.win

import dev.robothanzo.werewolf.domain.Faction
import dev.robothanzo.werewolf.game.roles.RoleIds.GUARD
import dev.robothanzo.werewolf.game.roles.RoleIds.SEER
import dev.robothanzo.werewolf.game.roles.RoleIds.VILLAGER
import dev.robothanzo.werewolf.game.roles.RoleIds.WITCH
import dev.robothanzo.werewolf.game.roles.RoleIds.WOLF
import dev.robothanzo.werewolf.support.TestFixtures.registry
import dev.robothanzo.werewolf.support.TestFixtures.seat
import dev.robothanzo.werewolf.support.TestFixtures.session
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WinConditionCheckerTest {

    private val checker = WinConditionChecker(registry())

    private fun alive(roleId: String) = roleId to false
    private fun dead(roleId: String) = roleId to true

    @Test
    fun `good wins when every wolf is dead`() {
        val s = session(
            playerCount = 4,
            seats = listOf(
                seat(1, dead(WOLF)),
                seat(2, alive(SEER)),
                seat(3, alive(VILLAGER)),
                seat(4, alive(VILLAGER)),
            ),
        )
        val r = checker.check(s)
        assertTrue(r.over)
        assertEquals(Faction.GOD, r.winner)
        assertEquals("game.over.reason.wolves", r.reasonKey)
    }

    @Test
    fun `wolves win by slaughtering the gods`() {
        val s = session(
            playerCount = 4,
            seats = listOf(
                seat(1, alive(WOLF)),
                seat(2, dead(SEER)),
                seat(3, alive(VILLAGER)),
                seat(4, alive(VILLAGER)),
            ),
        )
        val r = checker.check(s)
        assertTrue(r.over)
        assertEquals(Faction.WOLF, r.winner)
        assertEquals("game.over.reason.gods", r.reasonKey)
    }

    @Test
    fun `wolves win by slaughtering the villagers`() {
        val s = session(
            playerCount = 4,
            seats = listOf(
                seat(1, alive(WOLF)),
                seat(2, alive(SEER)),
                seat(3, dead(VILLAGER)),
                seat(4, dead(VILLAGER)),
            ),
        )
        val r = checker.check(s)
        assertTrue(r.over)
        assertEquals(Faction.WOLF, r.winner)
        assertEquals("game.over.reason.villagers", r.reasonKey)
    }

    @Test
    fun `all-god setup is supported (no villager-elimination win)`() {
        // 2 wolves vs 2 gods, no villagers ever — wolves win by parity, never by 屠民.
        val s = session(
            playerCount = 4,
            seats = listOf(
                seat(1, alive(WOLF)),
                seat(2, alive(WOLF)),
                seat(3, alive(SEER)),
                seat(4, dead(GUARD)),
            ),
        )
        val r = checker.check(s)
        assertTrue(r.over)
        assertEquals(Faction.WOLF, r.winner)
        assertEquals("game.over.reason.parity", r.reasonKey)
    }

    @Test
    fun `parity not reached while good still outnumbers wolves`() {
        val s = session(
            playerCount = 5,
            seats = listOf(
                seat(1, alive(WOLF)),
                seat(2, alive(SEER)),
                seat(3, alive(WITCH)),
                seat(4, alive(VILLAGER)),
                seat(5, alive(VILLAGER)),
            ),
        )
        assertFalse(checker.check(s).over)
    }

    @Test
    fun `wolves win at exact parity when there is no police`() {
        // 2 wolves vs 2 good (1 god + 1 vill) → 2 >= 2 → wolves win by parity.
        val s = session(
            playerCount = 4,
            seats = listOf(
                seat(1, alive(WOLF)),
                seat(2, alive(WOLF)),
                seat(3, alive(SEER)),
                seat(4, alive(VILLAGER)),
            ),
        )
        val r = checker.check(s)
        assertTrue(r.over)
        assertEquals(Faction.WOLF, r.winner)
        assertEquals("game.over.reason.parity", r.reasonKey)
    }

    @Test
    fun `a good-side police denies the wolves a win at exact parity`() {
        // Same 2 vs 2 board, but the seer holds the badge → good side becomes 2.5, so 2 >= 2.5 is
        // false and the game continues. The +0.5 parity bonus protects the good side.
        val s = session(
            playerCount = 4,
            seats = listOf(
                seat(1, alive(WOLF)),
                seat(2, alive(WOLF)),
                seat(3, alive(SEER)) { police = true },
                seat(4, alive(VILLAGER)),
            ),
            configure = { policeSeat = 3 },
        )
        assertFalse(checker.check(s).over)
    }

    @Test
    fun `double mode - wolves win when all golden babies are dead`() {
        val s = session(
            doubleIdentity = true,
            playerCount = 3,
            seats = listOf(
                seat(1, alive(WOLF), alive(VILLAGER)),
                seat(2, alive(SEER), alive(VILLAGER)),
                seat(3, dead(VILLAGER), dead(VILLAGER)) { goldenBaby = true },
            ),
        )
        val r = checker.check(s)
        assertTrue(r.over)
        assertEquals(Faction.WOLF, r.winner)
        assertEquals("game.over.reason.gbaby", r.reasonKey)
    }

    @Test
    fun `double mode - golden baby alive keeps the game going`() {
        val s = session(
            doubleIdentity = true,
            playerCount = 3,
            seats = listOf(
                seat(1, alive(WOLF), alive(VILLAGER)),
                seat(2, alive(SEER), alive(VILLAGER)),
                seat(3, dead(VILLAGER), alive(VILLAGER)) { goldenBaby = true },
            ),
        )
        assertFalse(checker.check(s).over)
    }

    @Test
    fun `living clone counts toward gods`() {
        // clone seat copied a wolf, but a living clone still counts as a god → gods not all dead.
        val s = session(
            doubleIdentity = true,
            playerCount = 2,
            seats = listOf(
                seat(1, alive(WOLF), alive(WOLF)),
                seat(2, alive(WOLF), alive(WOLF)) { clone = true },
            ),
        )
        // seat 2 is a living clone → counts as gods, so 屠神 has not happened; wolves not yet won by gods.
        val r = checker.check(s)
        // gods present (clone), wolves present; double mode, no golden babies → not over.
        assertFalse(r.over)
    }
}
