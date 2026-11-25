package com.librosleidos.backend.web

import com.librosleidos.testsupport.FailingRepositoryConfig
import com.librosleidos.testsupport.TestJwks
import com.librosleidos.testsupport.TestJwtSupport
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestJwtSupport::class, FailingRepositoryConfig::class)
class UnexpectedErrorTest {
    @Autowired lateinit var mockMvc: MockMvc

    @Autowired lateinit var jwks: TestJwks

    @Test
    fun `an unexpected failure is a generic 500 with the same problem shape and no internals`() {
        val response =
            mockMvc.get("/api/books") {
                header("Authorization", "Bearer ${jwks.token()}")
            }.andReturn().response
        assertEquals(500, response.status)
        val body = response.contentAsString
        assertTrue(body.contains(""""status":500"""), body)
        assertTrue(body.contains(""""detail":"Unexpected error""""), body)
        listOf("SELECT", "Repo.kt", "IllegalStateException", "secret").forEach {
            assertFalse(body.contains(it), "leaked '$it' in $body")
        }
    }
}
