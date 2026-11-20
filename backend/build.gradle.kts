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
    implementation(libs.kotlin.reflect)

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
            }
        }
    }
}

tasks.bootJar {
    archiveFileName = "backend.jar"
}

tasks.named("check") {
    dependsOn(testing.suites.named("integrationTest"))
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
