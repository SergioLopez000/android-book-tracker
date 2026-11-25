package com.librosleidos.backend.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Google ID token settings (`librosleidos.auth.google.*`). The audience is the Web client ID
 * (a public identifier, not a secret) and has no default: an unset value stops the startup.
 */
@ConfigurationProperties("librosleidos.auth.google")
data class GoogleAuthProperties(
    val issuers: List<String> = listOf("accounts.google.com", "https://accounts.google.com"),
    val audience: String = "",
    val jwkSetUri: String = "https://www.googleapis.com/oauth2/v3/certs"
)
