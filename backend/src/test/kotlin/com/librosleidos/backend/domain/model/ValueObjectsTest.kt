package com.librosleidos.backend.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ValueObjectsTest {
    @Test
    fun `owner id keeps a non-blank value`() {
        assertEquals("user-1", OwnerId("user-1").value)
    }

    @Test
    fun `owner id rejects empty and whitespace`() {
        assertFailsWith<IllegalArgumentException> { OwnerId("") }
        assertFailsWith<IllegalArgumentException> { OwnerId("   ") }
    }

    @Test
    fun `owner id rejects values longer than the column`() {
        OwnerId("a".repeat(OwnerId.MAX_LENGTH))
        assertFailsWith<IllegalArgumentException> { OwnerId("a".repeat(OwnerId.MAX_LENGTH + 1)) }
    }

    @Test
    fun `book id must be positive`() {
        assertEquals(1L, BookId(1).value)
        assertFailsWith<IllegalArgumentException> { BookId(0) }
        assertFailsWith<IllegalArgumentException> { BookId(-5) }
    }

    @Test
    fun `rating accepts half steps in range`() {
        listOf(0.5f, 1f, 2.5f, 4.5f, 5f).forEach { assertEquals(it, Rating(it).value) }
    }

    @Test
    fun `rating rejects out of range and non half steps`() {
        listOf(0f, 0.4f, 5.5f, 0.75f, 3.1f, -1f).forEach {
            assertFailsWith<IllegalArgumentException>("rating $it") { Rating(it) }
        }
    }
}
