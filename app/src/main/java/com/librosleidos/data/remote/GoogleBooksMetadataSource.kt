package com.librosleidos.data.remote

import com.librosleidos.domain.model.BookMetadata
import com.librosleidos.domain.port.BookMetadataSource
import org.json.JSONObject

/**
 * DTO mirroring one entry of Google Books' `volumes` response `items` array
 * (design: Phase 4, task 4.2). Kept internal — only [GoogleBooksMetadataSource]
 * maps it to the domain [BookMetadata].
 */
internal data class GoogleBooksVolume(
    val title: String,
    val author: String?,
    val thumbnailUrl: String?,
)

/**
 * Task 4.2 (design D2/D3, spec "Fallback to Google Books"). Gated by a
 * non-blank API key (design D2: `BuildConfig.GOOGLE_BOOKS_API_KEY`, default
 * `""` from a git-ignored `local.properties`): a blank key means the
 * fallback is disabled and returns an empty result rather than making a
 * request or throwing — a fresh clone builds and tests green without it.
 */
class GoogleBooksMetadataSource(
    private val httpClient: HttpClient,
    private val apiKey: String,
) : BookMetadataSource {

    override suspend fun search(query: String): Result<List<BookMetadata>> {
        if (apiKey.isBlank()) return Result.success(emptyList())

        return runCatching {
            val encoded = encodeQueryParam(query)
            val url = "$VOLUMES_URL?q=$encoded&key=$apiKey"
            val body = httpClient.get(url)
            parseVolumes(body).map { it.toBookMetadata() }
        }
    }

    private fun parseVolumes(body: String): List<GoogleBooksVolume> =
        JSONObject(body).optJSONArray("items").mapObjects { item ->
            val volumeInfo = item.optJSONObject("volumeInfo")
            GoogleBooksVolume(
                title = volumeInfo?.optString("title").orEmpty(),
                author = volumeInfo?.optJSONArray("authors")?.optString(0),
                thumbnailUrl = volumeInfo?.optJSONObject("imageLinks")?.optString("thumbnail"),
            )
        }

    private fun GoogleBooksVolume.toBookMetadata() = BookMetadata(
        title = title,
        author = author ?: "",
        coverUrl = thumbnailUrl?.toSecureCoverUrl(),
    )

    // Threat matrix: coverUrl accepted only over https.
    private fun String.toSecureCoverUrl(): String? = when {
        isBlank() -> null
        startsWith("https://") -> this
        startsWith("http://") -> "https://" + removePrefix("http://")
        else -> null
    }

    private companion object {
        const val VOLUMES_URL = "https://www.googleapis.com/books/v1/volumes"
    }
}
