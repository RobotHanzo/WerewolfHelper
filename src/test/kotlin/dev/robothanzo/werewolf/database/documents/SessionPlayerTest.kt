package dev.robothanzo.werewolf.database.documents

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SessionPlayerTest {
    private lateinit var player: Session.Player

    @BeforeEach
    fun setup() {
        player = Session.Player(
            id = 1,
            roleId = 100L,
            channelId = 200L
        )
    }

    @Test
    fun testPlayerInitialization() {
        assertEquals(1, player.id)
        assertEquals(100L, player.roleId)
        assertEquals(200L, player.channelId)
        assertFalse(player.police)
        assertFalse(player.idiot)
        assertFalse(player.jinBaoBao)
        assertFalse(player.duplicated)
        assertFalse(player.rolePositionLocked)
    }

    @Test
    fun testPlayerNicknameGeneration() {
        player.userId = 100L
        player.roles = mutableListOf("狼人")
        val nickname = player.nickname
        assertEquals(nickname, "玩家01")
    }

    @Test
    fun testPlayerRoles() {
        player.roles = mutableListOf("狼人", "平民")
        assertEquals(2, player.roles!!.size)
        assertEquals("狼人", player.roles!![0])
        assertEquals("平民", player.roles!![1])
    }

    @Test
    fun testPlayerDeadRoles() {
        player.roles = mutableListOf("狼人", "平民")
        player.deadRoles = mutableListOf("狼人")
        assertEquals(1, player.deadRoles!!.size)
        assertEquals("狼人", player.deadRoles!![0])
    }

    @Test
    fun testPlayerPoliceStatus() {
        assertFalse(player.police)
        player.police = true
        assertTrue(player.police)
    }

    @Test
    fun testPlayerIdiotStatus() {
        assertFalse(player.idiot)
        player.idiot = true
        assertTrue(player.idiot)
    }

    @Test
    fun testPlayerJinBaoBaoStatus() {
        assertFalse(player.jinBaoBao)
        player.jinBaoBao = true
        assertTrue(player.jinBaoBao)
    }

    @Test
    fun testPlayerDuplicatedStatus() {
        assertFalse(player.duplicated)
        player.duplicated = true
        assertTrue(player.duplicated)
    }

    @Test
    fun testPlayerRolePositionLocked() {
        assertFalse(player.rolePositionLocked)
        player.rolePositionLocked = true
        assertTrue(player.rolePositionLocked)
    }

    @Test
    fun testPlayerUserId() {
        assertFalse(player.userId != null)
        player.userId = 500L
        assertEquals(500L, player.userId)
    }

    @Test
    fun testIsWolfRole() {
        assertTrue(Session.Player.isWolf("狼人"))
        assertTrue(Session.Player.isWolf("特殊狼人"))
        assertFalse(Session.Player.isWolf("平民"))
        assertFalse(Session.Player.isWolf("獵人"))
    }

    @Test
    fun testIsGodRole() {
        assertTrue(Session.Player.isGod("獵人"))
        assertTrue(Session.Player.isGod("預言家"))
        assertTrue(Session.Player.isGod("守衛"))
        assertFalse(Session.Player.isGod("狼人"))
        assertFalse(Session.Player.isGod("平民"))
    }

    @Test
    fun testIsVillagerRole() {
        assertTrue(Session.Player.isVillager("平民"))
        assertFalse(Session.Player.isVillager("狼人"))
        assertFalse(Session.Player.isVillager("獵人"))
    }

    @Test
    fun testPlayerIDFormat() {
        val format = Session.Player.ID_FORMAT
        assertNotNull(format)
        val formatted = format.format(1L)
        assertEquals("01", formatted)
    }

    @Test
    fun testMultiplePlayersIndependence() {
        val player1 = Session.Player(id = 1, roleId = 100L, channelId = 200L)
        val player2 = Session.Player(id = 2, roleId = 101L, channelId = 201L)

        player1.police = true
        player2.idiot = true

        assertTrue(player1.police)
        assertFalse(player1.idiot)
        assertFalse(player2.police)
        assertTrue(player2.idiot)
    }

    @Test
    fun testPlayerIsAlive() {
        // Test 1: Player with no roles is dead
        player.roles = null
        assertFalse(player.isAlive, "Player with null roles should be dead")

        // Test 2: Player with empty roles is dead
        player.roles = mutableListOf()
        assertFalse(player.isAlive, "Player with empty roles should be dead")

        // Test 3: Player with roles but no deadRoles is alive
        player.roles = mutableListOf("狼人")
        player.deadRoles = null
        assertTrue(player.isAlive, "Player with roles and null deadRoles should be alive")

        // Test 4: Player with 1 role and no deadRoles is alive
        player.roles = mutableListOf("狼人", "平民")
        player.deadRoles = null
        assertTrue(player.isAlive, "Player with multiple roles and null deadRoles should be alive")

        // Test 5: Player with 1 role and 1 deadRole is dead
        player.roles = mutableListOf("狼人")
        player.deadRoles = mutableListOf("狼人")
        assertFalse(player.isAlive, "Player with equal roles and deadRoles should be dead")

        // Test 6: Player with 2 roles and 1 deadRole is alive
        player.roles = mutableListOf("狼人", "平民")
        player.deadRoles = mutableListOf("狼人")
        assertTrue(player.isAlive, "Player with more roles than deadRoles should be alive")

        // Test 7: Player with 3 roles and 2 deadRoles is alive
        player.roles = mutableListOf("狼人", "平民", "獵人")
        player.deadRoles = mutableListOf("狼人", "平民")
        assertTrue(player.isAlive, "Player with 3 roles and 2 deadRoles should be alive")

        // Test 8: Player with 3 roles and 3 deadRoles is dead
        player.roles = mutableListOf("狼人", "平民", "獵人")
        player.deadRoles = mutableListOf("狼人", "平民", "獵人")
        assertFalse(player.isAlive, "Player with all roles dead should be dead")

        // Test 9: Empty deadRoles list but has roles is alive
        player.roles = mutableListOf("狼人", "平民")
        player.deadRoles = mutableListOf()
        assertTrue(player.isAlive, "Player with roles and empty deadRoles should be alive")
    }
}
