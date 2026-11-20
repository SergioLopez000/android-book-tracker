package com.librosleidos.backend

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration

// Boot would otherwise generate an in-memory user with a random password,
// which is an authentication path no one asked for. Authentication arrives in #26.
@SpringBootApplication(exclude = [UserDetailsServiceAutoConfiguration::class])
class BackendApplication

// The spread copies the argv array once at startup; that is the standard Boot entry point.
@Suppress("SpreadOperator")
fun main(args: Array<String>) {
    runApplication<BackendApplication>(*args)
}
