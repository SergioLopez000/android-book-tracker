package com.librosleidos.backend.contract.books

import com.librosleidos.backend.contract.ContractTestBase
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test

class DeleteBookTest : ContractTestBase() {
    @Test
    fun `delete returns 204 with no body`() {
        val book = ownBook()
        api().delete("/api/books/{id}", book.id.value).then().statusCode(204)
    }

    @Test
    fun `delete without credentials returns 401`() {
        api(
            authenticated = false
        ).delete("/api/books/{id}", ownBook().id.value).then().statusCode(401)
    }

    @Test
    fun `delete with an id below the minimum returns 400`() {
        invalidApi().delete(
            "/api/books/{id}",
            0
        ).then().statusCode(400).body("status", equalTo(400))
    }

    @Test
    fun `delete of another owner book returns 404`() {
        api().delete("/api/books/{id}", foreignBook().id.value).then().statusCode(404)
            .body("status", equalTo(404))
    }
}
