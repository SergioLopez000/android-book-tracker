package com.librosleidos.backend.config

import com.librosleidos.testsupport.TestJwks
import com.librosleidos.testsupport.TestJwtSupport
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestJwtSupport::class)
class AuthenticatedActuatorTest {
    @Autowired lateinit var mockMvc: MockMvc

    @Autowired lateinit var jwks: TestJwks

    private val paths = listOf("/actuator/env", "/actuator/beans", "/actuator/info")

    @Test
    fun `non health actuator endpoints are 401 without a token`() {
        paths.forEach { path ->
            assertEquals(401, mockMvc.get(path).andReturn().response.status, path)
        }
    }

    @Test
    fun `non health actuator endpoints are 404 with a valid bearer token`() {
        paths.forEach { path ->
            val status =
                mockMvc.get(path) {
                    header("Authorization", "Bearer ${jwks.token()}")
                }.andReturn().response.status
            assertEquals(404, status, path)
        }
    }

    @Test
    fun `health exposes only the status`() {
        assertEquals(
            """{"status":"UP"}""",
            mockMvc.get("/actuator/health").andReturn().response.contentAsString
        )
    }
}
