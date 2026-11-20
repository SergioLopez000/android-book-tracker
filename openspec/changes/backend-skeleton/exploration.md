# Exploration: backend-skeleton (#6)

## Summary

Add the Kotlin + Spring Boot backend as a separate Gradle build under `/backend` (own wrapper, settings and catalog). Use hand-written controllers validated against `openapi.yaml` with swagger-request-validator instead of openapi-generator. Deliver as 3 chained PRs of at most 800 lines each; a single PR would be about 1,300-1,600 lines.

## Current State (verified)

- `settings.gradle.kts`: `include(":app")` only; repositories google, mavenCentral and the plugin portal with `FAIL_ON_PROJECT_REPOS`.
- `gradle/libs.versions.toml`: agp 9.1.1, kotlin 2.4.21, ksp 2.3.14, kover 0.9.11, spotless 8.11.0, ktlint 1.8.0. Plugins declared `apply false` at the root; AGP uses built-in Kotlin.
- Gradle wrapper 9.9.1; parallel and caching on; configuration cache not enabled. JDK 17.
- Root spotless: kotlin target `**/*.kt` already covers backend files; `kotlinGradle` target must add `backend/*.gradle.kts`. ktlint style `android_studio`.
- `.gitignore` must add `backend/build/` and `.kotlin/`.
- `openspec/config.yaml` context is stale (says Kotlin 2.3.20, Gradle 9.6.0).

## Build Layout

| Option | Pros | Cons |
|---|---|---|
| A. `:backend` module in the same build | One wrapper and catalog; root spotless and Kover apply as-is | AGP and Spring Boot plugin share the root plugin classpath; every task configures `:app` and needs the Android SDK (also inside Docker); Boot BOM manages Kotlin 2.3.21 vs repo 2.4.21 |
| B. Separate build in `/backend` (recommended) | Isolated plugins and Kotlin alignment; Docker, CodeQL and CI treat it as its own unit; explicit Dependabot directory; can be split into its own repo later | Second wrapper and catalog; spotless and lefthook need `./gradlew -p backend`; possible version drift |

## CI and Gates Impact

