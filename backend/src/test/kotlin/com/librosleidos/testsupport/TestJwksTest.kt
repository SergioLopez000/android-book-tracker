package com.librosleidos.testsupport

import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jwt.SignedJWT
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TestJwksTest {
    private fun served(jwks: TestJwks): JWKSet = JWKSet.load(URI(jwks.jwksUrl).toURL())

    private fun verifies(token: String, set: JWKSet): Boolean {
        val jwt = SignedJWT.parse(token)
        val key = set.getKeyByKeyId(jwt.header.keyID) as? RSAKey ?: return false
        return jwt.verify(RSASSAVerifier(key))
    }

    @Test
    fun `a minted token verifies against the served JWKS`() = TestJwks().use { jwks ->
        assertTrue(verifies(jwks.token(), served(jwks)))
    }

    @Test
    fun `two tokens minted in a row for the same sub are distinct`() = TestJwks().use { jwks ->
        val first = jwks.token()
        val second = jwks.token()
        assertNotEquals(first, second)
        assertEquals(
            SignedJWT.parse(first).jwtClaimsSet.subject,
            SignedJWT.parse(second).jwtClaimsSet.subject
        )
    }

    @Test
    fun `a token from an unknown key does not verify`() = TestJwks().use { jwks ->
        assertFalse(verifies(jwks.token(signing = TestJwks.Signing.FOREIGN_KEY), served(jwks)))
    }

    @Test
    fun `the JWKS URL is loopback on an ephemeral port`() = TestJwks().use { jwks ->
        val uri = URI(jwks.jwksUrl)
        assertEquals("127.0.0.1", uri.host)
        assertTrue(uri.port > 0)
    }

    @Test
    fun `stop then start serves again on the same port`() = TestJwks().use { jwks ->
        val url = jwks.jwksUrl
        jwks.stop()
        jwks.start()
        assertEquals(url, jwks.jwksUrl)
        assertTrue(verifies(jwks.token(), served(jwks)))
    }
}
