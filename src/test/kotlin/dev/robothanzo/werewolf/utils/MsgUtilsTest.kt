package dev.robothanzo.werewolf.utils

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MsgUtilsTest {
    @Test
    fun testGetSortOrderWithNumbersOnly() {
        val order1 = MsgUtils.getSortOrder("123")
        val order2 = MsgUtils.getSortOrder("45")
        val order3 = MsgUtils.getSortOrder("6")

        assertEquals(123 shl 8, order1)
        assertEquals(45 shl 8, order2)
        assertEquals(6 shl 8, order3)

        assertTrue(order1 > order2)
        assertTrue(order2 > order3)
    }

    @Test
    fun testGetSortOrderWithLetters() {
        val order1 = MsgUtils.getSortOrder("10a")
        val order2 = MsgUtils.getSortOrder("10b")
        val order3 = MsgUtils.getSortOrder("10")

        val expected1 = (10 shl 8) or 'a'.code
        val expected2 = (10 shl 8) or 'b'.code
        val expected3 = 10 shl 8

        assertEquals(expected1, order1)
        assertEquals(expected2, order2)
        assertEquals(expected3, order3)

        assertTrue(order2 > order1)
        assertTrue(order1 > order3)
    }

    @Test
    fun testGetSortOrderWithInvalidInput() {
        val order = MsgUtils.getSortOrder("abc")
        assertEquals(0, order)
    }

    @Test
    fun testGetSortOrderWithSpecialCharacters() {
        val order = MsgUtils.getSortOrder("12-34")
        assertEquals(0, order)
    }

    @Test
    fun testRandomColorIsValid() {
        val color1 = MsgUtils.randomColor
        val color2 = MsgUtils.randomColor

        // Colors should have valid values
        assertNotNull(color1)
        assertNotNull(color2)
        assertNotNull(color1.rgb)
        assertNotNull(color2.rgb)
    }

    @Test
    fun testGetAlphaNumComparator() {
        val unsorted = listOf("10", "2", "1a", "1", "20b", "5")
        val sorted = unsorted.sortedWith(MsgUtils.getAlphaNumComparator())

        val expectedOrder = listOf("1", "1a", "2", "5", "10", "20b")
        assertEquals(expectedOrder, sorted)
    }

    @Test
    fun testGetAlphaNumComparatorWithMoreComplex() {
        val unsorted = listOf("100z", "100a", "50", "100", "1a", "1")
        val sorted = unsorted.sortedWith(MsgUtils.getAlphaNumComparator())

        val expectedOrder = listOf("1", "1a", "50", "100", "100a", "100z")
        assertEquals(expectedOrder, sorted)
    }

    @Test
    fun testSpreadButtonsAcrossActionRowsEmpty() {
        val buttons = emptyList<org.junit.jupiter.api.extension.ExtendWith>()
        val result = MsgUtils.spreadButtonsAcrossActionRows(emptyList<net.dv8tion.jda.api.components.buttons.Button>())
        assertTrue(result.isEmpty())
    }

    @Test
    fun testSpreadButtonsAcrossActionRowsSingleButton() {
        val button = net.dv8tion.jda.api.components.buttons.Button.primary("test", "Test")
        val buttons = listOf(button)
        val result = MsgUtils.spreadButtonsAcrossActionRows(buttons)

        assertEquals(1, result.size)
        assertEquals(1, result[0].components.size)
    }

    @Test
    fun testSpreadButtonsAcrossActionRowsFiveButtons() {
        val buttons = (1..5).map {
            net.dv8tion.jda.api.components.buttons.Button.primary("test$it", "Test $it")
        }
        val result = MsgUtils.spreadButtonsAcrossActionRows(buttons)

        assertEquals(1, result.size)
        assertEquals(5, result[0].components.size)
    }

    @Test
    fun testSpreadButtonsAcrossActionRowsSixButtons() {
        val buttons = (1..6).map {
            net.dv8tion.jda.api.components.buttons.Button.primary("test$it", "Test $it")
        }
        val result = MsgUtils.spreadButtonsAcrossActionRows(buttons)

        assertEquals(2, result.size)
        assertEquals(5, result[0].components.size)
        assertEquals(1, result[1].components.size)
    }

    @Test
    fun testSpreadButtonsAcrossActionRowsTenButtons() {
        val buttons = (1..10).map {
            net.dv8tion.jda.api.components.buttons.Button.primary("test$it", "Test $it")
        }
        val result = MsgUtils.spreadButtonsAcrossActionRows(buttons)

        assertEquals(2, result.size)
        assertEquals(5, result[0].components.size)
        assertEquals(5, result[1].components.size)
    }

    @Test
    fun testSpreadButtonsAcrossActionRowsElevenButtons() {
        val buttons = (1..11).map {
            net.dv8tion.jda.api.components.buttons.Button.primary("test$it", "Test $it")
        }
        val result = MsgUtils.spreadButtonsAcrossActionRows(buttons)

        assertEquals(3, result.size)
        assertEquals(5, result[0].components.size)
        assertEquals(5, result[1].components.size)
        assertEquals(1, result[2].components.size)
    }
}
