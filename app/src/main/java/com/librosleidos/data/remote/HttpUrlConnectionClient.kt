package com.librosleidos.data.remote

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [HttpClient] backed by the JDK's [HttpURLConnection] — deliberately no
 * OkHttp/Retrofit dependency for this single-user, low-volume app (design:
 * keep `data/remote` minimal). Not unit-tested directly: it is a thin,
 * side-effecting transport wrapper exercised only through real network
 * calls, which the test harness must never make (see PR4 apply-progress —
 * exempt, same pattern as `BooksDatabase` in PR3). [OpenLibraryMetadataSource]
 * and [GoogleBooksMetadataSource] are the units under test, driven through
 * the [HttpClient] port with a fake.
 */
class HttpUrlConnectionClient(
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 10_000,
) : HttpClient {

    override suspend fun get(url: String, headers: Map<String, String>): String =
        withContext(Dispatchers.IO) {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = connectTimeoutMs
                connection.readTimeout = readTimeoutMs
                headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }

                val code = connection.responseCode
                check(code in 200..299) { "HTTP $code for $url" }

                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
        }
}
