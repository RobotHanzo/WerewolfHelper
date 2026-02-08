package dev.robothanzo.werewolf.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ParseUtilsTest {

    @Test
    fun `parseLong with Number should return Long`() {
        assertEquals(123L, parseLong(123))
        assertEquals(123L, parseLong(123.0))
        assertEquals(123L, parseLong(123L))
    }

    @Test
    fun `parseLong with Long String should return Long`() {
        assertEquals(123L, parseLong("123"))
    }

    @Test
    fun `parseLong with invalid String should return null`() {
        assertNull(parseLong("abc"))
    }

    @Test
    fun `parseLong with null should return null`() {
        assertNull(parseLong(null))
    }

    @Test
    fun `parseLong with other types should return null`() {
        assertNull(parseLong(Any()))
        assertNull(parseLong(listOf(1)))
    }
}
