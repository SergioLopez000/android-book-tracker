package com.librosleidos.data.remote

import com.librosleidos.domain.model.BookMetadata
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task 4.1 (design D3, spec "Search Open Library First"). Maps Open
 * Library's `search.json` response to [BookMetadata]; a transport failure
 * surfaces as [Result.failure], never a thrown exception.
 */
class OpenLibraryMetadataSourceTest {

    @Test
    fun `maps a matching doc to BookMetadata with a cover URL`() = runTest {
        val body = """
            {"docs":[{"title":"Dune","author_name":["Frank Herbert"],"cover_i":258027}]}
        """.trimIndent()
        val client = FakeHttpClient(response = Result.success(body))
        val source = OpenLibraryMetadataSource(client)

        val result = source.search("dune")

        assertEquals(
            listOf(BookMetadata(title = "Dune", author = "Frank Herbert", coverUrl = "https://covers.openlibrary.org/b/id/258027-M.jpg")),
            result.getOrThrow(),
        )
    }

    @Test
    fun `maps a doc with no author and no cover to blank author and null cover`() = runTest {
        val body = """{"docs":[{"title":"Untitled Work"}]}"""
        val client = FakeHttpClient(response = Result.success(body))
        val source = OpenLibraryMetadataSource(client)

        val result = source.search("untitled")

        assertEquals(
            listOf(BookMetadata(title = "Untitled Work", author = "", coverUrl = null)),
            result.getOrThrow(),
        )
    }

    @Test
    fun `returns an empty list when the docs array is missing`() = runTest {
        val client = FakeHttpClient(response = Result.success("{}"))
        val source = OpenLibraryMetadataSource(client)

        val result = source.search("nothing")

        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun `returns a failure result when the http client throws`() = runTest {
        val client = FakeHttpClient(response = Result.failure(RuntimeException("network down")))
        val source = OpenLibraryMetadataSource(client)

        val result = source.search("dune")

        assertTrue(result.isFailure)
    }

    @Test
    fun `sends the courtesy User-Agent header and encodes the query`() = runTest {
        val client = FakeHttpClient(response = Result.success("{}"))
        val source = OpenLibraryMetadataSource(client)

        source.search("dune messiah")

        assertEquals("https://openlibrary.org/search.json?q=dune+messiah&fields=title,author_name,cover_i&limit=20", client.lastUrl)
        assertTrue(client.lastHeaders["User-Agent"]?.contains("LibrosLeidos") == true)
    }
}
