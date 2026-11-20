package com.librosleidos.backend.domain.model

import java.time.LocalDate

/** A persisted book. It carries no owner: ownership is enforced by the port. */
data class Book(
    val id: BookId,
    val title: String,
    val author: String,
    val coverUrl: String?,
    val rating: Rating,
    val review: String,
    val dateFinished: LocalDate
)
