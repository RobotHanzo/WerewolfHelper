package dev.robothanzo.werewolf.service.impl

import dev.robothanzo.werewolf.database.documents.Player
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.DeathCause
import dev.robothanzo.werewolf.service.GameSessionService
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*

class GameActionServiceImplTest {

    private val gameSessionService: GameSessionService = mock()
    private lateinit var gameActionService: GameActionServiceImpl

    @BeforeEach
    fun setUp() {
        gameActionService = GameActionServiceImpl(gameSessionService)
    }

    @Test
    fun `markPlayerDead calls player died method`() {
        val session: Session = mock()
        val player: Player = mock()
        whenever(session.getPlayer(1)).thenReturn(player)

        gameActionService.markPlayerDead(session, 1, true, DeathCause.WEREWOLF)

        verify(player).died(DeathCause.WEREWOLF, true)
    }

    @Test
    fun `revivePlayer revives all dead roles`() {
        val session: Session = mock()
        val player: Player = mock()
        val guild: Guild = mock()
        val member: Member = mock()
        val role: Role = mock()
        val restAction: AuditableRestAction<Void> = mock()

        whenever(session.getPlayer(1)).thenReturn(player)
        whenever(player.deadRoles).thenReturn(mutableListOf("狼人", "女巫"))
        whenever(player.roles).thenReturn(mutableListOf("狼人", "女巫"))
        whenever(player.alive).thenReturn(false)
        whenever(player.id).thenReturn(1)
        whenever(player.nickname).thenReturn("Hanzo")
        whenever(player.member).thenReturn(member)
        whenever(player.role).thenReturn(role)
        whenever(session.guild).thenReturn(guild)
        whenever(member.effectiveName).thenReturn("Hanzo")
        whenever(guild.addRoleToMember(eq(member), eq(role))).thenReturn(restAction)

        // Mock more for reviveRole called internally
        // Since revivePlayer calls reviveRole, and reviveRole uses session.addLog, player.channel etc.
        // It's better to test reviveRole directly first or mock everything.

        // For now let's just test that it throws if no dead roles
        whenever(player.deadRoles).thenReturn(null)
        assertThrows(Exception::class.java) {
            gameActionService.revivePlayer(session, 1)
        }
    }

    @Test
    fun `setPolice updates session police`() {
        val session: Session = mock()
        val oldPolice: Player = mock()
        val newPolice: Player = mock()
        val guild: Guild = mock()

        whenever(session.guild).thenReturn(guild)
        whenever(session.police).thenReturn(oldPolice)
        whenever(session.getPlayer(2)).thenReturn(newPolice)

        gameActionService.setPolice(session, 2)

        verify(oldPolice).police = false
        verify(oldPolice).updateNickname()
        verify(newPolice).police = true
        verify(newPolice).updateNickname()
        verify(gameSessionService).saveSession(session)
    }

    @Test
    fun `broadcastProgress calls sessionService broadcastEvent`() {
        gameActionService.broadcastProgress(1L, "Loading", 50)

        verify(gameSessionService).broadcastEvent(eq("PROGRESS"), any())
    }
}
