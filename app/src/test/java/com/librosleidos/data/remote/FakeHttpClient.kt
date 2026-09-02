package com.librosleidos.data.remote

/**
 * Test double for [HttpClient] (Phase 4 runtime harness: "fake HTTP client,
 * no live network in tests"). Records the last request for assertions such
 * as the courtesy `User-Agent` header.
 */
class FakeHttpClient(
    private val response: Result<String> = Result.success("{}"),
) : HttpClient {
    var lastUrl: String? = null
        private set
    var lastHeaders: Map<String, String> = emptyMap()
        private set
    var callCount: Int = 0
        private set

    override suspend fun get(url: String, headers: Map<String, String>): String {
        callCount += 1
        lastUrl = url
        lastHeaders = headers
        return response.getOrThrow()
    }
}
