package com.librosleidos.backend.domain.usecase

import com.librosleidos.backend.domain.model.Book
import com.librosleidos.backend.domain.model.BookDraft
import com.librosleidos.backend.domain.model.BookId
import com.librosleidos.backend.domain.model.OwnerId
import com.librosleidos.backend.domain.port.BookRepository

/** In-memory fake that, like the real adapter, only ever matches on (owner, id). */
class InMemoryBookRepository : BookRepository {
    private val rows = linkedMapOf<Long, Pair<OwnerId, Book>>()
    private var next = 1L

    override fun create(owner: OwnerId, draft: BookDraft): Book {
        val book = draft.toBook(BookId(next++))
        rows[book.id.value] = owner to book
        return book
    }

    override fun findById(owner: OwnerId, id: BookId): Book? = rows[id.value]?.takeIf {
        it.first ==
            owner
    }?.second

    override fun findAll(owner: OwnerId): List<Book> = rows.values.filter {
        it.first == owner
    }.map { it.second }

    override fun update(owner: OwnerId, id: BookId, draft: BookDraft): Book? {
        if (findById(owner, id) == null) return null
        return draft.toBook(id).also { rows[id.value] = owner to it }
    }

    override fun delete(owner: OwnerId, id: BookId): Boolean =
        findById(owner, id)?.let { rows.remove(id.value) } != null

    private fun BookDraft.toBook(id: BookId) =
        Book(id, title, author, coverUrl, rating, review, dateFinished)
}
