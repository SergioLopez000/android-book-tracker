package com.librosleidos.domain.port

import com.librosleidos.domain.model.Book
import com.librosleidos.domain.model.BookSort
import kotlinx.coroutines.flow.Flow

/**
 * Persistence port for [Book]s (design: Interfaces / Contracts). Implemented
 * by `data/local/BookRepositoryImpl` in Phase 3 against Room.
 *
 * Task 4.3 (design D8): `fromEpochDay` is a nullable lower bound over
 * `dateFinished` — `null` means no date-range constraint ("histórico").
 */
interface BookRepository {
    fun observeBooks(sort: BookSort, minRating: Float, fromEpochDay: Long?): Flow<List<Book>>
    suspend fun findById(id: Long): Book?
    suspend fun save(book: Book): Long
    suspend fun delete(id: Long)
}
