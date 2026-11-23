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

## PR2a (tasks 7-8), branch `feat/6-backend-openapi-web`
- 00d1fb2 `feat(backend): add OpenAPI books contract, controller and request validation`. About 836 changed lines (limit 800). The user chose to ship as is with `size:exception` (2026-11-20); no test was moved or trimmed.
- 7.1 SP2 PASS: `javap` major versions core 65 (Java 21), spring-webmvc 61, restassured 65. Jackson 2.21.7 (validator, REST Assured) and 3.1.7 (Boot) coexist and Boot serialization is intact. Filter order 0 after security (-100): anonymous invalid body gets 401 with an empty body. Violations (unknown field, oversize, wrong type, malformed JSON) raise `InvalidRequestException`, caught by `@RestControllerAdvice`, giving a constant 400 ProblemDetail. Authenticated unknown route gives 404; a mapped route absent from the spec passes with `validation.request.path.missing` IGNORE. REST Assured 6.3.0 `OpenApiValidationFilter` accepted a valid request/response and threw on a violation. Scratch removed; REST Assured stays out of PR2a (PR2b).
- 8.1 RED: `OpenApiCoverageTest` failed "openapi.yaml must exist and parse" (fixtures passed). 8.2 RED: 23 of 54 failed. GREEN: `openapi.yaml`, `BooksController`, `BookModels`, `ApiErrorHandler` (extends `ResponseEntityExceptionHandler`, constant details per status), `OpenApiValidationConfig`.
- Mutation check: removing the interceptor registration fails 2 validator-only cases (rating as string, missing title).
- Evidence: backend `test` 55/0, `integrationTest` 6/0, `build` green (incl. `verifyPackagedArtifact`), spotless green, `:app:testDebugUnitTest --rerun-tasks` 102/0.
- Gotchas: test `@TestConfiguration` classes must live outside the scan root (`com.librosleidos.testsupport`); actuator adds a second `RequestMappingHandlerMapping` (use `@Qualifier`); KDoc with `/api/**` opens a nested comment in Kotlin.
- `jackson-module-kotlin` (Boot-managed) added for Kotlin data-class binding; `openapi.yaml` has no `security` section (auth scheme is #26).

## PR2b (task 9), branch `feat/6-backend-contract-pit`
- 667d619 `test(backend): add REST Assured contract tests and PIT mutation gate` (683 changed lines incl. scripts and CI; budget 800).
- 9.1 RED: `./gradlew contractTest` failed (task did not exist, suite unregistered). 9.3 GREEN: `contractTest` JvmTestSuite (REST Assured 6.3.0, `openapi-request-validator-restassured` 3.0.0, Testcontainers PostgreSQL 17, `RANDOM_PORT`, test-only httpBasic user in the suite's own `testsupport` package, wired into `check`). 25 contract tests pass first run; one class per operation in `contract.books` plus `BookLifecycleFlowTest`.
- Validation: `api()` validates request and response; `invalidApi()` ignores `validation.request` (400 cases) but still validates the response. Mutation check: adding an extra field to `BookResponse` failed 5 of 25 (additionalProperties false), then reverted.
- 9.2: `ContractCoverageTest` (spec operationIds vs `contract.books.<Op>Test`, untested fixture names the operation, drift fixture with a JDK HttpServer returning `rating:"five"` makes the filter throw naming `rating`). RED was the missing suite; no separate failing run per case.
- 9.4 RED: with `ValueObjectsTest`/`BookDraftTest` moved away, `pitest` exits non-zero: "Mutation score of 62 is below threshold of 80". Current tests already gave 82% overall (PR1b per-class 43-45% was local to classes), 3 getter survivors in `BookDraft` plus 7 no-coverage getters; added `AccessorsTest`. 9.5 config was already in PR1a (threshold 80, junk excludes) and is proven both ways.
- PIT final: `domain.model` 48/51 (94%), `domain.usecase` 4/4 (100%), total 52/55 (95%). The 3 survivors are `getValue` of inline value classes (`Rating`, `OwnerId`, `BookId`), unreachable as mangled synthetic getters (equivalent).
- 9.6: `ci.yml` job `contract-tests` (no `needs`, JDK 21, `contractTest pitest koverXmlReport koverHtmlReport --continue`, always-uploads `allure-results-contract`, `coverage-contract`, `pit-contract`); `ci-gate` and `report` need it; report downloads `pit-*`, `scripts/pit-summary.sh` (+ `test-pit-summary.sh`) surfaces the score. `permissions` unchanged (`contents: read`).
- Evidence: backend `build contractTest pitest --rerun-tasks` green (test 58/0, integrationTest 6/0, contractTest 25/0); `:app:testDebugUnitTest --rerun-tasks` 102/0; spotless, detekt, shellcheck, actionlint, kover and pit script tests green. Throwaway weak run with `--continue`: exit 1, `build/reports/pitest` still written (so `pit-contract` uploads).
- Caveat: Kover tasks run `test` and `integrationTest` again in the contract job (coverage-contract therefore covers all suites); hosted workflow not run (no push).

## PR3 (tasks 10-11), branch `feat/6-backend-image-docs`
- 98ab91f `ci(backend): publish hardened image to GHCR and retag on promote`; cfbd18d `docs(backend): document backend commands, image flow and Dependabot retargeting`. `git diff --shortstat origin/dev..HEAD` before the progress commit: 9 files, 356 insertions, 9 deletions (budget 800).
- 10.1 RED: `scripts/test-image-config.sh` failed 18 checks (Dockerfile, publish-image, check-image, retag-image and permissions absent). 10.3 RED: `scripts/test-image-smoke.sh` failed "open Dockerfile: no such file". 10.2 GREEN: `backend/Dockerfile` (single stage, `gcr.io/distroless/java21-debian13:nonroot@sha256:cddca4cc6b1dfc76142e5c1a1d00ce7a79bcc1292ad65d34227794f9d371a496`, re-verified current via `docker buildx imagetools inspect` and the gcr.io registry HEAD `docker-content-digest`; matches SP6) and `.dockerignore` (only the jar enters the context). Config checks: 12 failures left until the workflows existed, then 0.
- 10.4 `publish-image` in `ci.yml` (`needs: ci-gate`, dev push only, job-level `contents: read` + `packages: write`, `docker login` with `GITHUB_TOKEN`, `docker buildx build --platform linux/amd64 --push`, tags `sha-${GITHUB_SHA}` and `dev`, image `ghcr.io/${GITHUB_REPOSITORY,,}/backend`). No third-party actions added (shell docker only). ci-gate does not need it.
- 10.5 `promote.yml`: `check-image` (`packages: read`, `contents: read`; resolves the SHA, `docker manifest inspect sha-<sha>`) and `promote` `needs: check-image`, taking the SHA from `check-image` outputs, existing ci-gate check and `staging` environment untouched; `retag-image` (`needs: promote`, `contents: read` + `packages: write`) installs crane v0.22.1 from the release tarball verified with sha256 `0ab7a1d6...cda0` (matches the release checksums.txt and a local download), `crane tag`, then asserts `crane digest` equality of `sha-<sha>` and `staging`.
- 10.6 Dependabot docker `/backend` (weekly Monday, dev, prefix build, include scope, group `base-image`, limit 3).
- 11.1-11.2 README (Spanish, like the rest) backend section, image table, CI table/diagram/bullets, Dependabot bullet; `openspec/config.yaml` context refreshed (Kotlin 2.4.21 app, 2.3.21 backend, Gradle 9.9.1, backend commands and layers). The Dependabot security-PR retargeting section already existed. SP6 passed, so no manual digest bump procedure.
- Local image checks (Rancher Desktop, linux/arm64 locally, CI builds amd64): `docker inspect` user `nonroot` (passwd in base layer: uid 65532), labels only `org.opencontainers.image.source`, no secret env, entrypoint `/usr/bin/java -jar /app/backend.jar`; `docker save` layer: jar has 0 entries matching testsupport/test; smoke script: health 200 and anonymous `/api/books` 401 against PostgreSQL 17.
- Evidence: backend `build contractTest` green (test 58/0, integrationTest 6/0, contractTest 25/0); `:app:testDebugUnitTest --rerun-tasks` 102/0; actionlint, shellcheck, test-image-config green.
- Fix during GREEN: the first smoke script probed `/api/v1/books` (not in the spec; passed 401 only because everything is denied); corrected to `/api/books`.
- Only provable after merge: hosted `publish-image` push to GHCR (needs `packages: write` repo setting and a push to dev), `check-image` and `retag-image` on a real promote, package linking to the repo via the source label, crane `tag`/`digest` against GHCR. The main spec "Out of Scope" job list is not updated here (belongs to spec sync at archive).

## TDD Cycle Evidence (all PRs)

Consolidated from the per-PR evidence above (added after verify finding W1). Only recorded evidence is tabulated; RED and GREEN usually share a commit per work unit, so commit SHAs are the PR commits listed in each section. Test paths are relative to `backend/src/<suite>/kotlin/com/librosleidos/backend/` unless they start with `scripts/`.

| Task | Test file | Layer | Safety Net | RED | GREEN | TRIANGULATE | REFACTOR |
|------|-----------|-------|------------|-----|-------|-------------|----------|
| 1.1 SP4 | throwaway PIT scaffold test (removed) | Mutation | N/A (new build) | ✅ weak test: `pitest` exit non-zero, score 14 < 80 | ✅ strong test, 7/7 killed | ➖ Single (spike) | ✅ scaffold removed |
| 1.2 / 3.1 | `test/.../config/NoDefaultUserTest.kt`, `test/.../config/SecuritySliceTest.kt` | Unit (slice) | ✅ app 102/0 | ✅ default UserDetailsService present; health body had extra `groups` | ✅ 6/6 after exclude, `SecurityConfig`, probes off | ✅ `/api/books`, unknown path, `env`, `info`, health | ✅ 3.5 clean |
| 3.2 | `test/.../config/AuthenticatedActuatorTest.kt` | Unit (slice) | ✅ app 102/0 | ✅ Written before `SecurityConfig` | ✅ Passed (404 for `env`, `beans`, `info`) | ✅ 3 endpoints | ✅ 3.5 clean |
| 3.4 | Gradle `verifyPackagedArtifact` | Build check | N/A (new) | ✅ injected `security.enabled: false` + `testsupport.Leak` fail the check | ✅ passes after removal | ✅ 2 leak kinds | ➖ None needed |
| 2.1 | root `spotlessCheck` on `backend/*.gradle.kts` | Build check | ✅ app 102/0 | ✅ misformatted `backend/build.gradle.kts` passed (gap shown) | ✅ fails on misformat after target added | ➖ Single | ✅ 2.4 catalog deduped |
| 4.1 / 4.2 | `scripts/test-kover-summary.sh` (JVM fixture) | Unit (shell) | ✅ existing kover cases green | ➖ No observable RED (parser already report-agnostic; characterization test) | ✅ Passed | ➖ Single fixture | ✅ 4.6 actionlint/shellcheck |
| 4.4 | grep check on `codeql.yml` + actionlint | Workflow check | N/A | ✅ grep `-p backend` exit 1 | ✅ exit 0, actionlint clean | ➖ Single | ✅ 4.6 |
| 5.1 / 5.2 | `test/.../domain/model/ValueObjectsTest.kt`, `BookDraftTest.kt` | Unit | ✅ backend suite + app 102/0 | ✅ unresolved value objects | ✅ Passed | ✅ rating 0.4/0.75/5.5 rejected, blank owner, field bounds | ✅ 5.6 clean |
| 5.3 / 5.4 | `test/.../domain/usecase/BookUseCasesTest.kt` | Unit (fake repo) | ✅ | ✅ unresolved use cases | ✅ Passed | ✅ cross-owner get/update/delete | ✅ 5.6 clean |
| 5.5 | `test/.../domain/DomainIsFrameworkFreeTest.kt` | Architecture | ✅ | ✅ injected Spring import in `Book.kt` fails | ✅ passes after removal | ➖ Single guard | ➖ None needed |
| 6.1 | `test/.../data/jdbc/BookSqlGuardTest.kt` | Unit | ✅ | ✅ unresolved `BookSql` | ✅ Passed | ✅ SELECT/UPDATE/DELETE/INSERT | ✅ 6.4 guard still green |
| 6.2 / 6.3 | `integrationTest/.../data/jdbc/JdbcBookRepositoryTest.kt` | Integration (Testcontainers PG 17) | ✅ test 28/0, app 102/0 | ✅ unresolved adapter | ✅ 6/6 on real PostgreSQL | ✅ mutant `OR id = :id` on DELETE caught by cross-owner test | ✅ 6.4 |
| 8.1 | `test/.../web/OpenApiCoverageTest.kt` | Unit | ✅ test 28/0 | ✅ "openapi.yaml must exist and parse" | ✅ Passed | ✅ route-without-op and op-without-route fixtures | ✅ 8.5 |
| 8.2 | `test/.../web/BooksWebTest.kt`, `UnexpectedErrorTest.kt` | Unit (web slice) | ✅ | ✅ 23 of 54 failed | ✅ test 55/0 | ✅ unknown field, oversize, wrong type, malformed JSON; mutant (interceptor removed) caught by 2 cases | ✅ 8.5 |
| 9.1 / 9.3 | `contractTest/.../contract/books/{Create,List,Get,Update,Delete}BookTest.kt`, `BookLifecycleFlowTest.kt` | Contract (REST Assured + Testcontainers) | ✅ test 55/0, integrationTest 6/0 | ✅ `contractTest` task did not exist | ✅ 25/25 | ✅ mutant (extra `BookResponse` field) failed 5 of 25 | ✅ 9.7 |
| 9.2 | `contractTest/.../contract/ContractCoverageTest.kt` | Contract meta | ✅ | ✅ suite missing (no separate per-case failing run) | ✅ Passed | ✅ untested-op fixture + drift fixture (`rating:"five"`) | ✅ 9.7 |
| 9.4 / 9.5 | `test/.../domain/model/AccessorsTest.kt` + PIT gate | Mutation | ✅ test 58/0 | ✅ score 62 < 80 with value-object tests removed | ✅ 52/55 (95%) | ✅ gate proven both ways (14 and 62 fail, 95 passes) | ➖ None needed |
| 10.1 / 10.2 / 10.4-10.6 | `scripts/test-image-config.sh` | Config check (shell) | ✅ backend build, app 102/0 | ✅ 18 checks failed | ✅ 0 failures | ✅ Dockerfile, publish-image, check-image, retag-image, permissions | ✅ 10.7 |
| 10.3 | `scripts/test-image-smoke.sh` | Smoke (Docker + PG 17) | ✅ | ✅ "open Dockerfile: no such file" | ✅ health 200, anonymous `/api/books` 401 | ➖ Single | ✅ fixed `/api/v1/books` probe to `/api/books` |

### Test Summary
- **Total tests written (backend)**: 89 JVM tests (unit 58, integration 6, contract 25) plus 3 shell check scripts and 1 smoke script.
- **Total tests passing**: 89/89 backend, 102/102 app (`--rerun-tasks`); PIT 95% (threshold 80).
- **Layers used**: Unit (58), Integration (6), Contract (25), Mutation (55 mutants), Shell/smoke (4 scripts).
- **Approval tests** (refactoring): None — no refactoring of existing behavior.
- **Pure functions created**: domain value objects and use cases are framework-free (guarded by `DomainIsFrameworkFreeTest`).
- **Known gaps**: 4.1 has no observable RED (characterization); 9.2 RED was the missing suite, not a per-case failure; spikes 1.3, 1.4, 7.1 are evidence runs, not TDD cycles.
