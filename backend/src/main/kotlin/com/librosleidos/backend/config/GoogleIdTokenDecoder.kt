package com.librosleidos.backend.config

import java.net.URI
import java.net.http.HttpClient
import java.time.Duration
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtClaimNames
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.jwt.JwtTimestampValidator
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.web.client.RestTemplate

/**
 * Turns every JWKS failure (unreachable, timeout, no key) into a [BadJwtException]. Spring maps any
 * other [JwtException] to an authentication service error that surfaces as a 500, but a
 * token that cannot be verified must be a bodiless 401.
 */
class FailClosedJwtDecoder(private val delegate: JwtDecoder) : JwtDecoder {
    override fun decode(token: String): Jwt = try {
        delegate.decode(token)
    } catch (e: BadJwtException) {
        throw e
    } catch (e: JwtException) {
        throw BadJwtException("Token could not be verified", e)
    }
}

/** Builds the one production [JwtDecoder]: RS256 only, Google issuers, our audience, a real `sub`. */
object GoogleIdTokenDecoder {
    private val TIMEOUT: Duration = Duration.ofSeconds(2)
    private val LOOPBACK = setOf("localhost", "127.0.0.1", "[::1]")

    fun create(properties: GoogleAuthProperties): JwtDecoder {
        check(isHttpsOrLoopback(properties.jwkSetUri)) {
            "librosleidos.auth.google.jwk-set-uri must be an https URL (or loopback)"
        }
        val client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build()
        val requests = JdkClientHttpRequestFactory(client).apply { setReadTimeout(TIMEOUT) }
        val decoder =
            NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .validateType(true)
                .restOperations(RestTemplate(requests))
                .build()
        decoder.setJwtValidator(validators(properties))
        return FailClosedJwtDecoder(decoder)
    }

    internal fun validators(properties: GoogleAuthProperties): OAuth2TokenValidator<Jwt> =
        DelegatingOAuth2TokenValidator(
            JwtTimestampValidator(),
            require("exp") { it.expiresAt != null },
            require("iss") { it.getClaimAsString(JwtClaimNames.ISS) in properties.issuers },
            require("aud") { properties.audience in (it.audience ?: emptyList()) },
            require("sub") { !it.subject.isNullOrBlank() }
        )

    private fun require(claim: String, accept: (Jwt) -> Boolean) = OAuth2TokenValidator<Jwt> {
        if (accept(it)) {
            OAuth2TokenValidatorResult.success()
        } else {
            OAuth2TokenValidatorResult.failure(OAuth2Error("invalid_token", "Invalid $claim", null))
        }
    }

    private fun isHttpsOrLoopback(uri: String): Boolean {
        val parsed = runCatching { URI(uri) }.getOrNull() ?: return false
        return parsed.scheme == "https" || (parsed.scheme == "http" && parsed.host in LOOPBACK)
    }
}
