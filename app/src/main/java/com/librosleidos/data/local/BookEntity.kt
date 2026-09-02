package com.librosleidos.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room row for a persisted [com.librosleidos.domain.model.Book] (design:
 * Interfaces / Contracts). `dateFinishedEpochDay` stores `LocalDate` as an
 * indexable `Long` (design D5) — minSdk 26 needs no Room `TypeConverter`.
 */
@Entity(tableName = "books", indices = [Index("dateFinishedEpochDay")])
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val author: String,
    val coverUrl: String?,
    val rating: Float,
    val review: String,
    val dateFinishedEpochDay: Long,
)
