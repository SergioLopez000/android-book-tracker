package com.librosleidos.backend.config

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
class SecuritySliceTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `anonymous books request is 401 with no body and no cookie`() {
        val response = mockMvc.get("/api/books").andReturn().response
        assertEquals(401, response.status)
        assertEquals("", response.contentAsString)
        assertNull(response.getHeader("Set-Cookie"))
    }

    @Test
    fun `anonymous unknown path is 401 not 404`() {
        assertEquals(401, mockMvc.get("/nope").andReturn().response.status)
    }

    @Test
    fun `anonymous env and info are 401`() {
        assertEquals(401, mockMvc.get("/actuator/env").andReturn().response.status)
        assertEquals(401, mockMvc.get("/actuator/info").andReturn().response.status)
    }

    @Test
    fun `health is public and exposes only the status`() {
        val response = mockMvc.get("/actuator/health").andReturn().response
        assertEquals(200, response.status)
        assertEquals("""{"status":"UP"}""", response.contentAsString)
        assertNull(response.getHeader("Set-Cookie"))
    }
}
