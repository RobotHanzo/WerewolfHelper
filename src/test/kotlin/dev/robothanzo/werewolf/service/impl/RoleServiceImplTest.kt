package dev.robothanzo.werewolf.service.impl

import dev.robothanzo.werewolf.database.SessionRepository
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.service.DiscordService
import dev.robothanzo.werewolf.service.GameSessionService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.*

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
        roleService = RoleServiceImpl(sessionRepository, discordService, gameSessionService)
    }

    @Test
    fun testAddRoleSuccess() {
        val guildId = 123L
        val session = Session(guildId = guildId)
        session.roles = mutableListOf("狼人", "平民")

        whenever(sessionRepository.findByGuildId(guildId)).thenReturn(Optional.of(session))
        whenever(sessionRepository.save(any())).thenReturn(session)

        roleService.addRole(guildId, "狼人", 2)

        assertEquals(4, session.roles.size)
        assertEquals(3, session.roles.count { it == "狼人" })
        verify(sessionRepository).save(session)
    }

    @Test
    fun testAddRoleSessionNotFound() {
        val guildId = 123L
        whenever(sessionRepository.findByGuildId(guildId)).thenReturn(Optional.empty())

        assertThrows<RuntimeException> {
            roleService.addRole(guildId, "狼人", 1)
        }
    }

    @Test
    fun testRemoveRoleSuccess() {
        val guildId = 123L
        val session = Session(guildId = guildId)
        session.roles = mutableListOf("狼人", "狼人", "平民", "平民")

        whenever(sessionRepository.findByGuildId(guildId)).thenReturn(Optional.of(session))
        whenever(sessionRepository.save(any())).thenReturn(session)

        roleService.removeRole(guildId, "狼人", 1)

        assertEquals(3, session.roles.size)
        assertEquals(1, session.roles.count { it == "狼人" })
        verify(sessionRepository).save(session)
    }

    @Test
    fun testRemoveRoleSessionNotFound() {
        val guildId = 123L
        whenever(sessionRepository.findByGuildId(guildId)).thenReturn(Optional.empty())

        assertThrows<RuntimeException> {
            roleService.removeRole(guildId, "狼人", 1)
        }
    }

    @Test
    fun testGetRolesSuccess() {
        val guildId = 123L
        val session = Session(guildId = guildId)
        session.roles = mutableListOf("狼人", "狼人", "平民")

        whenever(sessionRepository.findByGuildId(guildId)).thenReturn(Optional.of(session))

        val roles = roleService.getRoles(guildId)

        assertEquals(3, roles.size)
        assertEquals(2, roles.count { it == "狼人" })
        assertEquals(1, roles.count { it == "平民" })
    }

    @Test
    fun testGetRolesSessionNotFound() {
        val guildId = 123L
        whenever(sessionRepository.findByGuildId(guildId)).thenReturn(Optional.empty())

        assertThrows<RuntimeException> {
            roleService.getRoles(guildId)
        }
    }

    @Test
    fun testAddMultipleRoles() {
        val guildId = 123L
        val session = Session(guildId = guildId)
        session.roles = mutableListOf()

        whenever(sessionRepository.findByGuildId(guildId)).thenReturn(Optional.of(session))
        whenever(sessionRepository.save(any())).thenReturn(session)

        roleService.addRole(guildId, "狼人", 2)
        roleService.addRole(guildId, "平民", 3)

        assertEquals(5, session.roles.size)
        assertEquals(2, session.roles.count { it == "狼人" })
        assertEquals(3, session.roles.count { it == "平民" })
    }

    @Test
    fun testRemoveNonExistentRole() {
        val guildId = 123L
        val session = Session(guildId = guildId)
        session.roles = mutableListOf("狼人", "平民")

        whenever(sessionRepository.findByGuildId(guildId)).thenReturn(Optional.of(session))
        whenever(sessionRepository.save(any())).thenReturn(session)

        // Removing a role that doesn't exist
        roleService.removeRole(guildId, "獵人", 1)

        // Original roles should remain unchanged
        assertEquals(2, session.roles.size)
    }
}
