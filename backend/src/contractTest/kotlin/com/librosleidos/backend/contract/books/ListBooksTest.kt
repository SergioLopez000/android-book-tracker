package com.librosleidos.backend.contract.books

import com.librosleidos.backend.contract.ContractTestBase
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.containsInAnyOrder
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.Test

class ListBooksTest : ContractTestBase() {
    @Test
    fun `list returns 200 with only the owner books`() {
        ownBook("Dune")
        foreignBook()
        api().get("/api/books").then()
            .statusCode(200)
            .body("", hasSize<Any>(1))
            .body("title", contains("Dune"))
            .body("[0].rating", equalTo(4.5f))
    }

    @Test
    fun `list is an empty array when the owner has no books`() {
        foreignBook()
        api().get("/api/books").then().statusCode(200).body("", hasSize<Any>(0))
    }

    @Test
    fun `list without credentials returns 401`() {
        api(authenticated = false).get("/api/books").then().statusCode(401)
    }

    @Test
    fun `another user token lists none of the owner books`() {
        ownBook()
        api(sub = OTHER_SUB).get("/api/books").then().statusCode(200).body("", hasSize<Any>(0))
    }

    @Test
    fun `a fresh token for the same sub lists the existing books`() {
        api().body(bookJson("Dune")).post("/api/books").then().statusCode(201)
        api().body(bookJson("Messiah")).post("/api/books").then().statusCode(201)
        api().get("/api/books").then().statusCode(200)
            .body("title", containsInAnyOrder("Dune", "Messiah"))
    }
}
