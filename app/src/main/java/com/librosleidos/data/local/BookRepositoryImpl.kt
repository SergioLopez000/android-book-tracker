package com.librosleidos.data.local

import com.librosleidos.domain.model.Book
import com.librosleidos.domain.model.BookSort
import com.librosleidos.domain.port.BookRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * [BookRepository] adapter backed by Room (design: Interfaces / Contracts).
 * Delegates sort selection to one of [BookDao]'s two explicit queries
 * (design D6) instead of building SQL dynamically.
 */
class BookRepositoryImpl(private val dao: BookDao) : BookRepository {

    override fun observeBooks(sort: BookSort, minRating: Float): Flow<List<Book>> {
        val entities = when (sort) {
            BookSort.DATE_FINISHED_DESC -> dao.observeByDateDesc(minRating)
            BookSort.RATING_DESC -> dao.observeByRatingDesc(minRating)
        }
        return entities.map { list -> list.map { it.toDomain() } }
    }

    override suspend fun findById(id: Long): Book? = dao.findById(id)?.toDomain()

    override suspend fun save(book: Book): Long {
        val generatedId = dao.upsert(book.toEntity())
        // Room's single-entity @Upsert returns the new row id on insert, or
        // -1 when the operation resolved to an update (no new id assigned).
        return if (generatedId != -1L) {
            generatedId
        } else {
            requireNotNull(book.id) {
                "Upsert reported an update (-1) but the book had no existing id"
            }
        }
    }

    override suspend fun delete(id: Long) = dao.deleteById(id)
}
