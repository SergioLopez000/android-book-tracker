package com.librosleidos.backend.domain.usecase

import com.librosleidos.backend.domain.model.Book

/** A book of another owner is indistinguishable from a missing one. */
sealed interface GetBookOutcome {
    data class Found(val book: Book) : GetBookOutcome

    data object NotFound : GetBookOutcome
}

sealed interface UpdateBookOutcome {
    data class Updated(val book: Book) : UpdateBookOutcome

    data object NotFound : UpdateBookOutcome
}

sealed interface DeleteBookOutcome {
    data object Deleted : DeleteBookOutcome

    data object NotFound : DeleteBookOutcome
}
