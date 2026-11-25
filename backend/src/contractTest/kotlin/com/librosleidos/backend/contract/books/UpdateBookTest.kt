package com.librosleidos.backend.contract.books

import com.librosleidos.backend.contract.ContractTestBase
import com.librosleidos.backend.domain.model.OwnerId
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test

class UpdateBookTest : ContractTestBase() {
    @Test
    fun `update returns 200 with the replaced book`() {
        val book = ownBook()
        api().body(bookJson(title = "Dune Messiah")).put("/api/books/{id}", book.id.value).then()
            .statusCode(200)
            .body("id", equalTo(book.id.value.toInt()))
            .body("title", equalTo("Dune Messiah"))
    }

    @Test
    fun `update without credentials returns 401`() {
        api(
            authenticated = false
        ).body(bookJson()).put("/api/books/{id}", ownBook().id.value).then()
            .statusCode(401)
    }

    @Test
    fun `update with an unknown field returns 400`() {
        invalidApi().body(
            bookJson(extra = ""","owner":"someone-else"""")
        ).put("/api/books/{id}", ownBook().id.value)
            .then().statusCode(400).body("status", equalTo(400))
    }

    @Test
    fun `update of a missing book returns 404`() {
        api().body(bookJson()).put("/api/books/{id}", 987654).then().statusCode(404)
            .body("status", equalTo(404))
    }

    @Test
    fun `update of another owner book returns 404 and leaves it untouched`() {
        val foreign = foreignBook()
        api().body(
            bookJson(title = "Hijacked")
        ).put("/api/books/{id}", foreign.id.value).then().statusCode(404)
        org.junit.jupiter.api.Assertions.assertEquals(
            "Foreign",
            repository.findById(OwnerId(ContractTestBase.FOREIGN_OWNER), foreign.id)?.title
        )
    }

    @Test
    fun `another user token cannot update a book and it stays unchanged`() {
        val book = ownBook()
        api(
            sub = OTHER_SUB
        ).body(bookJson(title = "Hijacked")).put("/api/books/{id}", book.id.value)
            .then().statusCode(404)
        org.junit.jupiter.api.Assertions.assertEquals(
            "Dune",
            repository.findById(OwnerId(com.librosleidos.testsupport.TestJwks.SUB), book.id)?.title
        )
    }
}