- `ci.yml`: new `backend-tests` job (JDK 17, `-p backend`, test + integrationTest + Kover XML; Testcontainers works on ubuntu-24.04). Contract and PIT job(s). Add to `ci-gate` and `report` needs. Upload `allure-results-backend`, `coverage-backend`, `allure-results-contract`, `pit-contract`.
- `report` job and `scripts/kover-summary.sh` hard-code Android Kover names (`htmlDebug`, `reportDebug.xml`); JVM names are `html` and `report.xml`.
- `codeql.yml`: add `./gradlew -p backend compileKotlin compileTestKotlin` next to `:app:compileDebugKotlin`, same flags.
- `dependabot.yml`: gradle and docker entries for `/backend`, target `dev`, groups for spring, flyway, testcontainers, pitest, rest-assured; scope the `kotlin-ksp` group per directory.
- `promote.yml`, `pr-cleanup.yml`, `board-sync.yml`: no change.
- Spec impact: `openspec/specs/ci-pipeline` forbids extra jobs; MODIFIED requirements needed (Unit Test Job, ci-gate needs, Out of Scope Boundaries, CodeQL, Dependabot, least privilege for GHCR `packages: write`). Backend spotless/detekt stay local only.
- Local gates: lefthook spotless covers backend; detekt may need baseline/config tuning for Spring code; gitleaks: no static DB passwords committed (deployment secrets belong to #54).
- README (Spanish) needs updates: what-runs-where table, artifact list, required checks.

## Contract-First Tooling

| Option | Pros | Cons |
|---|---|---|
| openapi-generator `kotlin-spring` | Compile-time drift detection | Verbose generated code with Jackson 2 annotations (Boot 4 uses Jackson 3); Boot 4 support unverified; exclusions needed in Spotless, detekt, Kover, PIT |
| Hand-written controllers + swagger-request-validator (recommended) | Boot 4 friendly; spec enforced at runtime (filter returns 400) and in tests (REST Assured filter validates request and response) | Controller/spec drift only caught by tests; needs a test that every spec operation has a route; Boot 4 compatibility unverified |

Fallback hardening: `fail-on-unknown-properties=true`, `@Size` limits, `@RestControllerAdvice` returning a generic `ProblemDetail`, `server.error.include-*=never`. REST Assured 5.x maps with Jackson 2: prefer JSON-path assertions.

## PIT and Testcontainers

- `info.solidsoft.pitest` 1.19.0 (PIT 1.22.1, initial Gradle 9 support) + `pitest-junit5-plugin`.
- `targetClasses`: domain and use cases only; `mutationThreshold` gate (e.g. 80); XML + HTML; `timestampedReports=false`; exclude Kotlin boilerplate (`copy`, `component*`, `equals`, `hashCode`, `toString`, `$default`; `avoidCallsTo` kotlin.jvm.internal). `excludedMethods` syntax unverified.
- PIT runs only unit tests (no Docker). Contract and integration tests run separately.
- Testcontainers 2.0.x (artifact `testcontainers-postgresql`, package `org.testcontainers.postgresql`), JUnit 5 only; `@ServiceConnection` and a singleton container.

## Versions

- Spring Boot 4.1.2 (Java 17+; manages Kotlin 2.3.21, Flyway 12.4.0, PostgreSQL driver 42.7.14).
- Repo Kotlin 2.4.21 is ahead of the Boot-managed version; Spring Framework 7 needs Kotlin 2.2+. Verify in a spike.
- Unverified: Flyway modules under Boot 4 (`flyway-database-postgresql`, `spring-boot-starter-flyway`), Testcontainers GA version, swagger-request-validator, REST Assured, Spring Security 7.

## Size and Delivery

Estimate 1,300-1,600 changed lines (excluding binaries). Proposed chained PRs into `dev`, each keeping CI green:

1. PR1 (~650): `/backend` build, domain, ports, use cases (TDD), JDBC adapter with `owner_id` filtering, first Flyway migration, deny-by-default security, actuator health only, CI `backend-tests`, CodeQL, Dependabot gradle.
2. PR2 (~600): `openapi.yaml`, controllers, validation and 400 handling, REST Assured contract tests, PIT gate with CI job.
3. PR3 (~200): Dockerfile (non-root, pinned digest), GHCR workflow, Dependabot docker, spec deltas and README for publishing.

## Open Decisions

1. Build layout: A or B (recommended B).
2. Contract tooling: openapi-generator or hand-written + swagger-request-validator (recommended).
3. Persistence: Spring `JdbcClient` (recommended), Spring Data JDBC, JPA or jOOQ.
4. Backend JDK: 17 (recommended) or 21.
5. Docker base image and build style (pinned digest, Temurin JRE 17 or distroless, non-root; jar COPY or multi-stage).
6. GHCR trigger and tags (recommended: push on `dev`, tag by SHA, retag on promote; package visibility).
7. Deny-by-default: confirm no deployment until #26 and #54.
8. PIT threshold (recommended 80) and whether it gates `ci-gate`.
9. Which jobs gate `ci-gate` (recommended backend tests, contract, PIT).
10. Issue DoD mentions SonarCloud, which the ci-pipeline spec puts out of scope.
11. Confirm the 3-PR split and use of the chained-pr skill.
12. `openapi.yaml` location (recommended `backend/src/main/resources/openapi.yaml`).
13. Spec deltas: update `ci-pipeline` (and possibly `code-quality-gates`).

## Risks

- Kotlin 2.4.21 vs Boot-managed 2.3.21 alignment unverified.
- Flyway 12 / Boot 4 starter modularization, Jackson 3 vs REST Assured Jackson 2, swagger-request-validator on Boot 4 unverified.
- Report job and `kover-summary.sh` miss backend coverage without changes.
- `ci-pipeline` spec forbids the new jobs; deltas mandatory.
- API unusable until #26; deployment waits for #26 and #54.
- Second wrapper adds a binary file and duplicate version management.
