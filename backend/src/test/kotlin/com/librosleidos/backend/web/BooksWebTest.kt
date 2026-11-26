package com.librosleidos.backend.web

import com.librosleidos.backend.domain.model.OwnerId
import com.librosleidos.backend.domain.port.BookRepository
import com.librosleidos.testsupport.FakeRepositoryConfig
import com.librosleidos.testsupport.TestApiAuthentication
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.test.web.servlet.request

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestApiAuthentication::class, FakeRepositoryConfig::class)
class BooksWebTest {
    @Autowired lateinit var mockMvc: MockMvc

    @Autowired lateinit var repository: BookRepository

    private val owner = OwnerId(TestApiAuthentication.OWNER)

    private fun valid(extra: String = "") =
        """{"title":"Dune","author":"Frank Herbert","rating":4.5,"review":"Great","dateFinished":"2026-01-02"$extra}"""

    private fun postBook(body: String, authenticated: Boolean = true) = mockMvc.post("/api/books") {
        if (authenticated) {
            with(httpBasic(TestApiAuthentication.OWNER, TestApiAuthentication.PASSWORD))
        }
        contentType = MediaType.APPLICATION_JSON
        content = body
    }.andReturn().response

    @Test
    fun `create returns 201 and stores the book under the authenticated owner`() {
        val response = postBook(valid())
        assertEquals(201, response.status)
        assertEquals(1, repository.findAll(owner).count { it.title == "Dune" })
        assertEquals(0, repository.findAll(OwnerId("someone-else")).size)
    }

    @Test
    fun `an owner field in the body is rejected, never trusted`() {
        val before = repository.findAll(owner).size
        assertEquals(400, postBook(valid(""","owner":"someone-else"""")).status)
        assertEquals(before, repository.findAll(owner).size)
    }

    @ParameterizedTest
    @MethodSource("invalidBodies")
    fun `schema violations are 400 with the constant problem body and nothing persisted`(
        body: String
    ) {
        val before = repository.findAll(owner).size
        val response = postBook(body)
        assertEquals(400, response.status)
        Companion.assertConstantProblem(
            response.contentAsString,
            400,
            "Request does not match the API contract"
        )
        assertEquals(before, repository.findAll(owner).size)
    }

    @Test
    fun `a title that passes the schema but is blank is 400 and nothing persisted`() {
        val before = repository.findAll(owner).size
        val response = postBook(valid().replace("Dune", "   "))
        assertEquals(400, response.status)
        Companion.assertConstantProblem(
            response.contentAsString,
            400,
            "Request does not match the API contract"
        )
        assertEquals(before, repository.findAll(owner).size)
    }

    @Test
    fun `anonymous invalid body is 401 with no body, before validation`() {
        val response = postBook("""{"nope":1}""", authenticated = false)
        assertEquals(401, response.status)
        assertEquals("", response.contentAsString)
    }

    @Test
    fun `get update and delete work for the owner and others see 404`() {
        val id = repository.create(
            owner,
            com.librosleidos.backend.domain.model.BookDraft(
                "T",
                "A",
                null,
                com.librosleidos.backend.domain.model.Rating(3f),
                "",
                java.time.LocalDate.of(2026, 1, 1)
            )
        ).id.value
        val auth = httpBasic(TestApiAuthentication.OWNER, TestApiAuthentication.PASSWORD)
        assertEquals(200, mockMvc.get("/api/books/$id") { with(auth) }.andReturn().response.status)
        assertEquals(
            200,
            mockMvc.put("/api/books/$id") {
                with(auth)
                contentType = MediaType.APPLICATION_JSON
                content = valid()
            }.andReturn().response.status
        )
        assertEquals(
            204,
            mockMvc.delete("/api/books/$id") {
                with(auth)
            }.andReturn().response.status
        )
        val missing = mockMvc.get("/api/books/$id") { with(auth) }.andReturn().response
        assertEquals(404, missing.status)
        Companion.assertConstantProblem(missing.contentAsString, 404, "Resource not found")
        assertEquals(
            404,
            mockMvc.delete("/api/books/$id") {
                with(auth)
            }.andReturn().response.status
        )
    }

    @Test
    fun `list returns only the owner books as an array`() {
        val response =
            mockMvc.get("/api/books") {
                with(httpBasic(TestApiAuthentication.OWNER, TestApiAuthentication.PASSWORD))
            }.andReturn().response
        assertEquals(200, response.status)
        assertEquals('[', response.contentAsString.first())
    }

    @Test
    fun `a non numeric id is 400`() {
        val response =
            mockMvc.get("/api/books/abc") {
                with(httpBasic(TestApiAuthentication.OWNER, TestApiAuthentication.PASSWORD))
            }.andReturn().response
        assertEquals(400, response.status)
        Companion.assertConstantProblem(
            response.contentAsString,
            400,
            "Request does not match the API contract"
        )
    }

    @Test
    fun `unknown routes are 404 for the authenticated user and 401 for anonymous`() {
        val auth = httpBasic(TestApiAuthentication.OWNER, TestApiAuthentication.PASSWORD)
        val api = mockMvc.get("/api/nope") { with(auth) }.andReturn().response
        assertEquals(404, api.status)
        Companion.assertConstantProblem(api.contentAsString, 404, "Resource not found")
        assertEquals(404, mockMvc.get("/nope") { with(auth) }.andReturn().response.status)
        assertEquals(401, mockMvc.get("/api/nope").andReturn().response.status)
    }

    @Test
    fun `an unsupported method is a constant 405`() {
        val response =
            mockMvc.request(HttpMethod.PATCH, "/api/books") {
                with(httpBasic(TestApiAuthentication.OWNER, TestApiAuthentication.PASSWORD))
            }.andReturn().response
        assertEquals(405, response.status)
        Companion.assertConstantProblem(response.contentAsString, 405, "Method not allowed")
    }

    companion object {
        @JvmStatic
        fun invalidBodies(): List<String> {
            val base = """"author":"A","rating":4.5,"review":"r","dateFinished":"2026-01-02""""
            return listOf(
                """{"title":"T",$base,"extra":1}""",
                """{"title":"${"x".repeat(201)}",$base}""",
                """{"title":"T","author":"${"x".repeat(
                    201
                )}","rating":4.5,"review":"r","dateFinished":"2026-01-02"}""",
                """{"title":"T",$base,"coverUrl":"${"x".repeat(2049)}"}""",
                """{"title":"T","author":"A","rating":4.5,"review":"${"x".repeat(
                    5001
                )}","dateFinished":"2026-01-02"}""",
                """{"title":"T","author":"A","rating":"high","review":"r","dateFinished":"2026-01-02"}""",
                """{"title":"T","author":"A","rating":"4.5","review":"r","dateFinished":"2026-01-02"}""",
                """{"title":"T","author":"A","rating":4.3,"review":"r","dateFinished":"2026-01-02"}""",
                """{"title":"T","author":"A","rating":4.5,"review":"r","dateFinished":"not-a-date"}""",
                """{"title":5,$base}""",
                """{"author":"A","rating":4.5,"review":"r","dateFinished":"2026-01-02"}""",
                """{"title":""",
                "[]",
                ""
            )
        }

        fun Companion.assertConstantProblem(body: String, status: Int, detail: String) {
            assertTrue(body.contains(""""status":$status"""), body)
            assertTrue(body.contains(""""detail":"$detail""""), body)
            listOf(
                "Exception",
                "at com.",
                ".kt",
                ".java",
                "SELECT",
                "stackTrace",
                "trace",
                "Caused by"
            ).forEach {
                assertFalse(body.contains(it), "leaked '$it' in $body")
            }
        }
    }
}
