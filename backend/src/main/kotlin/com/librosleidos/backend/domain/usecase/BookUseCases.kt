package com.librosleidos.backend.domain.usecase

import com.librosleidos.backend.domain.model.Book
import com.librosleidos.backend.domain.model.BookDraft
import com.librosleidos.backend.domain.model.BookId
import com.librosleidos.backend.domain.model.OwnerId
import com.librosleidos.backend.domain.port.BookRepository

class CreateBookUseCase(private val repository: BookRepository) {
    operator fun invoke(owner: OwnerId, draft: BookDraft): Book = repository.create(owner, draft)
}

class GetBookUseCase(private val repository: BookRepository) {
    operator fun invoke(owner: OwnerId, id: BookId): GetBookOutcome =
        repository.findById(owner, id)?.let { GetBookOutcome.Found(it) } ?: GetBookOutcome.NotFound
}

class ListBooksUseCase(private val repository: BookRepository) {
    operator fun invoke(owner: OwnerId): List<Book> = repository.findAll(owner)
}

class UpdateBookUseCase(private val repository: BookRepository) {
    operator fun invoke(owner: OwnerId, id: BookId, draft: BookDraft): UpdateBookOutcome =
        repository.update(owner, id, draft)?.let { UpdateBookOutcome.Updated(it) }
            ?: UpdateBookOutcome.NotFound
}

class DeleteBookUseCase(private val repository: BookRepository) {
    operator fun invoke(owner: OwnerId, id: BookId): DeleteBookOutcome =
        if (repository.delete(owner, id)) DeleteBookOutcome.Deleted else DeleteBookOutcome.NotFound
}
