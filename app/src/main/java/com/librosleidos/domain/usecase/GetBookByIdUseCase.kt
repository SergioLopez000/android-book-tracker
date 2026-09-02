package com.librosleidos.domain.usecase

import com.librosleidos.domain.model.Book
import com.librosleidos.domain.port.BookRepository

/**
 * Task 2.5 (design: not-found returns null).
 */
class GetBookByIdUseCase(private val repository: BookRepository) {
    suspend operator fun invoke(id: Long): Book? = repository.findById(id)
}
