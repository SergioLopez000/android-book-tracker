package com.librosleidos.domain.usecase

import com.librosleidos.domain.model.BookMetadata
import com.librosleidos.domain.port.BookMetadataSource

/**
 * Task 2.7 (design: trim, >=2 chars, source failure -> empty result).
 * Spec: "Graceful Degradation" — never blocks the manual entry form.
 *
 * A short query is rejected before the source is ever called, and a source
 * failure collapses to an empty result rather than propagating: at this
 * layer the wired [BookMetadataSource] is `ChainedMetadataSource` (design
 * D3, Phase 4), which already exhausted every provider by the time it
 * fails, so there is nothing left to fall back to here.
 */
class SearchBookMetadataUseCase(private val source: BookMetadataSource) {

    suspend operator fun invoke(query: String): List<BookMetadata> {
        val trimmed = query.trim()
        if (trimmed.length < MIN_QUERY_LENGTH) return emptyList()
        return source.search(trimmed).getOrElse { emptyList() }
    }

    private companion object {
        const val MIN_QUERY_LENGTH = 2
    }
}
