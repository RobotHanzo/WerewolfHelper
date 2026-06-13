package dev.robothanzo.werewolf.game.speech

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpeechServiceTest {

    private val service = SpeechService()

    @Test
    fun `down order ascends and wraps from the starting seat`() {
        val order = service.buildOrder(listOf(1, 2, 3, 5, 8), from = 3, direction = SpeechDirection.DOWN)
        assertEquals(listOf(3, 5, 8, 1, 2), order)
    }

    @Test
    fun `up order descends and wraps from the starting seat`() {
        val order = service.buildOrder(listOf(1, 2, 3, 5, 8), from = 3, direction = SpeechDirection.UP)
        assertEquals(listOf(3, 2, 1, 8, 5), order)
    }

    @Test
    fun `order contains only the alive seats exactly once`() {
        val order = service.buildOrder(listOf(2, 4, 6), from = 4, direction = SpeechDirection.DOWN)
        assertEquals(listOf(4, 6, 2), order)
        assertEquals(3, order.toSet().size)
    }

    @Test
    fun `missing start seat falls back to the first alive seat`() {
        val order = service.buildOrder(listOf(1, 2, 3), from = 99, direction = SpeechDirection.DOWN)
        assertEquals(listOf(1, 2, 3), order)
    }

    @Test
    fun `advance walks the queue then completes`() {
        val flow = SpeechFlow(order = listOf(3, 5, 8), direction = SpeechDirection.DOWN, from = 3)
        assertEquals(3, service.current(flow))
        assertEquals(5, service.advance(flow))
        assertEquals(8, service.advance(flow))
        assertNull(service.advance(flow))
        assertTrue(service.isComplete(flow))
    }

    @Test
    fun `interrupt carries at a majority of alive players`() {
        val flow = SpeechFlow(order = listOf(1, 2, 3, 4, 5), direction = SpeechDirection.DOWN, from = 1)
        // 5 alive → majority threshold = 3. Speaker is seat 1; their vote doesn't count.
        assertFalse(service.registerInterrupt(flow, voter = 2, aliveCount = 5))
        assertFalse(service.registerInterrupt(flow, voter = 3, aliveCount = 5))
        assertTrue(service.registerInterrupt(flow, voter = 4, aliveCount = 5))
    }

    @Test
    fun `the speaker cannot vote to interrupt themselves`() {
        val flow = SpeechFlow(order = listOf(1, 2, 3), direction = SpeechDirection.DOWN, from = 1)
        assertFalse(service.registerInterrupt(flow, voter = 1, aliveCount = 3))
        assertTrue(flow.interruptVotes.isEmpty())
    }

    @Test
    fun `majority threshold is a strict majority`() {
        assertEquals(2, service.majorityThreshold(3))
        assertEquals(3, service.majorityThreshold(5))
        assertEquals(4, service.majorityThreshold(6))
    }
}
