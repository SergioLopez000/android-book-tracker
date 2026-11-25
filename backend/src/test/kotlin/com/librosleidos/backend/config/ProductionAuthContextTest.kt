package com.librosleidos.backend.config

import com.librosleidos.backend.BackendApplication
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter

/** The production configuration only: no test authentication and no `application.yaml`. */
class ProductionAuthContextTest {
    private val runner =
        WebApplicationContextRunner().withUserConfiguration(BackendApplication::class.java)

    private val withAudience =
        "librosleidos.auth.google.audience=test-web-client.apps.googleusercontent.com"

    @Test
    fun `the production context has one decoder, no user store and no HTTP Basic`() {
        runner.withPropertyValues(withAudience).run { context ->
            assertEquals(1, context.getBeansOfType(JwtDecoder::class.java).size)
            assertTrue(context.getBeansOfType(UserDetailsService::class.java).isEmpty())
            val chain = context.getBean(SecurityFilterChain::class.java)
            assertTrue(chain.filters.none { it is BasicAuthenticationFilter })
        }
    }

    @Test
    fun `without the audience the context fails with the authentication message first`() {
        runner.run { context ->
            val message =
                generateSequence<Throwable>(assertNotNull(context.startupFailure)) { it.cause }
                    .joinToString(" | ") { it.message.orEmpty() }
            assertTrue(message.contains("Missing required authentication configuration"), message)
            assertTrue(!message.contains("Missing required database configuration"), message)
        }
    }
}
