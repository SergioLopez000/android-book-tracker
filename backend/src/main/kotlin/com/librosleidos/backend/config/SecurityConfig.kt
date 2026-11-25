package com.librosleidos.backend.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint

/**
 * Deny by default: every request except `/actuator/health` needs a valid Google ID token
 * (see [GoogleIdTokenDecoder]). Every failure is a bodiless 401. No profile or flag relaxes this,
 * and the authenticated principal name is the token `sub`.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(GoogleAuthProperties::class)
class SecurityConfig {
    @Bean
    fun jwtDecoder(properties: GoogleAuthProperties): JwtDecoder =
        GoogleIdTokenDecoder.create(properties)

    @Bean
    fun securityFilterChain(http: HttpSecurity, decoder: JwtDecoder): SecurityFilterChain {
        val converter = JwtAuthenticationConverter().apply { setPrincipalClaimName("sub") }
        http {
            authorizeHttpRequests {
                authorize("/actuator/health", permitAll)
                authorize(anyRequest, authenticated)
            }
            sessionManagement { sessionCreationPolicy = SessionCreationPolicy.STATELESS }
            // Stateless API with no cookies or browser sessions: CSRF protects nothing here.
            csrf { disable() }
            exceptionHandling {
                authenticationEntryPoint = HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)
            }
            oauth2ResourceServer {
                authenticationEntryPoint = HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)
                jwt {
                    jwtDecoder = decoder
                    jwtAuthenticationConverter = converter
                }
            }
        }
        return http.build()
    }
}
