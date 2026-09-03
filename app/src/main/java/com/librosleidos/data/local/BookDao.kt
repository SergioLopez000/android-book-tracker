package com.librosleidos.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for [BookEntity] (design: Interfaces / Contracts, D6). Two
 * explicit sort queries instead of `ORDER BY CASE` keep the sort SQL
 * compile-checked by Room. Both apply the `minRating` filter (spec:
 * "Sort Book List", "Filter by Minimum Rating").
 *
 * Task 4.5 (design D8): both queries also apply a nullable-bind
 * `fromEpochDay` lower bound — `(:fromEpochDay IS NULL OR
 * dateFinishedEpochDay >= :fromEpochDay)` — AND-combined with `minRating`
 * (spec: "Filter by Date Range", "Combine Date-Range and Minimum-Rating
 * Filters"). One nullable param collapses the three date buckets instead
 * of tripling the number of explicit queries.
 */
@Dao
interface BookDao {

    @Query(
        "SELECT * FROM books WHERE rating >= :minRating " +
            "AND (:fromEpochDay IS NULL OR dateFinishedEpochDay >= :fromEpochDay) " +
            "ORDER BY dateFinishedEpochDay DESC",
    )
    fun observeByDateDesc(minRating: Float, fromEpochDay: Long?): Flow<List<BookEntity>>

    @Query(
        "SELECT * FROM books WHERE rating >= :minRating " +
            "AND (:fromEpochDay IS NULL OR dateFinishedEpochDay >= :fromEpochDay) " +
            "ORDER BY rating DESC",
    )
    fun observeByRatingDesc(minRating: Float, fromEpochDay: Long?): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun findById(id: Long): BookEntity?

    @Upsert
    suspend fun upsert(entity: BookEntity): Long

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteById(id: Long)
}
