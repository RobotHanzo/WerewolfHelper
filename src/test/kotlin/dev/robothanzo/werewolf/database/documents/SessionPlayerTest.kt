package dev.robothanzo.werewolf.database.documents

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SessionPlayerTest {
    private lateinit var player: Player

    private fun getPrivateLongField(target: Any, fieldName: String): Long {
        val field = target.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        return field.getLong(target)
    }

    private fun getPrivateNullableLongField(target: Any, fieldName: String): Long? {
        val field = target.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        return field.get(target) as Long?
    }

    @BeforeEach
    fun setup() {
        player = Player(
            id = 1,
            roleId = 100L,
            channelId = 200L
        )
    }

    @Test
    fun testPlayerInitialization() {
        assertEquals(1, player.id)
        assertEquals(100L, getPrivateLongField(player, "roleId"))
        assertEquals(200L, getPrivateLongField(player, "channelId"))
        assertFalse(player.police)
        assertFalse(player.idiot)
        assertFalse(player.jinBaoBao)
        assertFalse(player.duplicated)
        assertFalse(player.rolePositionLocked)
    }

    @Test
    fun testPlayerNicknameGeneration() {
        player.updateUserId(100L)
        player.roles = mutableListOf("狼人")
        val nickname = player.nickname
        assertEquals(nickname, "玩家01")
    }

    @Test
    fun testPlayerRoles() {
        player.roles = mutableListOf("狼人", "平民")
        assertEquals(2, player.roles.size)
        assertEquals("狼人", player.roles[0])
        assertEquals("平民", player.roles[1])
    }

    @Test
    fun testPlayerDeadRoles() {
        player.roles = mutableListOf("狼人", "平民")
        player.deadRoles = mutableListOf("狼人")
        assertEquals(1, player.deadRoles.size)
        assertEquals(/* expected = */ "狼人", /* actual = */ player.deadRoles[0])
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
        assertNull(getPrivateNullableLongField(player, "userId"))
        player.updateUserId(500L)
        assertEquals(500L, getPrivateNullableLongField(player, "userId"))
    }

    @Test
    fun testIsWolfRole() {
        assertTrue(Player.isWolf("狼人"))
        assertTrue(Player.isWolf("特殊狼人"))
        assertFalse(Player.isWolf("平民"))
        assertFalse(Player.isWolf("獵人"))
    }

    @Test
    fun testIsGodRole() {
        assertTrue(Player.isGod("獵人"))
        assertTrue(Player.isGod("預言家"))
        assertTrue(Player.isGod("守衛"))
        assertFalse(Player.isGod("狼人"))
        assertFalse(Player.isGod("平民"))
    }

    @Test
    fun testIsVillagerRole() {
        assertTrue(Player.isVillager("平民"))
        assertFalse(Player.isVillager("狼人"))
        assertFalse(Player.isVillager("獵人"))
    }

    @Test
    fun testPlayerIDFormat() {
        val format = Player.ID_FORMAT
        assertNotNull(format)
        val formatted = format.format(1L)
        assertEquals("01", formatted)
    }

    @Test
    fun testMultiplePlayersIndependence() {
        val player1 = Player(id = 1, roleId = 100L, channelId = 200L)
        val player2 = Player(id = 2, roleId = 101L, channelId = 201L)

        player1.police = true
        player2.idiot = true

        assertTrue(player1.police)
        assertFalse(player1.idiot)
        assertFalse(player2.police)
        assertTrue(player2.idiot)
    }

    @Test
    fun testPlayerIsAlive() {
        // Test 2: Player with empty roles is dead
        player.roles = mutableListOf()
        assertFalse(player.alive, "Player with empty roles should be dead")

        // Test 3: Player with roles but no deadRoles is alive
        player.roles = mutableListOf("狼人")
        player.deadRoles = mutableListOf()
        assertTrue(player.alive, "Player with roles and null deadRoles should be alive")

        // Test 4: Player with 1 role and no deadRoles is alive
        player.roles = mutableListOf("狼人", "平民")
        player.deadRoles = mutableListOf()
        assertTrue(player.alive, "Player with multiple roles and null deadRoles should be alive")

        // Test 5: Player with 1 role and 1 deadRole is dead
        player.roles = mutableListOf("狼人")
        player.deadRoles = mutableListOf("狼人")
        assertFalse(player.alive, "Player with equal roles and deadRoles should be dead")

        // Test 6: Player with 2 roles and 1 deadRole is alive
        player.roles = mutableListOf("狼人", "平民")
        player.deadRoles = mutableListOf("狼人")
        assertTrue(player.alive, "Player with more roles than deadRoles should be alive")

        // Test 7: Player with 3 roles and 2 deadRoles is alive
        player.roles = mutableListOf("狼人", "平民", "獵人")
        player.deadRoles = mutableListOf("狼人", "平民")
        assertTrue(player.alive, "Player with 3 roles and 2 deadRoles should be alive")

        // Test 8: Player with 3 roles and 3 deadRoles is dead
        player.roles = mutableListOf("狼人", "平民", "獵人")
        player.deadRoles = mutableListOf("狼人", "平民", "獵人")
        assertFalse(player.alive, "Player with all roles dead should be dead")

        // Test 9: Empty deadRoles list but has roles is alive
        player.roles = mutableListOf("狼人", "平民")
        player.deadRoles = mutableListOf()
        assertTrue(player.alive, "Player with roles and empty deadRoles should be alive")
    }
}
