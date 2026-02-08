package dev.robothanzo.werewolf.service.impl

import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.listeners.RoleEventListener
import dev.robothanzo.werewolf.game.model.RoleEventType
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*

class RoleEventServiceImplTest {

    private val listener1: RoleEventListener = mock {
        on { getInterestedEvents() } doReturn listOf(RoleEventType.ON_NIGHT_START)
    }
    private val listener2: RoleEventListener = mock {
        on { getInterestedEvents() } doReturn listOf(RoleEventType.ON_DAY_START)
    }

    private lateinit var roleEventService: RoleEventServiceImpl

    @BeforeEach
    fun setUp() {
        roleEventService = RoleEventServiceImpl(listOf(listener1, listener2))
    }

    @Test
    fun `notifyListeners notifies interested listeners only`() {
        val session: Session = mock()
        val metadata = mapOf("test" to "data")

        roleEventService.notifyListeners(session, RoleEventType.ON_NIGHT_START, metadata)

        verify(listener1).onEvent(session, RoleEventType.ON_NIGHT_START, metadata)
        verify(listener2, never()).onEvent(any(), any(), any())
    }

    @Test
    fun `getListenersForEvent returns filtered list`() {
        val result = roleEventService.getListenersForEvent(RoleEventType.ON_NIGHT_START)

        assertEquals(1, result.size)
        assertEquals(listener1, result[0])
    }

    @Test
    fun `notifyListeners catches and prints exceptions from listeners`() {
        val session: Session = mock()
        whenever(listener1.onEvent(any(), any(), any())).thenThrow(RuntimeException("Test exception"))

        assertDoesNotThrow {
            roleEventService.notifyListeners(session, RoleEventType.ON_NIGHT_START, emptyMap())
        }

        verify(listener1).onEvent(any(), any(), any())
    }
}
