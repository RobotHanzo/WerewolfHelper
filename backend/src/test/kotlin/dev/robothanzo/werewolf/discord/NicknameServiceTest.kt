package dev.robothanzo.werewolf.discord

import dev.robothanzo.werewolf.support.TestFixtures
import dev.robothanzo.werewolf.support.TestFixtures.seat
import dev.robothanzo.werewolf.game.roles.RoleIds.SEER
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NicknameServiceTest {

    private val service = NicknameService(TestFixtures.msg())

    @Test
    fun `plain seat`() {
        assertEquals("玩家03", service.build("03", fullyDead = false, police = false))
    }

    @Test
    fun `police suffix only`() {
        assertEquals("玩家03 [警長]", service.build("03", fullyDead = false, police = true))
    }

    @Test
    fun `dead prefix only`() {
        assertEquals("[死人] 玩家03", service.build("03", fullyDead = true, police = false))
    }

    @Test
    fun `dead and police`() {
        assertEquals("[死人] 玩家03 [警長]", service.build("03", fullyDead = true, police = true))
    }

    @Test
    fun `derives from a seat`() {
        val s = seat(7, SEER to false) { police = true }
        assertEquals("玩家07 [警長]", service.nicknameFor(s))
        val dead = seat(8, SEER to true)
        assertEquals("[死人] 玩家08", service.nicknameFor(dead))
    }
}
