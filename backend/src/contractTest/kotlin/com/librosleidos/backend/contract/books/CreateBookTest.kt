package com.librosleidos.backend.contract.books

import com.librosleidos.backend.contract.ContractTestBase
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.greaterThan
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.Test

class CreateBookTest : ContractTestBase() {
    @Test
    fun `create returns 201 with the stored book`() {
        api().body(bookJson()).post("/api/books").then()
            .statusCode(201)
            .body("id", greaterThan(0))
            .body("title", equalTo("Dune"))
            .body("author", equalTo("Frank Herbert"))
            .body("coverUrl", nullValue())
            .body("rating", equalTo(4.5f))
            .body("review", equalTo("Great"))
            .body("dateFinished", equalTo("2026-01-02"))
        api().get("/api/books").then().statusCode(200).body("", hasSize<Any>(1))
    }

    @Test
    fun `create without credentials returns 401`() {
        api(authenticated = false).body(bookJson()).post("/api/books").then().statusCode(401)
    }

    @Test
    fun `create with an unknown field returns 400`() {
        invalidApi().body(bookJson(extra = ""","owner":"someone-else"""")).post("/api/books").then()
            .statusCode(400)
            .body("status", equalTo(400))
            .body("title", equalTo("Bad Request"))
    }

    @Test
    fun `create with a missing title returns 400`() {
        invalidApi().body("""{"author":"A","rating":4,"review":"","dateFinished":"2026-01-02"}""")
            .post("/api/books").then()
            .statusCode(400)
    }
}
