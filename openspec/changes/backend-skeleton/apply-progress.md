# Apply progress: backend-skeleton, PR1a (tasks 1-4) and PR1b (tasks 5-6)

Branch `feat/6-backend-skeleton`. Commits (local, not pushed):
- c5a600a `build(backend): add isolated Gradle build with Kover and PIT scaffold`
- 31916d6 `feat(backend): deny-by-default stateless security with health-only actuator`
- 7d61f8c `ci(backend): add backend-tests job, backend coverage report and Dependabot`

Size vs a8a956f: 908 added+deleted lines raw; 539 authored (wrapper scripts and properties are 369 generated lines, jar is binary). Budget 800.

## Done
1.1 SP4 PASS, 1.2 SP1 PASS, 2.1-2.4, 3.1-3.5, 4.1-4.3, 4.5.

## Second batch (PR1a remainder)
- 9ff5d4d `ci(codeql): trace backend build on JDK 21 alongside the app build` (13 added, 2 deleted, authored). Total PR1a authored now ~554; generated wrapper lines excluded.
- 1.3 SP5 PASS (local CodeQL 2.27.5, one manual DB, two traced builds: app JDK 17 and backend JDK 21, both `--no-daemon --no-build-cache --rerun-tasks`). `src.zip` holds `backend/src/main/kotlin/.../{BackendApplication,config/SecurityConfig,config/ApiAuthenticationCustomizer}.kt` and 41 app Kotlin files (app/src/main and generated KSP). No fallback needed (matrix not required).
- 1.4 SP6 PASS (Dependabot CLI docker dry run on scratch Dockerfile pinned to old index digest 0101e1c5..., not committed): `created | distroless/java21-debian13 (from 0101e1c to cddca4c)`.
- 4.4 done; RED = grep for `-p backend` in codeql.yml exit 1, GREEN exit 0 plus actionlint clean. JDKs picked through step `JAVA_HOME: ${{ env.JAVA_HOME_<n>_X64 }}`.
- 4.6 done: actionlint, shellcheck, test-kover-summary.sh green; `git diff a8a956f -- .github/workflows` adds no permissions line.
- Caveat: the hosted workflow itself is not run (no push); locally verified through the same commands. Local tracing on Apple Silicon without Rosetta needs the build command to be a non-system binary (java directly, not env/bash/gradlew).

## Evidence (strict TDD)
- SP4: weak test, `./gradlew pitest` exit non-zero ("Mutation score of 14 is below threshold of 80"); strong test, BUILD SUCCESSFUL, 7/7 killed; XML+HTML written; no equals/hashCode/toString/copy/component or kotlin.jvm.internal mutants. Scaffold class and test removed after the spike.
- SP1 / 3.1: RED = NoDefaultUserTest (Boot default UserDetailsService present) and health body test (extra `groups`) failed; GREEN after BackendApplication exclude, SecurityConfig (Kotlin DSL works), application.yaml (probes off). 6 backend tests pass.
- 3.4: RED = injected `security.enabled: false` and a `com.librosleidos.testsupport.Leak` class made `verifyPackagedArtifact` fail; GREEN after removal.
- 2.1 RED: misformatted `backend/build.gradle.kts` passed root `spotlessCheck`; GREEN after adding `backend/*.gradle.kts` to kotlinGradle target (now fails on misformat).
- 4.1: no observable RED; the parser is already report-agnostic, so the JVM fixture is a characterization test (passes).
- App: `:app:testDebugUnitTest` 102 tests, 0 failures; root `projects` lists only `:app`.

## PR1b (tasks 5-6), branch `feat/6-backend-domain-persistence`
- d0151e8 `feat(backend): add framework-free books domain and use cases`; 4e551c5 `feat(backend): add owner-scoped PostgreSQL persistence with Flyway`. Authored 745 lines plus docs, under 800.
- 5.1-5.6 done. RED = unresolved references (value objects, use cases) then GREEN; 5.5 guard RED proven by injecting a Spring import into `Book.kt`. Outcomes are sealed for get/update/delete; create returns `Book`, list returns `List<Book>`. Field bounds: title/author 200, cover 2048, review 5000.
- 6.1-6.6 done. RED = unresolved `BookSql`/adapter; GREEN with V1 (owner_id NOT NULL, indexed), `JdbcClient` adapter using `RETURNING`, `UseCaseConfig`. Mutation check: a guard-passing leak (`... OR id = :id` on DELETE) fails the cross-owner integration test.
- `NoDatabaseTestConfig` (test sources only) excludes DataSource/Flyway and mocks `JdbcClient`, so unit-suite security slices still run without a database.
- detekt: `config/detekt/detekt.yml` excludes `integrationTest`/`contractTest` from FunctionNaming and MagicNumber (default excludes miss those suites).
- Evidence: backend `test` 28/0, `integrationTest` 6/0 on real PostgreSQL 17 (Testcontainers, Ryuk on), `build` green incl. `verifyPackagedArtifact`; `:app:testDebugUnitTest` 102/0; spotless, actionlint, kover-summary green. Context7 tool was not available to the executor; versions were taken from research.md and confirmed by Gradle resolution (Boot BOM manages Testcontainers 2.x, Flyway, PostgreSQL).
- Local Docker (Rancher Desktop) needs `DOCKER_HOST=unix://$HOME/.rd/docker.sock` and `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`; without the override every integration test fails. Not committed.
- PIT run on domain (not gated until PR2 9.4) shows survivors; to be killed in PR2.
