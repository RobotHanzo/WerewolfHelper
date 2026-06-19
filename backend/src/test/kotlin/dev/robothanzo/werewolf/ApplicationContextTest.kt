package dev.robothanzo.werewolf

import dev.robothanzo.werewolf.discord.DiscordBot
import dev.robothanzo.werewolf.game.roles.RoleRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * Boots the entire Spring context against an embedded MongoDB and no Discord token — verifying every
 * bean wires (all controllers, services, security guards, the WS hub) and that the Discord layer
 * degrades gracefully when the `JDA?` bean is null (the [DiscordBot] still wires with no connection).
 */
@SpringBootTest
class ApplicationContextTest {

    @Autowired
    private lateinit var discord: DiscordBot

    @Autowired
    private lateinit var roles: RoleRegistry

    @Test
    fun `context loads with a null JDA and all 28 roles registered`() {
        assertEquals(28, roles.all.size) // 27 FEATURES §2 identities + 隱狼 (ROLES.md)
    }
}
