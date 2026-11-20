package com.librosleidos.testsupport

import com.librosleidos.backend.domain.model.Book
import com.librosleidos.backend.domain.model.BookDraft
import com.librosleidos.backend.domain.model.BookId
import com.librosleidos.backend.domain.model.OwnerId
import com.librosleidos.backend.domain.port.BookRepository
import com.librosleidos.backend.domain.usecase.InMemoryBookRepository
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

private const val LEAK = "SELECT secret FROM books at /srv/app/Repo.kt"

/** Replaces the JDBC adapter with an in-memory fake. Outside the scan root so only tests that import it get it. */
@TestConfiguration(proxyBeanMethods = false)
class FakeRepositoryConfig {
    @Bean @Primary
    fun inMemoryBookRepository(): BookRepository = InMemoryBookRepository()
}

/** A repository that always fails with a message full of internals, to prove they never reach the client. */
@TestConfiguration(proxyBeanMethods = false)
class FailingRepositoryConfig {
    @Bean
    @Primary
    fun failingBookRepository(): BookRepository = object : BookRepository {
        override fun create(owner: OwnerId, draft: BookDraft): Book = error(LEAK)

        override fun findById(owner: OwnerId, id: BookId): Book? = error(LEAK)

        override fun findAll(owner: OwnerId): List<Book> = throw IllegalStateException(LEAK)

        override fun update(owner: OwnerId, id: BookId, draft: BookDraft): Book? = error(LEAK)

        override fun delete(owner: OwnerId, id: BookId): Boolean = error(LEAK)
    }
}
