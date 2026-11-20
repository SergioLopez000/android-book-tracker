package com.librosleidos.backend.domain.model

/** Half-star rating, 0.5..5.0 in exact 0.5 steps (multiples of 0.5 are exact in IEEE-754). */
@JvmInline
value class Rating(val value: Float) {
    init {
        require(value in MIN..MAX) { "Rating must be between $MIN and $MAX" }
        require((value * 2) % 1f == 0f) { "Rating must be a 0.5 increment" }
    }

    companion object {
        const val MIN = 0.5f
        const val MAX = 5.0f
    }
}
