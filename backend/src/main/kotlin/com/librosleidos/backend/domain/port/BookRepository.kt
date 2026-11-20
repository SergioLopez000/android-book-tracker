package com.librosleidos.backend.domain.port

import com.librosleidos.backend.domain.model.Book
import com.librosleidos.backend.domain.model.BookDraft
import com.librosleidos.backend.domain.model.BookId
import com.librosleidos.backend.domain.model.OwnerId

/** Every operation requires the owner, so no caller can reach a book it does not own. */
interface BookRepository {
    fun create(owner: OwnerId, draft: BookDraft): Book

    fun findById(owner: OwnerId, id: BookId): Book?

    fun findAll(owner: OwnerId): List<Book>

    fun update(owner: OwnerId, id: BookId, draft: BookDraft): Book?

    fun delete(owner: OwnerId, id: BookId): Boolean
}
