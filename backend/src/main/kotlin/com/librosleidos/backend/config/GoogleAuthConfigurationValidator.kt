package com.librosleidos.backend.config

import org.springframework.beans.factory.config.BeanFactoryPostProcessor
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.context.EnvironmentAware
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

private object Names {
    const val PREFIX = "librosleidos.auth.google"
    const val AUDIENCE_VARIABLE = "GOOGLE_OAUTH_WEB_CLIENT_ID"
}

class MissingAuthConfigurationException(val missing: List<String>) :
    IllegalStateException(
        "Missing required authentication configuration: ${missing.joinToString(", ")}. " +
            "Set ${Names.AUDIENCE_VARIABLE} (the Web client ID) in the environment (see docs/secrets.md)."
    )

/** Names (never values) of the Google auth settings that are absent or blank. */
internal fun missingAuthSettings(properties: GoogleAuthProperties): List<String> = buildList {
    if (properties.issuers.isEmpty() || properties.issuers.any { it.isBlank() }) {
        add("${Names.PREFIX}.issuers")
    }
    if (properties.audience.isBlank()) add("${Names.PREFIX}.audience (${Names.AUDIENCE_VARIABLE})")
    if (properties.jwkSetUri.isBlank()) add("${Names.PREFIX}.jwk-set-uri")
}

/**
 * Fails startup before any bean is created when the Google issuers, audience or JWKS URI are
 * missing. It is a [BeanFactoryPostProcessor], so it always runs before the database credential
 * check, which fires when the connection details bean is built.
 */
@Component
class GoogleAuthConfigurationValidator :
    BeanFactoryPostProcessor,
    EnvironmentAware {
    private lateinit var environment: Environment

    override fun setEnvironment(environment: Environment) {
        this.environment = environment
    }

    override fun postProcessBeanFactory(beanFactory: ConfigurableListableBeanFactory) {
        val properties =
            Binder.get(environment)
                .bind(Names.PREFIX, GoogleAuthProperties::class.java)
                .orElseGet { GoogleAuthProperties() }
        val missing = missingAuthSettings(properties)
        if (missing.isNotEmpty()) throw MissingAuthConfigurationException(missing)
    }
}
