package com.librosleidos.data.remote

import com.librosleidos.domain.model.BookMetadata
import com.librosleidos.domain.port.BookMetadataSource

/**
 * Task 4.3 (design D3). Composite adapter: tries [primary] first, advancing
 * to [fallback] only when the primary source fails OR returns no matches.
 * Never advances once the primary already has at least one match (spec:
 * "Search Open Library First"). The wired instance is
 * `ChainedMetadataSource(OpenLibraryMetadataSource(...), GoogleBooksMetadataSource(...))`.
 *
 * Total failure (both sources fail) is left as [Result.failure] here — it
 * is [com.librosleidos.domain.usecase.SearchBookMetadataUseCase] (already
 * built, Phase 2) that collapses any source failure to an empty result, so
 * "Graceful Degradation" (spec) is satisfied end to end without this
 * composite needing to swallow the failure itself.
 */
class ChainedMetadataSource(
    private val primary: BookMetadataSource,
    private val fallback: BookMetadataSource,
) : BookMetadataSource {

    override suspend fun search(query: String): Result<List<BookMetadata>> {
        val primaryResult = primary.search(query)
        if (primaryResult.getOrNull()?.isNotEmpty() == true) return primaryResult
        return fallback.search(query)
    }
}
