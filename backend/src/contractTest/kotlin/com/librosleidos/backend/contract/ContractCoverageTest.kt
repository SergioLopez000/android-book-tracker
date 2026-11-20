package com.librosleidos.backend.contract

import com.atlassian.oai.validator.restassured.OpenApiValidationFilter
import com.sun.net.httpserver.HttpServer
import io.restassured.RestAssured
import io.swagger.parser.OpenAPIParser
import io.swagger.v3.parser.core.models.ParseOptions
import java.net.InetSocketAddress
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ContractCoverageTest {
    private val spec =
        OpenAPIParser().readLocation(
            "openapi.yaml",
            null,
            ParseOptions().apply {
                isResolve = true
            }
        ).openAPI

    @Test
    fun `every spec operation has a contract test class`() {
        assertNotNull(spec, "openapi.yaml must exist and parse")
        val operations = ContractCoverage.operationIds(spec)
        val existing =
            operations.map(ContractCoverage::expectedClass)
                .filter { runCatching { Class.forName(it) }.isSuccess }
                .toSet()

        val problems = ContractCoverage.untested(operations, existing)

        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
        assertEquals(
            setOf("createBook", "listBooks", "getBook", "updateBook", "deleteBook"),
            operations
        )
    }

    @Test
    fun `an untested operation is reported by name`() {
        val problems = ContractCoverage.untested(
            setOf("createBook", "archiveBook"),
            setOf("com.librosleidos.backend.contract.books.CreateBookTest")
        )
        assertEquals(
            listOf(
                "operation without contract test: archiveBook " +
                    "(expected com.librosleidos.backend.contract.books.ArchiveBookTest)"
            ),
            problems
        )
    }

    @Test
    fun `a response body that drifts from the schema fails the filter`() {
        val server = HttpServer.create(InetSocketAddress("localhost", 0), 0)
        server.createContext("/api/books/1") { exchange ->
            val body =
                (
                    """{"id":1,"title":"Dune","author":"A","coverUrl":null,"rating":"five",""" +
                        """"review":"","dateFinished":"2026-01-02"}"""
                    ).toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val error =
                assertThrows<Throwable> {
                    RestAssured.given().baseUri("http://localhost").port(server.address.port)
                        .filter(OpenApiValidationFilter("openapi.yaml"))
                        .get("/api/books/{id}", 1)
                }
            assertTrue(
                error.message.orEmpty().contains("rating"),
                "the violation must name the field: ${error.message}"
            )
        } finally {
            server.stop(0)
        }
    }
}
