package com.librosleidos.backend.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.springframework.boot.test.context.assertj.AssertableApplicationContext
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class GoogleAuthConfigurationValidatorTest {
    private val runner =
        ApplicationContextRunner().withUserConfiguration(
            GoogleAuthConfigurationValidator::class.java
        )

    private val realLooking = "9876-realistic-web-client.apps.googleusercontent.com"
    private val audience = "librosleidos.auth.google.audience"

    private fun failure(context: AssertableApplicationContext): MissingAuthConfigurationException {
        val cause = generateSequence<Throwable>(context.startupFailure) { it.cause }
            .filterIsInstance<MissingAuthConfigurationException>()
            .firstOrNull()
        return assertNotNull(cause, "expected MissingAuthConfigurationException")
    }

    @Test
    fun `a complete configuration starts`() {
        runner.withPropertyValues("$audience=$realLooking").run { assertNull(it.startupFailure) }
    }

    @Test
    fun `an unset audience fails naming the setting and the environment variable`() {
        runner.run {
            val message = failure(it).message.orEmpty()
            assertTrue(
                message.startsWith("Missing required authentication configuration:"),
                message
            )
            assertTrue(message.contains(audience), message)
            assertTrue(message.contains("GOOGLE_OAUTH_WEB_CLIENT_ID"), message)
        }
    }

    @Test
    fun `a whitespace-only audience counts as blank`() {
        runner.withPropertyValues("$audience=   ").run {
            assertTrue(failure(it).missing.single().startsWith(audience))
        }
    }

    @Test
    fun `blank issuers fail naming the setting`() {
        runner.withPropertyValues(
            "$audience=$realLooking",
            "librosleidos.auth.google.issuers="
        ).run {
            assertEquals(listOf("librosleidos.auth.google.issuers"), failure(it).missing)
        }
    }

    @Test
    fun `a blank JWKS URI fails naming the setting`() {
        runner.withPropertyValues(
            "$audience=$realLooking",
            "librosleidos.auth.google.jwk-set-uri= "
        )
            .run {
                assertEquals(listOf("librosleidos.auth.google.jwk-set-uri"), failure(it).missing)
            }
    }

    @Test
    fun `the message never echoes a configured value`() {
        runner.withPropertyValues(
            "$audience=$realLooking",
            "librosleidos.auth.google.jwk-set-uri=",
            "librosleidos.auth.google.issuers=https://issuer-value.example"
        ).run {
            val message = failure(it).message.orEmpty()
            assertFalse(message.contains(realLooking), message)
            assertFalse(message.contains("issuer-value.example"), message)
        }
    }

    @Test
    fun `a JWKS URI that is neither https nor loopback is rejected without echoing it`() {
        val properties =
            GoogleAuthProperties(audience = realLooking, jwkSetUri = "http://evil.example/jwks")
        val error =
            assertFailsWith<IllegalStateException> { GoogleIdTokenDecoder.create(properties) }
        assertFalse(error.message.orEmpty().contains("evil.example"))
    }

    @Test
    fun `an https or loopback JWKS URI is accepted`() {
        GoogleIdTokenDecoder.create(GoogleAuthProperties(audience = realLooking))
        GoogleIdTokenDecoder.create(
            GoogleAuthProperties(audience = realLooking, jwkSetUri = "http://127.0.0.1:1/jwks")
        )
    }
}
