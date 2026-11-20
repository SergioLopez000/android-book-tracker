package com.librosleidos.backend.domain.model

/** Identity of the principal that owns books. Never blank; fits the `owner_id` column. */
@JvmInline
value class OwnerId(val value: String) {
    init {
        require(value.isNotBlank()) { "OwnerId must not be blank" }
        require(value.length <= MAX_LENGTH) { "OwnerId must be at most $MAX_LENGTH characters" }
    }

    companion object {
        const val MAX_LENGTH = 255
    }
}
