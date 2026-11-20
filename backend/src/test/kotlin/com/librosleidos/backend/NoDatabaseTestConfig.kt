package com.librosleidos.backend

import org.mockito.Mockito
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.simple.JdbcClient

/**
 * Unit-suite contexts (security slices) run without a database. Persistence is verified by the
 * `integrationTest` suite against PostgreSQL. This class lives in test sources only.
 */
@Configuration(proxyBeanMethods = false)
@EnableAutoConfiguration(
    exclude = [DataSourceAutoConfiguration::class, FlywayAutoConfiguration::class]
)
class NoDatabaseTestConfig {
    @Bean
    fun jdbcClient(): JdbcClient = Mockito.mock(JdbcClient::class.java)
}
