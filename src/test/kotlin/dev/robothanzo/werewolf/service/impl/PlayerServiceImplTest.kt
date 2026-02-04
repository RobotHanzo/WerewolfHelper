package dev.robothanzo.werewolf.service.impl

import dev.robothanzo.werewolf.database.SessionRepository
import dev.robothanzo.werewolf.database.documents.Player
import dev.robothanzo.werewolf.database.documents.Session
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

        // Initialize WerewolfApplication.jda to a mock so Player.send() doesn't throw
        val mockJda = org.mockito.Mockito.mock(net.dv8tion.jda.api.JDA::class.java)
        dev.robothanzo.werewolf.WerewolfApplication.jda = mockJda
    }

    @Test
    fun testGetPlayersJSONSuccess() {
        val guildId = 123L
        val session = Session(guildId = guildId)
        val mockPlayersList = listOf(
            mapOf("id" to "1", "nickname" to "玩家1"),
            mapOf("id" to "2", "nickname" to "玩家2")
        )

        whenever(gameSessionService.playersToJSON(session)).thenReturn(mockPlayersList)

        val result = playerService.getPlayersJSON(session)

        assertEquals(2, result.size)
        verify(gameSessionService).playersToJSON(session)
    }

    @Test
    fun testGetPlayersJSONWithEmptySession() {
        val session = Session(guildId = 123L)
        whenever(gameSessionService.playersToJSON(session)).thenReturn(emptyList())

        val result = playerService.getPlayersJSON(session)

        assertTrue(result.isEmpty())
    }

    @Test
    fun testSetPlayerCountWithoutJdaThrows() {
        val session = Session(guildId = 123L)
        whenever(discordService.jda).thenReturn(null)

        assertThrows<Exception> {
            playerService.setPlayerCount(session, 5, {}, {})
        }
    }

    @Test
    fun testUpdatePlayerRolesSuccess() {
        val guildId = 123L
        val session = Session(guildId = guildId)
        val player = Player(id = 1, roleId = 100L, channelId = 200L)
        player.session = session
        session.players["1"] = player

        whenever(sessionRepository.save(any())).thenReturn(session)

        val roles = listOf("狼人", "平民")
        playerService.updatePlayerRoles(player, roles)

        assertEquals(roles, player.roles)
        verify(sessionRepository).save(session)
    }

    @Test
    fun testUpdatePlayerRolesPlayerNotFound() {
        val session = Session(guildId = 123L)

        // Player not present in session
        val missingPlayer = Player(id = 999, roleId = 0L, channelId = 0L)
        missingPlayer.session = session

        val roles = listOf("狼人")
        playerService.updatePlayerRoles(missingPlayer, roles)

        // The provided player instance should be updated even if not stored in session.players
        assertEquals(roles, missingPlayer.roles)
        verify(sessionRepository).save(session)
    }

    @Test
    fun testSwitchRoleOrderSuccess() {
        val guildId = 123L
        val session = Session(guildId = guildId)
        val player = Player(id = 1, roleId = 100L, channelId = 0L)
        player.roles = mutableListOf("狼人", "平民")
        player.session = session
        session.players["1"] = player

        whenever(sessionRepository.save(any())).thenReturn(session)

        playerService.switchRoleOrder(player)

        // Verify roles were swapped
        assertEquals("平民", player.roles?.get(0))
        assertEquals("狼人", player.roles?.get(1))
        verify(sessionRepository).save(session)
    }

    @Test
    fun testSetRolePositionLockSuccess() {
        val guildId = 123L
        val session = Session(guildId = guildId)
        val player = Player(id = 1, roleId = 100L, channelId = 200L)
        player.session = session
        session.players["1"] = player

        whenever(sessionRepository.save(any())).thenReturn(session)

        assertFalse(player.rolePositionLocked)
        playerService.setRolePositionLock(player, true)
        assertTrue(player.rolePositionLocked)

        playerService.setRolePositionLock(player, false)
        assertFalse(player.rolePositionLocked)

        // Verify save was called (may be multiple times due to addLog calling saveSession)
        verify(sessionRepository, atLeastOnce()).save(any())
    }
}
