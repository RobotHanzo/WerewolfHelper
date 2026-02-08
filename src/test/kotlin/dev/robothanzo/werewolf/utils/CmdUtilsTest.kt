package dev.robothanzo.werewolf.utils

import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.database.documents.Player
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.service.GameSessionService
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.util.*

class CmdUtilsTest {

    private val gameSessionService: GameSessionService = mock()

    @BeforeEach
    fun setUp() {
        WerewolfApplication.gameSessionService = gameSessionService
    }

    @Test
    fun `isAdmin ButtonInteractionEvent returns true if admin`() {
        val event: ButtonInteractionEvent = mock()
        val guild: Guild = mock()
        val member: Member = mock()
        whenever(event.guild).thenReturn(guild)
        whenever(event.member).thenReturn(member)
        whenever(member.permissions).thenReturn(EnumSet.of(Permission.ADMINISTRATOR))

        assertTrue(CmdUtils.isAdmin(event))
        verify(event, never()).hook
    }

    @Test
    fun `isAdmin ButtonInteractionEvent returns false if not admin`() {
        val event: ButtonInteractionEvent = mock()
        val guild: Guild = mock()
        val member: Member = mock()
        val interactionHook: net.dv8tion.jda.api.interactions.InteractionHook = mock()

        whenever(event.guild).thenReturn(guild)
        whenever(event.member).thenReturn(member)
        whenever(member.permissions).thenReturn(EnumSet.noneOf(Permission::class.java))
        whenever(event.hook).thenReturn(interactionHook)

        // Use any() for return type to avoid unresolved reference issues with specific JDA inner classes
        whenever(interactionHook.editOriginal(any<String>())).thenReturn(mock())

        assertFalse(CmdUtils.isAdmin(event))
        verify(interactionHook).editOriginal(":x: 你沒有管理員")
    }

    @Test
    fun `isAuthor returns true if author`() {
        val event: SlashCommandInteractionEvent = mock()
        val user: User = mock()
        whenever(event.user).thenReturn(user)
        whenever(user.idLong).thenReturn(WerewolfApplication.AUTHOR)

        assertTrue(CmdUtils.isAuthor(event))
    }

    @Test
    fun `isAuthor returns false if not author`() {
        val event: SlashCommandInteractionEvent = mock()
        val user: User = mock()
        val interactionHook: net.dv8tion.jda.api.interactions.InteractionHook = mock()

        whenever(event.user).thenReturn(user)
        whenever(user.idLong).thenReturn(123456789L)
        whenever(event.hook).thenReturn(interactionHook)
        whenever(interactionHook.editOriginal(any<String>())).thenReturn(mock())

        assertFalse(CmdUtils.isAuthor(event))
        verify(interactionHook).editOriginal(":x:")
    }

    @Test
    fun `getSession Guild returns session`() {
        val guild: Guild = mock()
        val session: Session = mock()
        whenever(guild.idLong).thenReturn(1L)
        whenever(gameSessionService.getSession(1L)).thenReturn(Optional.of(session))

        assertEquals(session, CmdUtils.getSession(guild))
    }

    @Test
    fun `getSession Guild returns null if guild is null`() {
        assertNull(CmdUtils.getSession(null as Guild?))
    }

    @Test
    fun `Member isAdmin extension returns true if admin`() {
        val member: Member = mock()
        whenever(member.hasPermission(Permission.ADMINISTRATOR)).thenReturn(true)

        assertTrue(member.isAdmin())
    }

    @Test
    fun `Member isSpectator extension returns true if no roles`() {
        val member: Member = mock()
        val guild: Guild = mock()
        val session: Session = mock()
        whenever(member.guild).thenReturn(guild)
        whenever(guild.idLong).thenReturn(1L)
        whenever(gameSessionService.getSession(1L)).thenReturn(Optional.of(session))
        whenever(member.roles).thenReturn(emptyList())

        assertTrue(member.isSpectator())
    }

    @Test
    fun `Member player extension returns player`() {
        val member: Member = mock()
        val guild: Guild = mock()
        val session: Session = mock()
        val player: Player = mock()
        whenever(member.guild).thenReturn(guild)
        whenever(member.idLong).thenReturn(100L)
        whenever(guild.idLong).thenReturn(1L)
        whenever(gameSessionService.getSession(1L)).thenReturn(Optional.of(session))
        whenever(session.getPlayer(100L)).thenReturn(player)
        whenever(player.alive).thenReturn(true)

        assertEquals(player, member.player())
    }
}
