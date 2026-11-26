package com.librosleidos.backend.contract.books

import com.librosleidos.backend.contract.ContractTestBase
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test

class GetBookTest : ContractTestBase() {
    @Test
    fun `get returns 200 with the book`() {
        val book = ownBook()
        api().get("/api/books/{id}", book.id.value).then()
            .statusCode(200)
            .body("id", equalTo(book.id.value.toInt()))
            .body("title", equalTo("Dune"))
            .body("dateFinished", equalTo("2026-01-02"))
    }

    @Test
    fun `get without credentials returns 401`() {
        api(authenticated = false).get("/api/books/{id}", ownBook().id.value).then().statusCode(401)
    }

    @Test
    fun `get with an id below the minimum returns 400`() {
        invalidApi().get("/api/books/{id}", 0).then().statusCode(400).body("status", equalTo(400))
    }

    @Test
    fun `get of a missing book returns 404`() {
        api().get("/api/books/{id}", 987654).then().statusCode(404).body("status", equalTo(404))
    }

    @Test
    fun `get of another owner book returns 404`() {
        api().get("/api/books/{id}", foreignBook().id.value).then().statusCode(404)
            .body("status", equalTo(404))
    }
}
