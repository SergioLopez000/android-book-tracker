package com.librosleidos.data.local

import com.librosleidos.domain.model.Book
import com.librosleidos.domain.model.Rating
import java.time.LocalDate

/**
 * Maps between [Book] and [BookEntity] (design D5): `dateFinished` round-
 * trips through `dateFinishedEpochDay` via `LocalDate.toEpochDay()` /
 * `LocalDate.ofEpochDay()` — minSdk 26 needs no Room `TypeConverter`.
 */
fun Book.toEntity(): BookEntity = BookEntity(
    id = id ?: 0,
    title = title,
    author = author,
    coverUrl = coverUrl,
    rating = rating.value,
    review = review,
    dateFinishedEpochDay = dateFinished.toEpochDay(),
)

fun BookEntity.toDomain(): Book = Book(
    id = id,
    title = title,
    author = author,
    coverUrl = coverUrl,
    rating = Rating(rating),
    review = review,
    dateFinished = LocalDate.ofEpochDay(dateFinishedEpochDay),
)
