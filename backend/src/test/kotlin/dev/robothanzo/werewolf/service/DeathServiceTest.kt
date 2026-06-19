package dev.robothanzo.werewolf.service

import dev.robothanzo.werewolf.discord.NicknameService
import dev.robothanzo.werewolf.game.roles.RoleIds.HIDDEN_WOLF
import dev.robothanzo.werewolf.game.roles.RoleIds.HUNTER
import dev.robothanzo.werewolf.game.roles.RoleIds.KNIGHT
import dev.robothanzo.werewolf.game.roles.RoleIds.VILLAGER
import dev.robothanzo.werewolf.game.roles.RoleIds.WHITE_WOLF_KING
import dev.robothanzo.werewolf.game.roles.RoleIds.WOLF
import dev.robothanzo.werewolf.game.roles.RoleIds.WOLF_BEAUTY
import dev.robothanzo.werewolf.support.TestFixtures
import dev.robothanzo.werewolf.support.TestFixtures.seat
import dev.robothanzo.werewolf.support.TestFixtures.session
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DeathServiceTest {

    private val service = DeathService(TestFixtures.registry(), NicknameService(TestFixtures.msg()), null)

    @Test
    fun `judge-killing a 獵人 arms its revenge`() {
        val s = session(seats = listOf(seat(1, HUNTER to false)))
        service.killSeat(s, 1, DeathCause.JUDGE)
        assertTrue(s.seat(1)!!.revengePending)
    }

    @Test
    fun `poison suppresses the 獵人 revenge`() {
        val s = session(seats = listOf(seat(1, HUNTER to false)))
        service.killSeat(s, 1, DeathCause.POISON, suppressRevenge = true)
        assertFalse(s.seat(1)!!.revengePending)
    }

    @Test
    fun `白狼王 only arms on self-destruct`() {
        val expel = session(seats = listOf(seat(1, WHITE_WOLF_KING to false)))
        service.killSeat(expel, 1, DeathCause.EXPEL)
        assertFalse(expel.seat(1)!!.revengePending)

        val boom = session(seats = listOf(seat(1, WHITE_WOLF_KING to false)))
        service.killSeat(boom, 1, DeathCause.SELF_DESTRUCT)
        assertTrue(boom.seat(1)!!.revengePending)
    }

    @Test
    fun `狼美人 death cascades 殉情 onto the charmed seat`() {
        val s = session(seats = listOf(
            seat(1, WOLF_BEAUTY to false) { charmedSeat = 2 },
            seat(2, VILLAGER to false),
        ))
        service.killSeat(s, 1, DeathCause.EXPEL)
        assertFalse(s.seat(2)!!.alive)
    }

    @Test
    fun `騎士 duel on the 狼美人 spares the charmed seat`() {
        val s = session(seats = listOf(
            seat(1, WOLF_BEAUTY to false) { charmedSeat = 2 },
            seat(2, VILLAGER to false),
        ))
        service.killSeat(s, 1, DeathCause.KNIGHT_DUEL)
        assertTrue(s.seat(2)!!.alive)
    }

    @Test
    fun `邱比特 lovers 殉情 together`() {
        val s = session(seats = listOf(
            seat(1, KNIGHT to false) { loverSeat = 2 },
            seat(2, VILLAGER to false) { loverSeat = 1 },
        ))
        service.killSeat(s, 1, DeathCause.JUDGE)
        assertFalse(s.seat(2)!!.alive)
    }

    @Test
    fun `隱狼 dies with the team when it does not inherit the knife`() {
        val s = session(seats = listOf(
            seat(1, WOLF to false),
            seat(2, HIDDEN_WOLF to false),
            seat(3, VILLAGER to false),
        )) { settings.hiddenWolfInheritsKnife = false }
        service.killSeat(s, 1, DeathCause.EXPEL)
        assertFalse(s.seat(2)!!.alive) // 隱狼 falls with the last wolf
    }

    @Test
    fun `隱狼 survives as the last wolf when it inherits the knife`() {
        val s = session(seats = listOf(
            seat(1, WOLF to false),
            seat(2, HIDDEN_WOLF to false),
            seat(3, VILLAGER to false),
        )) { settings.hiddenWolfInheritsKnife = true }
        service.killSeat(s, 1, DeathCause.EXPEL)
        assertTrue(s.seat(2)!!.alive)
    }

    @Test
    fun `killing a seat returns the full death list including cascade`() {
        val s = session(seats = listOf(
            seat(1, WOLF_BEAUTY to false) { charmedSeat = 2 },
            seat(2, VILLAGER to false),
        ))
        val produced = service.killSeat(s, 1, DeathCause.EXPEL)
        assertEquals(setOf(1, 2), produced.map { it.seat }.toSet())
    }
}
