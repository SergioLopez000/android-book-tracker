package com.librosleidos.testsupport

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.test.context.DynamicPropertyRegistrar

/**
 * Points the production decoder at the local [TestJwks]. Only `jwk-set-uri` is overridden; the
 * issuers and the test audience come from the test-only `config/application.yaml`.
 * Outside the `com.librosleidos.backend` scan root, so only tests that import it get it.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestJwtSupport {
    @Bean(destroyMethod = "close")
    fun testJwks(): TestJwks = TestJwks()

    @Bean
    fun testJwksProperty(jwks: TestJwks): DynamicPropertyRegistrar = DynamicPropertyRegistrar {
        it.add("librosleidos.auth.google.jwk-set-uri") { jwks.jwksUrl }
    }
}
