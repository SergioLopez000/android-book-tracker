package com.librosleidos.data.remote

/**
 * Minimal outbound HTTP port (Phase 4). Kept deliberately small so
 * [OpenLibraryMetadataSource] and [GoogleBooksMetadataSource] are testable
 * against a fake implementation — no real network access in unit tests, and
 * no HTTP framework dependency for a single-user, low-volume lookup feature
 * (see apply-progress PR4 for the "why not Retrofit/OkHttp" rationale).
 */
interface HttpClient {
    /**
     * Performs a GET request and returns the raw response body as text.
     * Implementations MUST throw on a non-2xx response or a transport error
     * so adapters can surface it as [Result.failure].
     */
    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): String
}
