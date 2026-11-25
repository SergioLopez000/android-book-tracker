package com.librosleidos.backend.config

import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails
import org.springframework.stereotype.Component

private object Variables {
    const val URL = "SPRING_DATASOURCE_URL"
    const val USERNAME = "SPRING_DATASOURCE_USERNAME"
    const val PASSWORD = "SPRING_DATASOURCE_PASSWORD"
}

class MissingDatabaseConfigurationException(val missing: List<String>) :
    IllegalStateException(
        "Missing required database configuration: ${missing.joinToString(", ")}. " +
            "Set ${Variables.URL}, ${Variables.USERNAME} and ${Variables.PASSWORD} " +
            "in the environment " +
            "(see docs/secrets.md)."
    )

/**
 * Names (never values) of the database settings that are absent or blank, in the order
 * URL, USERNAME, PASSWORD. A getter that throws counts as missing.
 */
internal fun missingVariables(details: JdbcConnectionDetails): List<String> = buildList {
    if (details.valueOrNull { jdbcUrl }.isNullOrBlank()) add(Variables.URL)
    if (details.valueOrNull { username }.isNullOrBlank()) add(Variables.USERNAME)
    if (details.valueOrNull { password }.isNullOrBlank()) add(Variables.PASSWORD)
}

private inline fun JdbcConnectionDetails.valueOrNull(
    read: JdbcConnectionDetails.() -> String?
): String? = try {
    read()
} catch (_: RuntimeException) {
    null
}

/**
 * Fails startup before the DataSource is built when the effective connection details lack
 * a URL, username or password. It validates what Spring will actually use, so test
 * contexts (no details bean, or container-provided details) need no bypass.
 */
@Component
class DataSourceCredentialsValidator : BeanPostProcessor {
    override fun postProcessAfterInitialization(bean: Any, beanName: String): Any {
        if (bean is JdbcConnectionDetails) {
            val missing = missingVariables(bean)
            if (missing.isNotEmpty()) throw MissingDatabaseConfigurationException(missing)
        }
        return bean
    }
}
