package com.librosleidos.testsupport

import com.librosleidos.backend.config.ApiAuthenticationCustomizer
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.security.config.Customizer
import org.springframework.security.core.userdetails.User
import org.springframework.security.provisioning.InMemoryUserDetailsManager

/**
 * Test-only authentication (httpBasic with one dummy user). It lives outside the
 * `com.librosleidos.backend` scan root and is imported explicitly, so it can
 * never end up in the packaged application.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestApiAuthentication {
    @Bean
    fun testAuthentication(): ApiAuthenticationCustomizer = ApiAuthenticationCustomizer { http ->
        val user =
            User.withUsername(OWNER)
                .password("{noop}$PASSWORD")
                .roles("USER")
                .build()
        http.userDetailsService(InMemoryUserDetailsManager(user))
        http.httpBasic(Customizer.withDefaults())
    }

    companion object {
        const val OWNER = "test-owner"
        const val PASSWORD = "test-password-not-a-secret"
    }
}
