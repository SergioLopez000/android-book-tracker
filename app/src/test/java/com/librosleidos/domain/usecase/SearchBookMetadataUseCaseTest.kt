package com.librosleidos.domain.usecase

import com.librosleidos.domain.model.BookMetadata
import com.librosleidos.domain.port.BookMetadataSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task 2.7 (design: SearchBookMetadataUseCase — trim, >=2 chars, source
 * failure maps to an empty result). Spec: "Graceful Degradation".
 */
class SearchBookMetadataUseCaseTest {

    private class FakeBookMetadataSource(
        private val result: Result<List<BookMetadata>> = Result.success(emptyList()),
    ) : BookMetadataSource {
        var lastQuery: String? = null
            private set

        override suspend fun search(query: String): Result<List<BookMetadata>> {
            lastQuery = query
            return result
        }
    }

    private val dune = BookMetadata(title = "Dune", author = "Frank Herbert", coverUrl = null)

    @Test
    fun `trims the query before delegating to the source`() = runTest {
        val source = FakeBookMetadataSource()
        val useCase = SearchBookMetadataUseCase(source)

        useCase("  dune  ")

        assertEquals("dune", source.lastQuery)
    }

    @Test
    fun `rejects a query shorter than 2 characters without calling the source`() = runTest {
        val source = FakeBookMetadataSource()
        val useCase = SearchBookMetadataUseCase(source)

        val results = useCase(" a ")

        assertTrue(results.isEmpty())
        assertEquals(null, source.lastQuery)
    }

    @Test
    fun `returns the source results on success`() = runTest {
        val source = FakeBookMetadataSource(result = Result.success(listOf(dune)))
        val useCase = SearchBookMetadataUseCase(source)

        val results = useCase("dune")

        assertEquals(listOf(dune), results)
    }

    @Test
    fun `maps a source failure to an empty result`() = runTest {
        val source = FakeBookMetadataSource(result = Result.failure(RuntimeException("network down")))
        val useCase = SearchBookMetadataUseCase(source)

        val results = useCase("dune")

        assertTrue(results.isEmpty())
    }
}
