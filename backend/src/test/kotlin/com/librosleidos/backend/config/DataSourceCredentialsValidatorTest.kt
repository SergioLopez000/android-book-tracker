package com.librosleidos.backend.config

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class DataSourceCredentialsValidatorTest {
    private val url = "jdbc:postgresql://db.internal:5432/books"
    private val user = "books_app"
    private val password = "s3cr3t-value"

    private fun details(
        url: String? = this.url,
        user: String? = this.user,
        password: String? = this.password,
        urlFails: Boolean = false
    ): JdbcConnectionDetails = object : JdbcConnectionDetails {
        override fun getJdbcUrl(): String {
            if (urlFails) error("no url")
            return url.orEmpty()
        }

        override fun getUsername(): String? = user

        override fun getPassword(): String? = password
    }

    @Test
    fun `all present reports nothing missing`() {
        assertTrue(missingVariables(details()).isEmpty())
    }

    @Test
    fun `each blank shape is reported by name`() {
        for (blank in listOf(null, "", "   ")) {
            assertEquals(
                listOf("SPRING_DATASOURCE_URL"),
                missingVariables(details(url = blank))
            )
            assertEquals(
                listOf("SPRING_DATASOURCE_USERNAME"),
                missingVariables(details(user = blank))
            )
            assertEquals(
                listOf("SPRING_DATASOURCE_PASSWORD"),
                missingVariables(details(password = blank))
            )
        }
    }

    @Test
    fun `throwing url getter counts as missing`() {
        assertEquals(listOf("SPRING_DATASOURCE_URL"), missingVariables(details(urlFails = true)))
    }

    @Test
    fun `several missing keep url username password order`() {
        assertEquals(
            listOf(
                "SPRING_DATASOURCE_URL",
                "SPRING_DATASOURCE_USERNAME",
                "SPRING_DATASOURCE_PASSWORD"
            ),
            missingVariables(details(url = null, user = "", password = " "))
        )
    }

    @Test
    fun `beans that are not connection details pass through untouched`() {
        val bean = Any()
        assertSame(
            bean,
            DataSourceCredentialsValidator().postProcessAfterInitialization(bean, "any")
        )
    }

    @Test
    fun `valid connection details pass through untouched`() {
        val ok = details()
        assertSame(ok, DataSourceCredentialsValidator().postProcessAfterInitialization(ok, "ok"))
    }

    @Test
    fun `message names variables and the docs hint but never a value`() {
        val ex =
            assertThrows<MissingDatabaseConfigurationException> {
                DataSourceCredentialsValidator()
                    .postProcessAfterInitialization(details(password = ""), "d")
            }
        val message = ex.message.orEmpty()
        assertTrue(message.contains("Missing required database configuration"))
        assertTrue(message.contains("SPRING_DATASOURCE_PASSWORD"))
        assertTrue(message.contains("docs/secrets.md"))
        assertFalse(message.contains(url))
        assertFalse(message.contains(user))
        assertEquals(listOf("SPRING_DATASOURCE_PASSWORD"), ex.missing)
    }

    private val runner =
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration::class.java))
            .withBean(DataSourceCredentialsValidator::class.java)

    @Test
    fun `context without properties fails naming all three`() {
        runner.run { context ->
            val message = rootMessage(context.startupFailure)
            assertTrue(message.contains("Missing required database configuration"))
            assertTrue(message.contains("SPRING_DATASOURCE_URL"))
            assertTrue(message.contains("SPRING_DATASOURCE_USERNAME"))
            assertTrue(message.contains("SPRING_DATASOURCE_PASSWORD"))
        }
    }

    @Test
    fun `context with blank password fails naming it without values`() {
        runner
            .withPropertyValues(
                "spring.datasource.url=$url",
                "spring.datasource.username=$user",
                "spring.datasource.password=   "
            ).run { context ->
                val message = rootMessage(context.startupFailure)
                assertTrue(message.contains("SPRING_DATASOURCE_PASSWORD"))
                assertFalse(message.contains(url))
                assertFalse(message.contains(user))
            }
    }

    @Test
    fun `context with full properties starts`() {
        runner
            .withPropertyValues(
                "spring.datasource.url=$url",
                "spring.datasource.username=$user",
                "spring.datasource.password=$password"
            ).run { context -> assertTrue(context.startupFailure == null) }
    }

    private fun rootMessage(failure: Throwable?): String {
        var t = failure
        while (t != null) {
            if (t is MissingDatabaseConfigurationException) return t.message.orEmpty()
            t = t.cause
        }
        error("no MissingDatabaseConfigurationException in cause chain: $failure")
    }
}
