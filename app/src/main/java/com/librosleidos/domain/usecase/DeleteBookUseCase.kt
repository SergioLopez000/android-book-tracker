package com.librosleidos.domain.usecase

import com.librosleidos.domain.port.BookRepository

/**
 * Task 2.6 (design: delegates delete by id). Spec: "Delete Book Entry".
 */
class DeleteBookUseCase(private val repository: BookRepository) {
    suspend operator fun invoke(id: Long) = repository.delete(id)
}
