package com.librosleidos.data.remote

import com.librosleidos.domain.model.BookMetadata
import com.librosleidos.domain.port.BookMetadataSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task 4.3 (design D3). Composite: tries the primary source first, only
 * advancing to the fallback on failure or an empty result — never once the
 * primary already has matches (spec: "Search Open Library First",
 * "Fallback to Google Books", "Graceful Degradation").
 */
class ChainedMetadataSourceTest {

    private class FakeBookMetadataSource(
        private val result: Result<List<BookMetadata>>,
    ) : BookMetadataSource {
        var callCount: Int = 0
            private set

        override suspend fun search(query: String): Result<List<BookMetadata>> {
            callCount += 1
            return result
        }
    }

    private val dune = BookMetadata(title = "Dune", author = "Frank Herbert", coverUrl = null)
    private val foundation = BookMetadata(title = "Foundation", author = "Isaac Asimov", coverUrl = null)

    @Test
    fun `returns primary results without calling the fallback when primary has matches`() = runTest {
        val primary = FakeBookMetadataSource(Result.success(listOf(dune)))
        val fallback = FakeBookMetadataSource(Result.success(listOf(foundation)))
        val chained = ChainedMetadataSource(primary, fallback)

        val result = chained.search("dune")

        assertEquals(listOf(dune), result.getOrThrow())
        assertEquals(0, fallback.callCount)
    }

    @Test
    fun `falls back when the primary returns an empty result`() = runTest {
        val primary = FakeBookMetadataSource(Result.success(emptyList()))
        val fallback = FakeBookMetadataSource(Result.success(listOf(foundation)))
        val chained = ChainedMetadataSource(primary, fallback)

        val result = chained.search("foundation")

        assertEquals(listOf(foundation), result.getOrThrow())
        assertEquals(1, fallback.callCount)
    }

    @Test
    fun `falls back when the primary fails`() = runTest {
        val primary = FakeBookMetadataSource(Result.failure(RuntimeException("open library down")))
        val fallback = FakeBookMetadataSource(Result.success(listOf(foundation)))
        val chained = ChainedMetadataSource(primary, fallback)

        val result = chained.search("foundation")

        assertEquals(listOf(foundation), result.getOrThrow())
        assertEquals(1, fallback.callCount)
    }

    @Test
    fun `propagates a failure when both the primary and the fallback fail`() = runTest {
        val primary = FakeBookMetadataSource(Result.failure(RuntimeException("open library down")))
        val fallback = FakeBookMetadataSource(Result.failure(RuntimeException("google books down")))
        val chained = ChainedMetadataSource(primary, fallback)

        val result = chained.search("dune")

        assertTrue(result.isFailure)
    }
}
