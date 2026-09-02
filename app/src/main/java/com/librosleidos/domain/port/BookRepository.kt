package com.librosleidos.domain.port

import com.librosleidos.domain.model.Book
import com.librosleidos.domain.model.BookSort
import kotlinx.coroutines.flow.Flow

/**
 * Persistence port for [Book]s (design: Interfaces / Contracts). Implemented
 * by `data/local/BookRepositoryImpl` in Phase 3 against Room.
 */
interface BookRepository {
    fun observeBooks(sort: BookSort, minRating: Float): Flow<List<Book>>
    suspend fun findById(id: Long): Book?
    suspend fun save(book: Book): Long
    suspend fun delete(id: Long)
}
