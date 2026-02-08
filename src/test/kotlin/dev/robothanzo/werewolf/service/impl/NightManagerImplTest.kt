package dev.robothanzo.werewolf.service.impl

import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.service.GameSessionService
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.*

class NightManagerImplTest {

    private val gameSessionService: GameSessionService = mock()
    private lateinit var nightManager: NightManagerImpl

    @BeforeEach
    fun setUp() {
        nightManager = NightManagerImpl(gameSessionService)
    }

    @Test
    fun `notifyPhaseUpdate emits guildId to flow`() = runBlocking {
        val guildId = 123L
        val flow = nightManager.getUpdateFlow()

        val deferred = async {
            flow.first { it == guildId }
        }

        yield()
        nightManager.notifyPhaseUpdate(guildId)

        assertEquals(guildId, withTimeout(1000) { deferred.await() })
    }

    @Test
    fun `waitForPhase returns session`() = runBlocking {
        val guildId = 123L
        val session = Session().apply { this.guildId = guildId }
        whenever(gameSessionService.getSession(guildId)).thenReturn(Optional.of(session))

        val result = nightManager.waitForPhase(guildId, 1000)

        assertEquals(session, result)
    }

    @Test
    fun `waitForPhase throws exception if session not found`() {
        val guildId = 123L
        whenever(gameSessionService.getSession(guildId)).thenReturn(Optional.empty())

        assertThrows(Exception::class.java) {
            runBlocking { nightManager.waitForPhase(guildId, 1000) }
        }
    }
}
