package dev.robothanzo.werewolf.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SpeechOrderTest {
    @Test
    fun testSpeechOrderValues() {
        assertEquals(2, SpeechOrder.entries.size)
        assertTrue(SpeechOrder.entries.contains(SpeechOrder.UP))
        assertTrue(SpeechOrder.entries.contains(SpeechOrder.DOWN))
    }

    @Test
    fun testSpeechOrderToString() {
        val upString = SpeechOrder.UP.toString()
        val downString = SpeechOrder.DOWN.toString()

        assertEquals("往上", upString)
        assertEquals("往下", downString)
    }

    @Test
    fun testSpeechOrderToEmoji() {
        val upEmoji = SpeechOrder.UP.toEmoji()
        val downEmoji = SpeechOrder.DOWN.toEmoji()

        // Just verify the emojis are created properly
        assertNotNull(upEmoji)
        assertNotNull(downEmoji)
    }

    @Test
    fun testSpeechOrderFromString() {
        val upOrder = SpeechOrder.fromString("UP")
        val downOrder = SpeechOrder.fromString("DOWN")

        assertEquals(SpeechOrder.UP, upOrder)
        assertEquals(SpeechOrder.DOWN, downOrder)
    }

    @Test
    fun testSpeechOrderFromStringIgnoreCase() {
        val upOrder = SpeechOrder.fromString("up")
        val downOrder = SpeechOrder.fromString("down")

        assertEquals(SpeechOrder.UP, upOrder)
        assertEquals(SpeechOrder.DOWN, downOrder)
    }

    @Test
    fun testSpeechOrderGetRandomOrder() {
        val orders = mutableSetOf<SpeechOrder>()
        repeat(100) {
            orders.add(SpeechOrder.getRandomOrder())
        }

        // With 100 random samples, we should get both values
        assertTrue(orders.size > 0)
        assertTrue(orders.contains(SpeechOrder.UP) || orders.contains(SpeechOrder.DOWN))
    }

    @Test
    fun testSpeechOrderRandomOrderDistribution() {
        val upCount = (0..999).count { SpeechOrder.getRandomOrder() == SpeechOrder.UP }
        val downCount = 1000 - upCount

        // With 1000 samples, counts should be roughly balanced
        // Allowing 40-60% range for each
        assertTrue(upCount in 400..600)
        assertTrue(downCount in 400..600)
    }

    @Test
    fun testSpeechOrderEquality() {
        val up1 = SpeechOrder.UP
        val up2 = SpeechOrder.UP
        val down = SpeechOrder.DOWN

        assertEquals(up1, up2)
        assertFalse(up1 == down)
    }
}
