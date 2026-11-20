package com.librosleidos.backend.config

import com.librosleidos.backend.data.jdbc.JdbcBookRepository
import com.librosleidos.backend.domain.port.BookRepository
import com.librosleidos.backend.domain.usecase.CreateBookUseCase
import com.librosleidos.backend.domain.usecase.DeleteBookUseCase
import com.librosleidos.backend.domain.usecase.GetBookUseCase
import com.librosleidos.backend.domain.usecase.ListBooksUseCase
import com.librosleidos.backend.domain.usecase.UpdateBookUseCase
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.simple.JdbcClient

/** Wires the framework-free domain to its adapters. The domain itself carries no Spring annotations. */
@Configuration(proxyBeanMethods = false)
class UseCaseConfig {
    @Bean fun bookRepository(jdbc: JdbcClient): BookRepository = JdbcBookRepository(jdbc)

    @Bean fun createBookUseCase(repository: BookRepository) = CreateBookUseCase(repository)

    @Bean fun getBookUseCase(repository: BookRepository) = GetBookUseCase(repository)

    @Bean fun listBooksUseCase(repository: BookRepository) = ListBooksUseCase(repository)

    @Bean fun updateBookUseCase(repository: BookRepository) = UpdateBookUseCase(repository)

    @Bean fun deleteBookUseCase(repository: BookRepository) = DeleteBookUseCase(repository)
}
