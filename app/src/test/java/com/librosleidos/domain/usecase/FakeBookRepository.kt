package com.librosleidos.domain.usecase

import com.librosleidos.domain.model.Book
import com.librosleidos.domain.model.BookSort
import com.librosleidos.domain.port.BookRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [BookRepository] test double shared by the domain use-case
 * tests (design: pure JVM, no Android — Phase 3's Room-backed
 * implementation is a separate, later concern).
 */
class FakeBookRepository(seed: List<Book> = emptyList()) : BookRepository {

    private val books = MutableStateFlow(seed)
    private var nextId = (seed.mapNotNull { it.id }.maxOrNull() ?: 0L) + 1L

    var lastRequestedSort: BookSort? = null
        private set
    var lastRequestedMinRating: Float? = null
        private set
    var lastDeletedId: Long? = null
        private set

    override fun observeBooks(sort: BookSort, minRating: Float): Flow<List<Book>> {
        lastRequestedSort = sort
        lastRequestedMinRating = minRating
        return books.map { current -> current.filter { it.rating.value >= minRating } }
    }

    override suspend fun findById(id: Long): Book? = books.value.find { it.id == id }

    override suspend fun save(book: Book): Long {
        val id = book.id ?: nextId++
        val saved = book.copy(id = id)
        books.value = books.value.filter { it.id != id } + saved
        return id
    }

    override suspend fun delete(id: Long) {
        lastDeletedId = id
        books.value = books.value.filter { it.id != id }
    }
}
