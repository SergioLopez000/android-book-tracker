package com.librosleidos.domain

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec: "Domain is Android-free". */
class DomainIsAndroidFreeTest {

    private val forbidden =
        Regex("""^\s*import\s+(android\.|androidx\.|com\.google\.android\.libraries\.identity)""")

    private fun offendingImports(root: File): List<String> = root.walkTopDown()
        .filter { it.extension == "kt" }
        .flatMap { file ->
            file.readLines().filter(forbidden::containsMatchIn).map { "${file.name}: $it" }
        }
        .toList()

    @Test
    fun `domain sources import no android or credential classes`() {
        val domain = File("src/main/java/com/librosleidos/domain")
        assertTrue("domain dir not found from ${File(".").absolutePath}", domain.isDirectory)

        assertEquals(emptyList<String>(), offendingImports(domain))
    }

    @Test
    fun `a planted android import is detected`() {
        val dir = createTempDirectory().toFile()
        File(
            dir,
            "Planted.kt"
        ).writeText("package x\nimport androidx.credentials.CredentialManager\n")

        assertEquals(1, offendingImports(dir).size)
        dir.deleteRecursively()
    }
}
