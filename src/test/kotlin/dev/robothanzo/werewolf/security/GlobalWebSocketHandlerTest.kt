package dev.robothanzo.werewolf.security

import dev.robothanzo.werewolf.database.documents.AuthSession
import dev.robothanzo.werewolf.database.documents.UserRole
import dev.robothanzo.werewolf.websocket.WebSocketEventData
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.net.URI

class GlobalWebSocketHandlerTest {

    private lateinit var handler: GlobalWebSocketHandler

    @Mock
    private lateinit var session: WebSocketSession

    @BeforeEach
    fun setup() {
        MockitoAnnotations.openMocks(this)
        handler = GlobalWebSocketHandler()
    }

    @Test
    fun testConnectionEstablished_Success() {
        val user = AuthSession(userId = "user1", guildId = "123", role = UserRole.SPECTATOR)
        whenever(session.attributes).thenReturn(mutableMapOf("user" to user))
        whenever(session.uri).thenReturn(URI("ws://localhost:8080/ws?guildId=123"))

        handler.afterConnectionEstablished(session)

        verify(session, never()).close(any())
    }

    @Test
    fun testConnectionEstablished_NoUser() {
        whenever(session.attributes).thenReturn(mutableMapOf())

        handler.afterConnectionEstablished(session)

        verify(session).close(CloseStatus.POLICY_VIOLATION)
    }

    @Test
    fun testConnectionEstablished_UnauthorizedRole() {
        val user = AuthSession(userId = "user1", guildId = "123", role = UserRole.PENDING)
        whenever(session.attributes).thenReturn(mutableMapOf("user" to user))

        handler.afterConnectionEstablished(session)

        verify(session).close(CloseStatus.POLICY_VIOLATION)
    }

    @Test
    fun testConnectionEstablished_GuildMismatch() {
        val user = AuthSession(userId = "user1", guildId = "123", role = UserRole.SPECTATOR)
        whenever(session.attributes).thenReturn(mutableMapOf("user" to user))
        whenever(session.uri).thenReturn(URI("ws://localhost:8080/ws?guildId=456"))

        handler.afterConnectionEstablished(session)

        verify(session).close(CloseStatus.POLICY_VIOLATION)
    }

    @Test
    fun testHandlePing() {
        val payload = """{"type": "PING"}"""
        doNothing().whenever(session).sendMessage(any())

        handler.handleMessage(session, TextMessage(payload))

        val captor = argumentCaptor<TextMessage>()
        verify(session).sendMessage(captor.capture())
        assertTrue(captor.firstValue.payload.contains("PONG"))
    }

    @Test
    fun testBroadcastToGuild() {
        val user1 = AuthSession(userId = "user1", guildId = "123", role = UserRole.SPECTATOR)
        val session1 = mock(WebSocketSession::class.java)
        whenever(session1.attributes).thenReturn(mutableMapOf("user" to user1))
        whenever(session1.uri).thenReturn(URI("ws://localhost/ws?guildId=123"))
        whenever(session1.isOpen).thenReturn(true)
        whenever(session1.id).thenReturn("s1")

        val user2 = AuthSession(userId = "user2", guildId = "456", role = UserRole.SPECTATOR)
        val session2 = mock(WebSocketSession::class.java)
        whenever(session2.attributes).thenReturn(mutableMapOf("user" to user2))
        whenever(session2.uri).thenReturn(URI("ws://localhost/ws?guildId=456"))
        whenever(session2.isOpen).thenReturn(true)
        whenever(session2.id).thenReturn("s2")

        handler.afterConnectionEstablished(session1)
        handler.afterConnectionEstablished(session2)

        val eventData = WebSocketEventData.ProgressUpdate(guildId = "123", message = "test")
        handler.broadcastToGuild("123", eventData)

        verify(session1).sendMessage(any())
        verify(session2, never()).sendMessage(any())
    }
}
