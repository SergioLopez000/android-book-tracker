package com.librosleidos.backend.contract.books

import com.librosleidos.backend.contract.ContractTestBase
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.Test

class BookLifecycleFlowTest : ContractTestBase() {
    @Test
    fun `create, get, update, list, delete, then get is 404`() {
        val id =
            api().body(
                bookJson()
            ).post("/api/books").then().statusCode(201).extract().path<Int>("id")

        api().get("/api/books/{id}", id).then().statusCode(200).body("title", equalTo("Dune"))
        api().body(bookJson(title = "Dune Messiah")).put("/api/books/{id}", id).then()
            .statusCode(200).body("title", equalTo("Dune Messiah"))
        api().get("/api/books").then().statusCode(200)
            .body("", hasSize<Any>(1)).body("[0].title", equalTo("Dune Messiah"))
        api().delete("/api/books/{id}", id).then().statusCode(204)
        api().get("/api/books/{id}", id).then().statusCode(404)
    }
}
