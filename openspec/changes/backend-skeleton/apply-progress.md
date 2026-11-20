# Apply progress: backend-skeleton, PR1a (tasks 1-4)

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
