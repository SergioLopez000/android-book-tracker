package com.librosleidos.testsupport

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.postgresql.PostgreSQLContainer

/** One real PostgreSQL per cached Spring context; `@ServiceConnection` wires the DataSource and Flyway. */
@TestConfiguration(proxyBeanMethods = false)
class ContractPostgresConfig {
    @Bean
    @ServiceConnection
    fun postgres(): PostgreSQLContainer = PostgreSQLContainer("postgres:17-alpine")
}
