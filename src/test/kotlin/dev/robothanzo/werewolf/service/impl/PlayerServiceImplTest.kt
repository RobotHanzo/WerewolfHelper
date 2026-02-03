package dev.robothanzo.werewolf.service.impl

import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.security.SessionRepository
import dev.robothanzo.werewolf.service.DiscordService
import dev.robothanzo.werewolf.service.GameSessionService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.*

class PlayerServiceImplTest {
    @Mock
    private lateinit var sessionRepository: SessionRepository

    @Mock
    private lateinit var discordService: DiscordService

    @Mock
    private lateinit var gameSessionService: GameSessionService

    private lateinit var playerService: PlayerServiceImpl

    @BeforeEach
    fun setup() {
        MockitoAnnotations.openMocks(this)
        playerService = PlayerServiceImpl(sessionRepository, discordService, gameSessionService)
    }

    @Test
    fun testGetPlayersJSONSuccess() {
        val guildId = 123L
        val session = Session(guildId = guildId)
        val mockPlayersList = listOf(
            mapOf("id" to "1", "nickname" to "玩家1"),
            mapOf("id" to "2", "nickname" to "玩家2")
        )

        whenever(sessionRepository.findByGuildId(guildId)).thenReturn(Optional.of(session))
        whenever(gameSessionService.playersToJSON(session)).thenReturn(mockPlayersList)

        val result = playerService.getPlayersJSON(guildId)

        assertEquals(2, result.size)
        verify(gameSessionService).playersToJSON(session)
    }

    @Test
    fun testGetPlayersJSONSessionNotFound() {
        val guildId = 123L
        whenever(sessionRepository.findByGuildId(guildId)).thenReturn(Optional.empty())

        assertThrows<RuntimeException> {
            playerService.getPlayersJSON(guildId)
        }
    }

    @Test
    fun testSetPlayerCountSessionNotFound() {
        val guildId = 123L
        whenever(sessionRepository.findByGuildId(guildId)).thenReturn(Optional.empty())

        assertThrows<RuntimeException> {
            playerService.setPlayerCount(guildId, 5, {}, {})
        }
    }

    @Test
    fun testUpdatePlayerRolesSuccess() {
        val guildId = 123L
        val playerId = "1"
        val session = Session(guildId = guildId)
        val player = Session.Player(id = 1, roleId = 100L, channelId = 200L)
        session.players["1"] = player

        whenever(sessionRepository.findByGuildId(guildId)).thenReturn(Optional.of(session))
        whenever(sessionRepository.save(any())).thenReturn(session)

        val roles = listOf("狼人", "平民")
        playerService.updatePlayerRoles(guildId, playerId, roles)

        assertEquals(roles, player.roles)
        verify(sessionRepository).save(session)
    }

    @Test
    fun testUpdatePlayerRolesSessionNotFound() {
        val guildId = 123L
        whenever(sessionRepository.findByGuildId(guildId)).thenReturn(Optional.empty())

        assertThrows<RuntimeException> {
            playerService.updatePlayerRoles(guildId, "1", listOf("狼人"))
        }
    }

    @Test
    fun testUpdatePlayerRolesPlayerNotFound() {
        val guildId = 123L
        val session = Session(guildId = guildId)

        whenever(sessionRepository.findByGuildId(guildId)).thenReturn(Optional.of(session))

        assertThrows<RuntimeException> {
            playerService.updatePlayerRoles(guildId, "999", listOf("狼人"))
        }
    }

    @Test
    fun testSwitchRoleOrderSuccess() {
        val guildId = 123L
        val playerId = "1"
        val session = Session(guildId = guildId)
        val player = Session.Player(id = 1, roleId = 100L, channelId = 200L)
        player.roles = mutableListOf("狼人", "平民")
        session.players["1"] = player

        whenever(sessionRepository.findByGuildId(guildId)).thenReturn(Optional.of(session))
        whenever(sessionRepository.save(any())).thenReturn(session)

        playerService.switchRoleOrder(guildId, playerId)

        // Verify roles were swapped
        assertEquals("平民", player.roles?.get(0))
        assertEquals("狼人", player.roles?.get(1))
        verify(sessionRepository).save(session)
    }

    @Test
    fun testSetRolePositionLockSuccess() {
        val guildId = 123L
        val playerId = "1"
        val session = Session(guildId = guildId)
        val player = Session.Player(id = 1, roleId = 100L, channelId = 200L)
        session.players["1"] = player

        whenever(sessionRepository.findByGuildId(guildId)).thenReturn(Optional.of(session))
        whenever(sessionRepository.save(any())).thenReturn(session)

        assertFalse(player.rolePositionLocked)
        playerService.setRolePositionLock(guildId, playerId, true)
        assertTrue(player.rolePositionLocked)

        playerService.setRolePositionLock(guildId, playerId, false)
        assertFalse(player.rolePositionLocked)

        // Verify save was called (may be multiple times due to addLog calling saveSession)
        verify(sessionRepository, atLeastOnce()).save(any())
    }

    @Test
    fun testSetRolePositionLockSessionNotFound() {
        val guildId = 123L
        whenever(sessionRepository.findByGuildId(guildId)).thenReturn(Optional.empty())

        assertThrows<RuntimeException> {
            playerService.setRolePositionLock(guildId, "1", true)
        }
    }
}
