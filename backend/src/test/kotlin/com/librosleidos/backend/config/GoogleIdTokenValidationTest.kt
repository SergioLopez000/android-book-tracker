package com.librosleidos.backend.config

import com.librosleidos.testsupport.FakeRepositoryConfig
import com.librosleidos.testsupport.TestJwks
import com.librosleidos.testsupport.TestJwks.Signing
import com.librosleidos.testsupport.TestJwtSupport
import com.nimbusds.jose.JOSEObjectType
import java.time.Duration
import java.util.Base64
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestJwtSupport::class, FakeRepositoryConfig::class)
class GoogleIdTokenValidationTest {
    @Autowired lateinit var mockMvc: MockMvc

    @Autowired lateinit var jwks: TestJwks

    private fun call(authorization: String?, path: String = "/api/books"): MockHttpServletResponse =
        mockMvc.get(path) {
            authorization?.let { header("Authorization", it) }
        }.andReturn().response

    private fun assertBodiless401(response: MockHttpServletResponse) {
        assertEquals(401, response.status)
        assertEquals("", response.contentAsString)
        assertNull(response.getHeader("WWW-Authenticate"))
    }

    @Test
    fun `a valid token is accepted`() {
        assertEquals(200, call("Bearer ${jwks.token()}").status)
    }

    @ParameterizedTest
    @MethodSource("acceptedIssuers")
    fun `both Google issuer forms are accepted`(issuer: String) {
        assertEquals(200, call("Bearer ${jwks.token(issuer = issuer)}").status)
    }

    @Suppress("UnusedParameter") // `case` only names the parameterized invocation
    @ParameterizedTest(name = "{0} is rejected with a bodiless 401")
    @MethodSource("rejectedTokens")
    fun `invalid tokens are rejected`(case: String, token: (TestJwks) -> String) {
        assertBodiless401(call("Bearer ${token(jwks)}"))
    }

    @Test
    fun `a token without a typ header is accepted`() {
        assertEquals(200, call("Bearer ${jwks.token(type = null)}").status)
    }

    @Test
    fun `HTTP Basic credentials are no longer accepted`() {
        val basic = Base64.getEncoder().encodeToString("test-owner:test-password".toByteArray())
        assertBodiless401(call("Basic $basic"))
    }

    @Test
    fun `no header is a bodiless 401`() {
        assertBodiless401(call(null))
    }

    @Test
    fun `an unknown path is 401 not 404 for a missing token and 404 for a valid one`() {
        assertBodiless401(call(null, "/nope"))
        assertEquals(404, call("Bearer ${jwks.token()}", "/nope").status)
    }

    @Test
    fun `health stays public`() {
        assertEquals(200, call(null, "/actuator/health").status)
    }

    companion object {
        @JvmStatic
        fun acceptedIssuers(): Stream<String> =
            Stream.of(TestJwks.ISSUER_PLAIN, TestJwks.ISSUER_HTTPS)

        private fun case(name: String, token: (TestJwks) -> String): Arguments =
            arguments(name, token)

        @JvmStatic
        fun rejectedTokens(): Stream<Arguments> = Stream.of(
            case("wrong issuer") { it.token(issuer = "https://evil.example") },
            case("wrong audience") {
                it.token(audience = "someone-else.apps.googleusercontent.com")
            },
            case("expired") { it.token(expiresIn = Duration.ofHours(-1)) },
            case("nbf in the future") { it.token(notBeforeIn = Duration.ofHours(1)) },
            case("foreign key") { it.token(signing = Signing.FOREIGN_KEY) },
            case("alg none") { it.token(signing = Signing.NONE) },
            case("HS256") { it.token(signing = Signing.HS256) },
            case("missing sub") { it.token(sub = null) },
            case("blank sub") { it.token(sub = "   ") },
            case("missing exp") { it.token(expiresIn = null) },
            case("unexpected typ header") { it.token(type = JOSEObjectType("at+jwt")) },
            case("malformed") { "not.a.jwt" }
        )
    }
}
