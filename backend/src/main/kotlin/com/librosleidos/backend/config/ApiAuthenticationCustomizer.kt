package com.librosleidos.backend.config

import org.springframework.security.config.annotation.web.builders.HttpSecurity

/**
 * Extension point for the authentication mechanism chosen in #26.
 * There is intentionally NO implementation in main code: only test sources
 * provide one, so nothing in the packaged artifact can authenticate a caller.
 */
fun interface ApiAuthenticationCustomizer {
    fun customize(http: HttpSecurity)
}
