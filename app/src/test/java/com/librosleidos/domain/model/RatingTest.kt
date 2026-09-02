package com.librosleidos.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Task 2.1 (design D4): [Rating] enforces the half-star invariant at
 * construction — 0.5..5.0 inclusive, in exact 0.5 increments.
 * Spec: "Validate Half-Star Rating".
 */
class RatingTest {

    @Test
    fun `accepts a valid half-star value`() {
        val rating = Rating(3.5f)

        assertEquals(3.5f, rating.value)
    }

    @Test
    fun `accepts the lower boundary value`() {
        val rating = Rating(0.5f)

        assertEquals(0.5f, rating.value)
    }

    @Test
    fun `accepts the upper boundary value`() {
        val rating = Rating(5.0f)

        assertEquals(5.0f, rating.value)
    }

    @Test
    fun `rejects a value below the lower boundary`() {
        assertThrows(IllegalArgumentException::class.java) {
            Rating(0.0f)
        }
    }

    @Test
    fun `rejects a value above the upper boundary`() {
        assertThrows(IllegalArgumentException::class.java) {
            Rating(5.5f)
        }
    }

    @Test
    fun `rejects a non 0point5-step value within range`() {
        assertThrows(IllegalArgumentException::class.java) {
            Rating(3.7f)
        }
    }
}
