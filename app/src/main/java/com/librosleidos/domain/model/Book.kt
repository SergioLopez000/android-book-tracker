package com.librosleidos.domain.model

import java.time.LocalDate

/**
 * A persisted (or about-to-be-persisted) read book.
 *
 * `id == null` means the book has not been saved yet (design: Interfaces /
 * Contracts). `rating` is a validated [Rating] — an instance of [Book] can
 * never carry an invalid rating.
 */
data class Book(
    val id: Long?,
    val title: String,
    val author: String,
    val coverUrl: String?,
    val rating: Rating,
    val review: String,
    val dateFinished: LocalDate,
)
