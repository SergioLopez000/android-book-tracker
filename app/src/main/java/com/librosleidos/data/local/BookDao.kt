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
 */
@Dao
interface BookDao {

    @Query("SELECT * FROM books WHERE rating >= :minRating ORDER BY dateFinishedEpochDay DESC")
    fun observeByDateDesc(minRating: Float): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE rating >= :minRating ORDER BY rating DESC")
    fun observeByRatingDesc(minRating: Float): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun findById(id: Long): BookEntity?

    @Upsert
    suspend fun upsert(entity: BookEntity): Long

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteById(id: Long)
}
