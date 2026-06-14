package dev.robothanzo.werewolf.game.day

import dev.robothanzo.werewolf.game.roles.RoleIds.KNIGHT
import dev.robothanzo.werewolf.game.roles.RoleIds.VILLAGER
import dev.robothanzo.werewolf.game.roles.RoleIds.WOLF
import dev.robothanzo.werewolf.support.TestFixtures.registry
import dev.robothanzo.werewolf.support.TestFixtures.seat
import dev.robothanzo.werewolf.support.TestFixtures.session
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DuelResolverTest {

    private val resolver = DuelResolver(registry())

    private fun board() = session(seats = listOf(
        seat(1, KNIGHT to false),
        seat(2, WOLF to false),
        seat(3, VILLAGER to false),
    ))

    @Test
    fun `dueling a wolf kills the wolf`() {
        val r = resolver.resolve(board(), knightSeat = 1, targetSeat = 2)
        assertTrue(r.targetIsWolf)
        assertEquals(2, r.deadSeat)
    }

    @Test
    fun `dueling a good player kills the knight`() {
        val r = resolver.resolve(board(), knightSeat = 1, targetSeat = 3)
        assertFalse(r.targetIsWolf)
        assertEquals(1, r.deadSeat)
    }
}
