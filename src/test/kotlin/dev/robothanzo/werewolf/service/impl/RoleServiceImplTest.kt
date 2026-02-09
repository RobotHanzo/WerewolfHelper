package dev.robothanzo.werewolf.service.impl

import dev.robothanzo.werewolf.database.SessionRepository
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.service.DiscordService
import dev.robothanzo.werewolf.service.GameSessionService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RoleServiceImplTest {
    @Mock
    private lateinit var sessionRepository: SessionRepository

    @Mock
    private lateinit var discordService: DiscordService

    @Mock
    private lateinit var gameSessionService: GameSessionService

    private lateinit var roleService: RoleServiceImpl

    @BeforeEach
    fun setup() {
        MockitoAnnotations.openMocks(this)
        roleService = RoleServiceImpl(gameSessionService)
    }

    @Test
    fun testAddRoleSuccess() {
        val session = Session(guildId = 123L)
        session.roles = mutableListOf("狼人", "平民")

        whenever(gameSessionService.saveSession(any())).thenReturn(session)

        roleService.addRole(session, "狼人", 2)

        assertEquals(4, session.roles.size)
        assertEquals(3, session.roles.count { it == "狼人" })
        verify(gameSessionService).saveSession(session)
    }

    @Test
    fun testRemoveRoleSuccess() {
        val session = Session(guildId = 123L)
        session.roles = mutableListOf("狼人", "狼人", "平民", "平民")

        whenever(gameSessionService.saveSession(any())).thenReturn(session)

        roleService.removeRole(session, "狼人", 1)

        assertEquals(3, session.roles.size)
        assertEquals(1, session.roles.count { it == "狼人" })
        verify(gameSessionService).saveSession(session)
    }

    @Test
    fun testAddMultipleRoles() {
        val session = Session(guildId = 123L)
        session.roles = mutableListOf()

        whenever(gameSessionService.saveSession(any())).thenReturn(session)

        roleService.addRole(session, "狼人", 2)
        roleService.addRole(session, "平民", 3)

        assertEquals(5, session.roles.size)
        assertEquals(2, session.roles.count { it == "狼人" })
        assertEquals(3, session.roles.count { it == "平民" })
    }

    @Test
    fun testRemoveNonExistentRole() {
        val session = Session(guildId = 123L)
        session.roles = mutableListOf("狼人", "平民")

        whenever(gameSessionService.saveSession(any())).thenReturn(session)

        // Removing a role that doesn't exist
        roleService.removeRole(session, "獵人", 1)

        // Original roles should remain unchanged
        assertEquals(2, session.roles.size)
    }
}
