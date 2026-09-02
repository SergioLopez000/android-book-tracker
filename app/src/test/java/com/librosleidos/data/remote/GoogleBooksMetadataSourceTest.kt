package com.librosleidos.data.remote

import com.librosleidos.domain.model.BookMetadata
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task 4.2 (design D2/D3, spec "Fallback to Google Books"). Gated by a
 * non-blank API key: a blank key disables the fallback entirely (design D2
 * — "the key IS the switch"), returning an empty result without a request.
 */
class GoogleBooksMetadataSourceTest {

    @Test
    fun `returns an empty result without calling the http client when the API key is blank`() = runTest {
        val client = FakeHttpClient(response = Result.success("{}"))
        val source = GoogleBooksMetadataSource(client, apiKey = "")

        val result = source.search("dune")

        assertTrue(result.getOrThrow().isEmpty())
        assertEquals(0, client.callCount)
    }

    @Test
    fun `maps a matching item to BookMetadata and upgrades the thumbnail to https`() = runTest {
        val body = """
            {"items":[{"volumeInfo":{"title":"Dune","authors":["Frank Herbert"],"imageLinks":{"thumbnail":"http://books.google.com/dune.jpg"}}}]}
        """.trimIndent()
        val client = FakeHttpClient(response = Result.success(body))
        val source = GoogleBooksMetadataSource(client, apiKey = "test-key")

        val result = source.search("dune")

        assertEquals(
            listOf(BookMetadata(title = "Dune", author = "Frank Herbert", coverUrl = "https://books.google.com/dune.jpg")),
            result.getOrThrow(),
        )
        assertTrue(client.lastUrl?.contains("key=test-key") == true)
    }

    @Test
    fun `maps an item with no authors and no imageLinks to blank author and null cover`() = runTest {
        val body = """{"items":[{"volumeInfo":{"title":"Untitled Work"}}]}"""
        val client = FakeHttpClient(response = Result.success(body))
        val source = GoogleBooksMetadataSource(client, apiKey = "test-key")

        val result = source.search("untitled")

        val metadata = result.getOrThrow().single()
        assertEquals("", metadata.author)
        assertNull(metadata.coverUrl)
    }

    @Test
    fun `returns a failure result when the http client throws`() = runTest {
        val client = FakeHttpClient(response = Result.failure(RuntimeException("network down")))
        val source = GoogleBooksMetadataSource(client, apiKey = "test-key")

        val result = source.search("dune")

        assertTrue(result.isFailure)
    }
}
