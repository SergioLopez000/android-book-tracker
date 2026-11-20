package com.librosleidos.backend.web

import com.librosleidos.backend.domain.model.BookId
import com.librosleidos.backend.domain.model.OwnerId
import com.librosleidos.backend.domain.usecase.CreateBookUseCase
import com.librosleidos.backend.domain.usecase.DeleteBookOutcome
import com.librosleidos.backend.domain.usecase.DeleteBookUseCase
import com.librosleidos.backend.domain.usecase.GetBookOutcome
import com.librosleidos.backend.domain.usecase.GetBookUseCase
import com.librosleidos.backend.domain.usecase.ListBooksUseCase
import com.librosleidos.backend.domain.usecase.UpdateBookOutcome
import com.librosleidos.backend.domain.usecase.UpdateBookUseCase
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Implements `openapi.yaml`. The owner always comes from the authenticated principal, never from the request. */
@RestController
@RequestMapping("/api/books")
class BooksController(
    private val createBook: CreateBookUseCase,
    private val listBooks: ListBooksUseCase,
    private val getBook: GetBookUseCase,
    private val updateBook: UpdateBookUseCase,
    private val deleteBook: DeleteBookUseCase
) {
    @PostMapping
    fun create(
        authentication: Authentication,
        @RequestBody request: BookRequest
    ): ResponseEntity<BookResponse> = ResponseEntity.status(HttpStatus.CREATED)
        .body(createBook(owner(authentication), request.toDraft()).toResponse())

    @GetMapping
    fun list(authentication: Authentication): List<BookResponse> =
        listBooks(owner(authentication)).map { it.toResponse() }

    @GetMapping("/{id}")
    fun get(authentication: Authentication, @PathVariable id: Long): ResponseEntity<BookResponse> =
        when (val outcome = getBook(owner(authentication), bookId(id))) {
            is GetBookOutcome.Found -> ResponseEntity.ok(outcome.book.toResponse())
            GetBookOutcome.NotFound -> throw BookNotFoundException()
        }

    @PutMapping("/{id}")
    fun update(
        authentication: Authentication,
        @PathVariable id: Long,
        @RequestBody request: BookRequest
    ): ResponseEntity<BookResponse> =
        when (val outcome = updateBook(owner(authentication), bookId(id), request.toDraft())) {
            is UpdateBookOutcome.Updated -> ResponseEntity.ok(outcome.book.toResponse())
            UpdateBookOutcome.NotFound -> throw BookNotFoundException()
        }

    @DeleteMapping("/{id}")
    fun delete(authentication: Authentication, @PathVariable id: Long): ResponseEntity<Void> =
        when (deleteBook(owner(authentication), bookId(id))) {
            DeleteBookOutcome.Deleted -> ResponseEntity.noContent().build()
            DeleteBookOutcome.NotFound -> throw BookNotFoundException()
        }

    private fun owner(authentication: Authentication) = OwnerId(authentication.name)

    private fun bookId(id: Long) = try {
        BookId(id)
    } catch (e: IllegalArgumentException) {
        throw InvalidBookRequestException(e)
    }
}

/** A missing book and another owner's book are indistinguishable by design. */
class BookNotFoundException : RuntimeException()
