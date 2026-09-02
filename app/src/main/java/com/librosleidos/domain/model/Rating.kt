package com.librosleidos.domain.model

/**
 * A half-star book rating, 0.5..5.0 inclusive, in exact 0.5 increments.
 *
 * Validated at construction (design D4) so an invalid `Rating` can never
 * exist once constructed — callers that accept raw, possibly-invalid input
 * (e.g. [com.librosleidos.domain.usecase.SaveBookUseCase]) must catch the
 * thrown [IllegalArgumentException] and turn it into a domain-level
 * validation result themselves.
 *
 * Multiples of 0.5 are exact in IEEE-754, so the step check needs no
 * epsilon comparison.
 */
@JvmInline
value class Rating(val value: Float) {

    init {
        require(value in MIN..MAX) {
            "Rating must be between $MIN and $MAX, was $value"
        }
        require((value * 2) % 1f == 0f) {
            "Rating must be a 0.5 increment, was $value"
        }
    }

    companion object {
        const val MIN = 0.5f
        const val MAX = 5.0f
    }
}
