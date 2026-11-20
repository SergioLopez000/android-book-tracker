package com.librosleidos.backend.config

import com.librosleidos.testsupport.TestApiAuthentication
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestApiAuthentication::class)
class AuthenticatedActuatorTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `non health actuator endpoints are 404 for the test user`() {
        listOf("/actuator/env", "/actuator/beans", "/actuator/info").forEach { path ->
            val status =
                mockMvc.get(path) {
                    with(httpBasic(TestApiAuthentication.OWNER, TestApiAuthentication.PASSWORD))
                }.andReturn().response.status
            assertEquals(404, status, path)
        }
    }
}
