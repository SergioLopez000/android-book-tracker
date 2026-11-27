import java.util.Properties

plugins {
    // AGP 9.0+ ships built-in Kotlin support; the separate
    // `org.jetbrains.kotlin.android` plugin is no longer applied here
    // (see design R1 spike notes for the discovery).
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kover)
    alias(libs.plugins.google.services)
}

// Google Books API key is optional. It is read from a git-ignored
// `local.properties` so a fresh clone builds and tests green without it
// (Google Books fallback is then simply disabled — see design D2).
val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}
val googleBooksApiKey: String = localProperties.getProperty("GOOGLE_BOOKS_API_KEY", "")

android {
    namespace = "com.librosleidos"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.sergiolopez.rincondelectura"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "GOOGLE_BOOKS_API_KEY", "\"$googleBooksApiKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        baseline = file("lint-baseline.xml")
        lintConfig = file("lint.xml")
        abortOnError = true
        warningsAsErrors = true
    }

    // Robolectric needs merged Android resources on the unit-test classpath.
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Export Room's schema history so future migrations stay diffable
// (design: Migration / Rollout — no fallbackToDestructiveMigration).
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Report-only coverage (no koverVerify threshold). Generated classes are
// excluded so the report reflects hand-written code.
kover {
    reports {
        filters {
            excludes {
                classes(
                    "*_Impl",
                    "*_Impl\$*",
                    "*ComposableSingletons*",
                    "*.BuildConfig",
                    "*.R",
                    "*.R\$*"
                )
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    // Compose BOM is the sole source of every Compose artifact version,
    // including the test artifacts below (design R1 resolution).
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    // Firebase BoM is the sole source of every Firebase artifact version
    // (design D10); credentials and googleid back Google sign-in (slice 2b).
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.appcheck.playintegrity)
    debugImplementation(libs.firebase.appcheck.debug)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    // Coil3 smoke test for the toolchain bump (PR3a): proves the dependency
    // graph resolves and the project compiles with Coil present. No
    // production usage (BookCoverImage.kt) is added in this PR — that is
    // PR3b's scope.
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.test.runner)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)
    // Real org.json implementation for plain-JVM unit tests: the Android
    // SDK stub jar throws at runtime outside Robolectric (Phase 4 adapters
    // are pure-JVM tests, see data/remote runtime harness).
    testImplementation(libs.org.json)
    // ViewModel state-flow assertions (Phase 5, design: ViewModel tests use
    // coroutines-test + Turbine + fakes).
    testImplementation(libs.turbine)
    testImplementation(libs.coil.test)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    lintChecks(libs.compose.lint.checks)
}
