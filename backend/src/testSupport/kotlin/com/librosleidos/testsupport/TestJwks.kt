package com.librosleidos.testsupport

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.PlainJWT
import com.nimbusds.jwt.SignedJWT
import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.time.Duration
import java.util.Date
import java.util.UUID

/**
 * Test-only identity provider: an RSA key generated at runtime (nothing committed), served as a
 * JWKS by a JDK HTTP server on an ephemeral loopback port, plus a Google-shaped token minter.
 * Tests never replace the production decoder; they only point it at this JWKS.
 */
class TestJwks : AutoCloseable {
    enum class Signing { VALID, FOREIGN_KEY, HS256, NONE }

    private val key: RSAKey = RSAKeyGenerator(2048).keyID(KEY_ID).generate()
    private val foreignKey: RSAKey = RSAKeyGenerator(2048).keyID(KEY_ID).generate()
    private var server: HttpServer? = null
    private var port = 0

    init {
        start()
    }

    val jwksUrl: String get() = "http://127.0.0.1:$port/jwks"

    /** Serves again on the same port, so a decoder configured once sees the outage end. */
    fun start() {
        if (server != null) return
        val created = HttpServer.create(
            InetSocketAddress(InetAddress.getByName("127.0.0.1"), port),
            0
        )
        val body = JWKSet(key.toPublicJWK()).toString().toByteArray()
        created.createContext("/jwks") { exchange ->
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(HTTP_OK, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        created.start()
        port = created.address.port
        server = created
    }

    fun stop() {
        server?.stop(0)
        server = null
    }

    @Suppress("LongParameterList")
    fun token(
        sub: String? = SUB,
        issuer: String = ISSUER_PLAIN,
        audience: String = AUDIENCE,
        expiresIn: Duration? = Duration.ofHours(1),
        notBeforeIn: Duration? = null,
        signing: Signing = Signing.VALID,
        type: JOSEObjectType? = JOSEObjectType.JWT
    ): String {
        val claims =
            JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience(audience)
                .jwtID(UUID.randomUUID().toString())
                .apply {
                    expiresIn?.let {
                        expirationTime(Date(System.currentTimeMillis() + it.toMillis()))
                    }
                    sub?.let(::subject)
                    notBeforeIn?.let {
                        notBeforeTime(Date(System.currentTimeMillis() + it.toMillis()))
                    }
                }
                .build()
        if (signing == Signing.NONE) return PlainJWT(claims).serialize()
        val alg = if (signing == Signing.HS256) JWSAlgorithm.HS256 else JWSAlgorithm.RS256
        val header = JWSHeader.Builder(alg).keyID(KEY_ID).type(type).build()
        val signer =
            when (signing) {
                Signing.HS256 -> MACSigner(ByteArray(MAC_SECRET_BYTES) { it.toByte() })
                Signing.FOREIGN_KEY -> RSASSASigner(foreignKey)
                else -> RSASSASigner(key)
            }
        return SignedJWT(header, claims).apply { sign(signer) }.serialize()
    }

    override fun close() = stop()

    companion object {
        const val KEY_ID = "test-key"
        const val SUB = "google-sub-123"
        const val ISSUER_PLAIN = "accounts.google.com"
        const val ISSUER_HTTPS = "https://accounts.google.com"
        const val AUDIENCE = "test-web-client-id.apps.googleusercontent.com"
        private const val MAC_SECRET_BYTES = 32
        private const val HTTP_OK = 200
    }
}
