package com.librosleidos.data.remote

import com.librosleidos.domain.model.BookMetadata
import com.librosleidos.domain.port.BookMetadataSource
import org.json.JSONObject

/**
 * DTO mirroring one entry of Open Library's `search.json` `docs` array
 * (design: Phase 4, task 4.1). Kept internal — only [OpenLibraryMetadataSource]
 * maps it to the domain [BookMetadata].
 */
internal data class OpenLibraryDoc(
    val title: String,
    val authorName: String?,
    val coverId: Int?,
)

/**
 * Task 4.1 (design D3, spec "Search Open Library First"). Primary metadata
 * source: no API key required. Sends a courtesy `User-Agent` header per the
 * research findings (anon rate limit ~1 req/s, 3 req/s with a descriptive
 * `User-Agent`). A transport or parse failure surfaces as [Result.failure]
 * rather than throwing — [ChainedMetadataSource] (task 4.3) decides whether
 * to advance to the fallback.
 */
class OpenLibraryMetadataSource(
    private val httpClient: HttpClient,
    private val userAgent: String = "LibrosLeidos/1.0 (contact: sergio.lopezm@sngular.com)",
) : BookMetadataSource {

    override suspend fun search(query: String): Result<List<BookMetadata>> = runCatching {
        val encoded = encodeQueryParam(query)
        val url = "$SEARCH_URL?q=$encoded&fields=title,author_name,cover_i&limit=20"
        val body = httpClient.get(url, headers = mapOf("User-Agent" to userAgent))
        parseDocs(body).map { it.toBookMetadata() }
    }

    private fun parseDocs(body: String): List<OpenLibraryDoc> =
        JSONObject(body).optJSONArray("docs").mapObjects { doc ->
            OpenLibraryDoc(
                title = doc.optString("title"),
                authorName = doc.optJSONArray("author_name")?.optString(0),
                coverId = doc.optInt("cover_i", NO_COVER).takeIf { it != NO_COVER },
            )
        }

    private fun OpenLibraryDoc.toBookMetadata() = BookMetadata(
        title = title,
        author = authorName ?: "",
        coverUrl = coverId?.let { "https://covers.openlibrary.org/b/id/$it-M.jpg" },
    )

    private companion object {
        const val SEARCH_URL = "https://openlibrary.org/search.json"
        const val NO_COVER = -1
    }
}
