package com.librosleidos.backend.config

import com.librosleidos.backend.BackendApplication
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.security.core.userdetails.UserDetailsService

class NoDefaultUserTest {
    @Test
    fun `context without the test customizer has no user details service`() {
        WebApplicationContextRunner()
            .withUserConfiguration(BackendApplication::class.java)
            .run { context ->
                assertTrue(context.getBeansOfType(UserDetailsService::class.java).isEmpty())
            }
    }
}
