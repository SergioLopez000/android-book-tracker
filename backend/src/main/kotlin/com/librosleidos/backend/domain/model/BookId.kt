package com.librosleidos.backend.domain.model

@JvmInline
value class BookId(val value: Long) {
    init {
        require(value > 0) { "BookId must be positive" }
    }
}
