import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.kover)
    alias(libs.plugins.pitest)
}

group = "com.librosleidos"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

dependencies {
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.kotlin.reflect)
    implementation(libs.openapi.validator.core)
    implementation(libs.openapi.validator.webmvc)
    runtimeOnly(libs.flyway.postgresql)
    runtimeOnly(libs.postgresql)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.boot.starter.security.test)
    testImplementation(libs.kotlin.test.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)
}

testing {
    suites {
        named<JvmTestSuite>("test") {
            useJUnitJupiter()
        }
        register<JvmTestSuite>("integrationTest") {
            useJUnitJupiter()
            dependencies {
                implementation(project())
                implementation(platform(SpringBootPlugin.BOM_COORDINATES))
                implementation(libs.spring.boot.starter.test)
                implementation(libs.kotlin.test.junit5)
                implementation(libs.spring.boot.starter.jdbc)
                implementation(libs.spring.boot.testcontainers)
                implementation(libs.testcontainers.junit)
                implementation(libs.testcontainers.postgresql)
                runtimeOnly(libs.postgresql)
            }
        }
        register<JvmTestSuite>("contractTest") {
            useJUnitJupiter()
            dependencies {
                implementation(project())
                implementation(platform(SpringBootPlugin.BOM_COORDINATES))
                implementation(libs.spring.boot.starter.test)
                implementation(libs.spring.boot.starter.security)
                implementation(libs.kotlin.test.junit5)
                implementation(libs.spring.boot.starter.jdbc)
                implementation(libs.spring.boot.testcontainers)
                implementation(libs.testcontainers.junit)
                implementation(libs.testcontainers.postgresql)
                implementation(libs.rest.assured)
                implementation(libs.openapi.validator.restassured)
                implementation(libs.openapi.validator.core)
                runtimeOnly(libs.postgresql)
            }
        }
    }
}

tasks.bootJar {
    archiveFileName = "backend.jar"
}

tasks.named("check") {
    dependsOn(testing.suites.named("integrationTest"), testing.suites.named("contractTest"))
}

kover {
    reports {
        filters {
            excludes {
                // Generated code never counts toward coverage.
                classes("*\$\$*", "*.BuildConfig")
            }
        }
    }
}

pitest {
    pitestVersion = libs.versions.pitestEngine
    junit5PluginVersion = libs.versions.pitestJunit5
    targetClasses = setOf("com.librosleidos.backend.domain.*")
    targetTests = setOf("com.librosleidos.backend.domain.*")
    mutationThreshold = 80
    outputFormats = setOf("XML", "HTML")
    timestampedReports = false
    excludedMethods = setOf("equals", "hashCode", "toString", "copy*", "component*")
    avoidCallsTo = setOf("kotlin.jvm.internal")
    useClasspathFile = true
}

// Packaged-artifact guard: the jar must hold no test-only authentication, no property
// that could relax security and no credential. Anything found here fails `check`.
// Credential findings name the entry, line and rule, never the matched value.
val verifyPackagedArtifact by tasks.registering {
    group = "verification"
    description =
        "Fails if the boot jar has test-only users, a security bypass property or a credential."
    val jar = tasks.bootJar.flatMap { it.archiveFile }
    dependsOn(tasks.bootJar)
    doLast {
        val forbiddenClasses = Regex("""(?i)(testsupport|TestApiAuthentication|TestApi)""")
        val bypass =
            Regex(
                """(?i)(spring\.security\.user|security\.(enabled|disabled|ignored)|permit-?all|disable-?security|management\.security)"""
            )
        // Text entries only, and only the application's own classes: BOOT-INF/lib is third-party.
        val scannedEntry = Regex("""BOOT-INF/classes/.+\.(ya?ml|properties|json|xml|sql)""")
        val credentialKey =
            Regex(
                """(?i)^\s*[\w.-]*(password|secret|token|api[-_.]?key|credential)[\w.-]*\s*[:=]\s*(?!\$\{[A-Z0-9_]+\}\s*$)\S"""
            )
        val jdbcEmbedded =
            listOf(
                Regex("""(?i)jdbc:[a-z0-9]+://[^/\s:@]+:[^@\s]+@"""),
                Regex("""(?i)jdbc:[^\s]*[?&;]password=""")
            )
        val datasourceKey = Regex("""^(\s*)(spring\.)?datasource:\s*$""")
        val datasourceLiteral =
            Regex(
                """(?i)^\s*(spring\.datasource\.|datasource\.)?(url|username|password)\s*[:=]\s*(?!\$\{[A-Z0-9_]+\}\s*$)\S"""
            )
        val violations = mutableListOf<String>()
        zipTree(jar).visit {
            if (isDirectory) return@visit
            val entry = relativePath.pathString
            if (forbiddenClasses.containsMatchIn(entry)) {
                violations +=
                    "test-only class packaged: $entry"
            }
            if (scannedEntry.matches(entry)) {
                var datasourceIndent = -1
                file.readLines().forEachIndexed { index, line ->
                    val at = "$entry:${index + 1}"
                    if (credentialKey.containsMatchIn(line)) {
                        violations += "credential-like key with a literal value in $at"
                    }
                    if (jdbcEmbedded.any { it.containsMatchIn(line) }) {
                        violations += "jdbc URL with embedded credentials in $at"
                    }
                    if (entry.substringAfterLast('/').startsWith("application")) {
                        val indent = line.length - line.trimStart().length
                        datasourceKey.find(line)?.let {
                            datasourceIndent = it.groupValues[1].length
                        }
                        val inBlock = datasourceIndent >= 0 && indent > datasourceIndent
                        if (line.isNotBlank() && !inBlock && datasourceKey.find(line) == null) {
                            datasourceIndent = -1
                        }
                        val direct = line.trimStart().startsWith("spring.datasource.")
                        if ((inBlock || direct) && datasourceLiteral.containsMatchIn(line)) {
                            violations += "datasource literal (url, username or password) in $at"
                        }
                    }
                }
            }
            if (Regex("""BOOT-INF/classes/application[^/]*\.(ya?ml|properties)""").matches(entry)) {
                file.useLines { lines ->
                    lines.filter { bypass.containsMatchIn(it) }.forEach {
                        violations +=
                            "bypass in $entry: ${it.trim()}"
                    }
                }
            }
        }
        check(violations.isEmpty()) {
            "Packaged artifact violations:\n" +
                violations.joinToString("\n")
        }
    }
}

tasks.named("check") {
    dependsOn(verifyPackagedArtifact)
}
