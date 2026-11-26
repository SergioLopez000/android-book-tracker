package com.librosleidos.backend.config

import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint

/**
 * Deny by default: every request except `/actuator/health` is rejected with 401
 * until authentication (#26) is wired. No profile or flag can relax this.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {
    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        customizers: ObjectProvider<ApiAuthenticationCustomizer>
    ): SecurityFilterChain {
        http {
            authorizeHttpRequests {
                authorize("/actuator/health", permitAll)
                authorize(anyRequest, authenticated)
            }
            sessionManagement { sessionCreationPolicy = SessionCreationPolicy.STATELESS }
            // Stateless API with no cookies or browser sessions: CSRF protects nothing here.
            csrf { disable() }
            exceptionHandling {
                authenticationEntryPoint =
                    HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)
            }
        }
        customizers.orderedStream().forEach { it.customize(http) }
        return http.build()
    }
}
