package com.librosleidos.domain.port

import com.librosleidos.domain.model.BookMetadata

/**
 * Metadata lookup port (design: Interfaces / Contracts). Implemented by
 * `data/remote/OpenLibraryMetadataSource`, `GoogleBooksMetadataSource` and
 * their `ChainedMetadataSource` composite in Phase 4.
 *
 * Returns [Result] rather than a bare list so callers can distinguish a
 * provider failure from a genuine no-match (design D3).
 */
interface BookMetadataSource {
    suspend fun search(query: String): Result<List<BookMetadata>>
}
