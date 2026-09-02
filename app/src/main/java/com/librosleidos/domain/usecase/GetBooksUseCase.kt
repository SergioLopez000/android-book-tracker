package com.librosleidos.domain.usecase

import com.librosleidos.domain.model.Book
import com.librosleidos.domain.model.BookSort
import com.librosleidos.domain.port.BookRepository
import kotlinx.coroutines.flow.Flow

/**
 * Task 2.5 (design: delegates sort/minRating to the repository).
 * Spec: "List Read Books", "Sort Book List", "Filter by Minimum Rating".
 */
class GetBooksUseCase(private val repository: BookRepository) {
    operator fun invoke(sort: BookSort, minRating: Float): Flow<List<Book>> =
        repository.observeBooks(sort, minRating)
}
