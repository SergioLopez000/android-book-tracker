package com.librosleidos.backend.config

import com.librosleidos.testsupport.FakeRepositoryConfig
import com.librosleidos.testsupport.TestJwks
import com.librosleidos.testsupport.TestJwtSupport
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

/** Fresh context so no key is cached: the outage starts before the first JWKS fetch. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestJwtSupport::class, FakeRepositoryConfig::class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class JwksOutageTest {
    @Autowired lateinit var mockMvc: MockMvc

    @Autowired lateinit var jwks: TestJwks

    private fun list(token: String) =
        mockMvc.get("/api/books") { header("Authorization", "Bearer $token") }.andReturn().response

    @Test
    fun `a JWKS outage fails closed with a bodiless 401 and recovery is immediate`() {
        val token = jwks.token()
        jwks.stop()
        try {
            val during = list(token)
            assertEquals(401, during.status)
            assertEquals("", during.contentAsString)
        } finally {
            jwks.start()
        }
        assertEquals(200, list(token).status)
    }
}
