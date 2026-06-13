package dev.robothanzo.werewolf

import dev.robothanzo.werewolf.discord.DiscordGateway
import dev.robothanzo.werewolf.game.roles.RoleRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * Boots the entire Spring context against an embedded MongoDB and no Discord token — verifying every
 * bean wires (all controllers, services, security guards, the WS hub) and that the bot layer
 * degrades to the no-op gateway as designed.
 */
@SpringBootTest
class ApplicationContextTest {

    @Autowired
    private lateinit var gateway: DiscordGateway

    @Autowired
    private lateinit var roles: RoleRegistry

    @Test
    fun `context loads with the no-op gateway and all 27 roles registered`() {
        assertFalse(gateway.available) // no token → NoOp
        assertEquals(27, roles.all.size)
    }
}
