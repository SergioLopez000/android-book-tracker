// Root build file. Plugins are declared here with `apply false` and
// applied per-module (currently only `:app`).
plugins {
    // AGP 9.0+ ships built-in Kotlin support, so no separate
    // `org.jetbrains.kotlin.android` plugin is declared here.
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.spotless)
}

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**")
        ktlint(libs.versions.ktlint.get())
    }
    kotlinGradle {
        target("*.gradle.kts", "app/*.gradle.kts")
        ktlint(libs.versions.ktlint.get())
    }
}
