package dev.robothanzo.werewolf.game.night

import dev.robothanzo.werewolf.game.roles.RoleIds.DEMON_HUNTER
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

class NightResolverTest {

    private val resolver = NightResolver(registry())

    /** A board where seat 9 is a villager (good) and seat 12 is a wolf — used for hunt faction tests. */
    private fun board() = session(
        playerCount = 12,
        seats = (1..12).map {
            when (it) {
                12 -> seat(it, WOLF to false)
                else -> seat(it, VILLAGER to false)
            }
        },
    )

    @Test
    fun `wolf knife kills the target`() {
        val r = resolver.resolve(NightDeclarations(wolfKill = NightAction(setOf(1), 9)), board())
        assertEquals(setOf(9), r.deadSeats)
    }

    @Test
    fun `guard protection saves the target`() {
        val r = resolver.resolve(
            NightDeclarations(wolfKill = NightAction(setOf(1), 9), guard = NightAction(setOf(7), 9)),
            board(),
        )
        assertTrue(r.deadSeats.isEmpty())
    }

    @Test
    fun `witch antidote saves the target`() {
        val r = resolver.resolve(
            NightDeclarations(wolfKill = NightAction(setOf(1), 9), witchActor = 5, witchSave = 9),
            board(),
        )
        assertTrue(r.deadSeats.isEmpty())
    }

    @Test
    fun `同守同救 - guard plus save on the same seat still dies`() {
        val r = resolver.resolve(
            NightDeclarations(
                wolfKill = NightAction(setOf(1), 9),
                guard = NightAction(setOf(7), 9),
                witchActor = 5, witchSave = 9,
            ),
            board(),
        )
        assertEquals(setOf(9), r.deadSeats)
    }

    @Test
    fun `夢魘 fearing a wolf voids the kill`() {
        // fear targets seat 1, who is one of the wolf-kill actors → no knife this night.
        val r = resolver.resolve(
            NightDeclarations(fearTarget = 1, wolfKill = NightAction(setOf(1), 9)),
            board(),
        )
        assertTrue(r.deadSeats.isEmpty())
    }

    @Test
    fun `魔術師 swap redirects the wolf knife`() {
        // swap seats 9 and 4; the wolves aimed at 9, so 4 dies instead.
        val r = resolver.resolve(
            NightDeclarations(swap = 9 to 4, wolfKill = NightAction(setOf(1), 9)),
            board(),
        )
        assertEquals(setOf(4), r.deadSeats)
    }

    @Test
    fun `witch poison kills and suppresses revenge`() {
        val r = resolver.resolve(
            NightDeclarations(witchActor = 5, witchPoison = 6),
            board(),
        )
        assertEquals(setOf(6), r.deadSeats)
        assertTrue(r.deaths.single { it.seat == 6 }.suppressRevenge)
    }

    @Test
    fun `poisoning the wolf target yields one suppressed death`() {
        val r = resolver.resolve(
            NightDeclarations(
                wolfKill = NightAction(setOf(1), 6),
                witchActor = 5, witchPoison = 6,
            ),
            board(),
        )
        assertEquals(setOf(6), r.deadSeats)
        assertTrue(r.deaths.single().suppressRevenge)
    }

    @Test
    fun `獵魔人 hunting a wolf kills the wolf`() {
        val r = resolver.resolve(
            NightDeclarations(hunt = NightAction(setOf(3), 12)), // seat 12 is a wolf
            board(),
        )
        assertEquals(setOf(12), r.deadSeats)
    }

    @Test
    fun `獵魔人 hunting a good player kills the hunter`() {
        val r = resolver.resolve(
            NightDeclarations(hunt = NightAction(setOf(3), 9)), // seat 9 is good
            board(),
        )
        assertEquals(setOf(3), r.deadSeats)
    }

    @Test
    fun `狼美人 殉情 - charmed dies with the 狼美人 and before her`() {
        // wolves kill the 狼美人 (seat 2); seat 8 is charmed → both die, charmed listed first.
        val r = resolver.resolve(
            NightDeclarations(
                wolfKill = NightAction(setOf(1), 2),
                charm = NightAction(setOf(2), 8),
            ),
            board(),
        )
        assertEquals(setOf(2, 8), r.deadSeats)
        assertEquals(8, r.deaths.first().seat) // charmed dies before the 狼美人
        assertTrue(r.deaths.single { it.seat == 8 }.suppressRevenge)
    }

    @Test
    fun `charm has no effect while the 狼美人 survives`() {
        val r = resolver.resolve(
            NightDeclarations(
                wolfKill = NightAction(setOf(1), 9),
                charm = NightAction(setOf(2), 8),
            ),
            board(),
        )
        assertEquals(setOf(9), r.deadSeats)
        assertFalse(8 in r.deadSeats)
    }

    @Test
    fun `fearing the witch voids both potions`() {
        val r = resolver.resolve(
            NightDeclarations(
                fearTarget = 5,
                wolfKill = NightAction(setOf(1), 9),
                witchActor = 5, witchSave = 9, witchPoison = 6,
            ),
            board(),
        )
        // save voided → 9 dies; poison voided → 6 lives
        assertEquals(setOf(9), r.deadSeats)
    }

    @Test
    fun `女巫自救被禁止時仍死亡`() {
        // wolves knife the witch (seat 5); she saves herself → blocked by default 房規, so she dies.
        val s = session(
            playerCount = 12,
            seats = (1..12).map { if (it == 5) seat(it, WITCH to false) else seat(it, VILLAGER to false) },
        )
        val r = resolver.resolve(
            NightDeclarations(wolfKill = NightAction(setOf(1), 5), witchActor = 5, witchSave = 5),
            s,
        )
        assertEquals(setOf(5), r.deadSeats)
    }

    @Test
    fun `女巫自救開啟時可自救`() {
        val s = session(
            playerCount = 12,
            seats = (1..12).map { if (it == 5) seat(it, WITCH to false) else seat(it, VILLAGER to false) },
        ) { settings.witchSelfSave = true }
        val r = resolver.resolve(
            NightDeclarations(wolfKill = NightAction(setOf(1), 5), witchActor = 5, witchSave = 5),
            s,
        )
        assertTrue(r.deadSeats.isEmpty())
    }

    @Test
    fun `獵魔人免疫女巫毒`() {
        // seat 7 is a 獵魔人; the witch poisons it → immune (ROLES.md 獵魔人 被動), so it lives.
        val s = session(
            playerCount = 12,
            seats = (1..12).map { if (it == 7) seat(it, DEMON_HUNTER to false) else seat(it, VILLAGER to false) },
        )
        val r = resolver.resolve(NightDeclarations(witchActor = 5, witchPoison = 7), s)
        assertTrue(r.deadSeats.isEmpty())
    }

    @Test
    fun `peaceful night yields no deaths`() {
        val r = resolver.resolve(NightDeclarations(), board())
        assertTrue(r.deaths.isEmpty())
    }
}
