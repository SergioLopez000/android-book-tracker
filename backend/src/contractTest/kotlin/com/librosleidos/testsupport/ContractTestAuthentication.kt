package com.librosleidos.testsupport

import com.librosleidos.backend.config.ApiAuthenticationCustomizer
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.security.config.Customizer
import org.springframework.security.core.userdetails.User
import org.springframework.security.provisioning.InMemoryUserDetailsManager

/**
 * Test-only authentication (httpBasic, one dummy user). It lives in the contractTest
 * source set outside the scan root, so it can never end up in the packaged application.
 */
@TestConfiguration(proxyBeanMethods = false)
class ContractTestAuthentication {
    @Bean
    fun contractAuthentication(): ApiAuthenticationCustomizer =
        ApiAuthenticationCustomizer { http ->
            val user = User.withUsername(OWNER).password("{noop}$PASSWORD").roles("USER").build()
            http.userDetailsService(InMemoryUserDetailsManager(user))
            http.httpBasic(Customizer.withDefaults())
        }

    companion object {
        const val OWNER = "test-owner"
        const val PASSWORD = "test-password-not-a-secret"
    }
}
