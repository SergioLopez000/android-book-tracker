package com.librosleidos.backend.domain

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.readLines
import kotlin.test.Test
import kotlin.test.assertTrue

/** Architecture guard: the domain must not import Spring, JDBC, servlet or HTTP types. */
class DomainIsFrameworkFreeTest {
    private val forbidden =
        listOf(
            "org.springframework",
            "java.sql",
            "javax.sql",
            "jakarta.servlet",
            "jakarta.persistence"
        )

    @Test
    fun `domain sources import no framework or IO types`() {
        val root = Path.of("src/main/kotlin/com/librosleidos/backend/domain")
        assertTrue(Files.isDirectory(root), "domain source root not found: $root")
        val violations =
            Files.walk(root).use { paths ->
                paths
                    .filter { it.extension == "kt" }
                    .toList()
                    .flatMap { file ->
                        file.readLines()
                            .filter { it.trimStart().startsWith("import ") }
                            .filter { line -> forbidden.any { line.contains("import $it") } }
                            .map { "$file: $it" }
                    }
            }
        assertTrue(
            violations.isEmpty(),
            "Forbidden domain imports:\n" + violations.joinToString("\n")
        )
    }
}
