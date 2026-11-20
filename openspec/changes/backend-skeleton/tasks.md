# Tasks: backend-skeleton (#6)

Strict TDD is active. Every implementation task is preceded by its failing-test task (RED -> GREEN -> REFACTOR). Spikes are the first tasks of their PR. A failed spike STOPS apply until the orchestrator and user decide; the design.md fallback is the proposed option, not an automatic switch.

Spec refs use `BA` = backend-api, `CI` = ci-pipeline, `QG` = code-quality-gates (requirement names).

## Review Workload Forecast

| Field | Value |
|---|---|
| Estimated changed lines | PR1 ~1,150 (900-1,350); PR2 ~1,150 (950-1,350); PR3 ~330 (250-450) |
| Chained PRs recommended | Yes (already confirmed: 3 sequential PRs into `dev`, stacked-to-main) |
| 400-line budget risk | High for PR1 and PR2; Low for PR3 |
| 800-line budget risk (session `review_budget_lines`) | PR1 EXCEEDS 800; PR2 EXCEEDS 800; PR3 within budget |
| Decision needed before apply | Resolved 2026-11-20: the user chose the 5-PR split (PR1a, PR1b, PR2a, PR2b, PR3), all within 800 lines, sequential into `dev` (stacked-to-main). No `size:exception`. |

Honest breakdown (authored additions plus deletions; generated files such as the Gradle wrapper jar/scripts are excluded from the authored count but included in the diff):

| Work unit | Est. lines |
|---|---|
| PR1 WU1 build scaffold, catalog, wrapper config, spotless/lefthook/gitignore | ~200 |
| PR1 WU2 security + actuator + SP1 tests | ~230 |
| PR1 WU3 CI (`backend-tests`, `report`, `ci-gate`), CodeQL, Dependabot gradle, kover fixture | ~260 |
| PR1 WU4 domain model, port, use cases + unit tests | ~330 |
| PR1 WU5 JDBC adapter, Flyway V1, SQL guard, integration tests | ~330 |
| PR2 WU6 `openapi.yaml` + web models + controller + error handler + validator wiring | ~600 |
| PR2 WU7 contract tests (5 operation classes, flow, coverage) + PIT config + `contract-tests` CI | ~550 |
| PR3 WU8 Dockerfile, publish-image, promote retag, Dependabot docker | ~200 |
| PR3 WU9 README + `openspec/config.yaml` | ~130 |

Proposed split (one honest slicing pass, cohesive boundaries, each slice independently green):
- PR1a = WU1 + WU2 + WU3 (~690): build, security, CI wiring. `backend-tests` runs the security slice tests, so `ci-gate` is green.
- PR1b = WU4 + WU5 (~660): domain, use cases, persistence, integration tests. Stacks on PR1a.
- PR2a = WU6 (~600): contract, controller, validation, safe errors, with slice tests.
- PR2b = WU7 (~550): contract tests, PIT gate, `contract-tests` job. Stacks on PR2a.
- PR3 = WU8 + WU9 (~330).

If the user keeps 3 PRs, PR1 and PR2 each need an accepted `size:exception` (smallest honest count ~1,150 each; no cohesive 3-PR split fits 800 without removing tests or docs, which is forbidden).

Task numbering below follows the 3-PR plan; the `[PR1a|PR2a]` boundary markers are the confirmed cuts of the 5-PR delivery (user decision 2026-11-20).

---

## PR1: Build, security, persistence, CI (branch `feat/6-backend-skeleton`, `Refs #6`)

Start state: `dev` without `backend/`. End state: backend builds on JDK 21, deny-by-default security, owner-scoped persistence, `backend-tests` gating `ci-gate`, CodeQL and Dependabot cover the backend. Out of scope: OpenAPI, controllers, PIT, Docker, README.

### 1. Spikes (first, in order)

- [x] 1.1 **SP4 PIT on Gradle 9.9.1** (vehicle: minimal backend scaffold, kept). Create `backend/settings.gradle.kts`, `backend/build.gradle.kts`, `backend/gradle/libs.versions.toml`, backend wrapper (Gradle 9.9.1), `gradle.properties`, foojay toolchain 21, Boot 4.1.2, Kotlin 2.3.21 (Boot-managed), gradle-pitest-plugin 1.19.0 with `pitestVersion` 1.30.2 and pitest-junit5-plugin 1.2.3, and one trivial domain class plus one test. Pass: `./gradlew pitest` runs, writes XML+HTML, and `mutationThreshold` of 80 fails a deliberately weak run (exit non-zero) and passes a strong one; `excludedMethods` (equals, hashCode, toString, copy*, component*) and `avoidCallsTo` kotlin.jvm.internal yield no mutants on a data class. Fallback (STOP and ask): pin the backend wrapper to Gradle 8.14.x; else a `JavaExec` task with the PIT CLI plus `excludedClasses`. (BA: Separate Backend Build, Mutation Testing Gate)
- [x] 1.2 **SP1 Security Kotlin DSL** (RED then GREEN, throwaway test code kept as real tests in 2.x). Write a MockMvc test and a minimal `SecurityFilterChain` using `http { authorizeHttpRequests { ... } }`, `sessionManagement STATELESS`, `csrf { disable() }`, `exceptionHandling { authenticationEntryPoint = HttpStatusEntryPoint(UNAUTHORIZED) }`, with import `org.springframework.security.config.annotation.web.invoke`. Pass: anonymous GET "/api/books" and unknown path give 401 with no body; "/actuator/health" gives 200; no `Set-Cookie`. Fallback (STOP and ask): Java lambda `HttpSecurity` API; tests unchanged. (BA: Deny-by-Default Security)
- [x] 1.3 **SP5 CodeQL two builds in one job**. On a throwaway branch/fork run `codeql.yml` with two manual builds (`./gradlew --no-daemon --no-build-cache` for app on JDK 17, then `-p backend` on JDK 21), or locally with `codeql database create --no-daemon` and two `--command`. Pass: results list Kotlin files under `backend/src/main` and still under `app/`; Kotlin 2.3.21 (backend) and 2.4.21 (app) warning noted. Fallback (STOP and ask): matrix of two jobs with categories "/language:java-kotlin/app" and "/language:java-kotlin/backend"; check the dev ruleset "Require code scanning results" accepts two categories (open question). (CI: CodeQL Analysis Workflow) (PASS locally 2026-11-20: see apply-progress.)
- [x] 1.4 **SP6 Dependabot digest**. Using a scratch Dockerfile `FROM gcr.io/distroless/java21-debian13:nonroot@sha256:<old digest>` on a throwaway branch/fork (or `dependabot update` dry-run), confirm Dependabot proposes a digest bump and that `dependabot.yml` with the docker `backend/` entry validates. Pass: a digest-update proposal is observed or the dry-run output shows it. Fallback (STOP and ask): document a manual digest bump in the README (PR3 task 3.12). Scratch Dockerfile is NOT committed. (CI: Dependabot Configuration) (PASS locally 2026-11-20: see apply-progress.)

### 2. WU1 Build scaffold and repo tooling

- [x] 2.1 RED: add `scripts/` or Gradle-level check expectations: `cd backend && ./gradlew build` fails (no sources/tests) and `./gradlew spotlessCheck` at root fails on a misformatted file under `backend/` (fixture demonstrates scope). (BA: Separate Backend Build; QG: Formatting Gate)
- [x] 2.2 GREEN: finalize `backend/` build from the 1.1 scaffold: JVM test suites `integrationTest` (and registered but empty-safe `contractTest` in PR2), Kover with HTML and XML reports and generated-code exclusions, JUnit 5, no coverage threshold, `settings.gradle.kts` isolated from the root, `gradle.properties`. Verify `:app:testDebugUnitTest` at the root does not resolve backend modules. (BA: Separate Backend Build; QG: Coverage Report)
- [x] 2.3 GREEN: root `build.gradle.kts` spotless `kotlinGradle` target adds `backend/*.gradle.kts`; Kotlin target includes `backend/**`; `.gitignore` adds `backend/build/` (root-anchored in `.gitignore`), `.kotlin/`; `lefthook.yml` globs cover `backend/**` for spotless, detekt and gitleaks. Misformatted backend file now fails `spotlessCheck` (2.1 turns green). (QG: Formatting Gate, Pre-commit Hook, Detekt Gate)
- [x] 2.4 REFACTOR: dedupe catalog entries; confirm `git diff` shows no app source changes. Commit: `build(backend): add isolated Gradle build with Kover and PIT scaffold`.

### 3. WU2 Deny-by-default security and actuator

- [x] 3.1 RED: security slice tests in `backend/src/test`: 401 on "/api/books" and unknown path without credentials; 401 on "/actuator/env" and "/actuator/info" anonymous; 200 on "/actuator/health" with body containing only `status`; no session cookie; `ApplicationContextRunner` test proves a context without the test customizer has no `UserDetailsService` bean. (BA: Deny-by-Default Security, Actuator Exposes Only Health)
- [x] 3.2 RED: test-user tests: with the test-only `ApiAuthenticationCustomizer` (in `src/test`) authenticated httpBasic user gets 404 on "/actuator/env", "/actuator/beans", "/actuator/info". (BA: Actuator Exposes Only Health)
- [x] 3.3 GREEN: `BackendApplication`, `SecurityConfig` (using the DSL/fallback chosen in 1.2), `fun interface ApiAuthenticationCustomizer` with no main-code implementation, exclude `UserDetailsServiceAutoConfiguration`, `application.yaml` (actuator `exposure.include=health`, `access.default=none`, health `read-only`, `show-details=never`, `server.error.include-*=never`, `fail-on-unknown-properties=true`, no profile or flag that disables security). Test-source customizer enables httpBasic with an in-memory test user. (BA: Deny-by-Default Security, Actuator Exposes Only Health)
- [x] 3.4 RED then GREEN: packaged-artifact test or Gradle check (`bootJar` contents inspection) asserting the test user and customizer classes are absent from `backend.jar`, and that no packaged `application*.yaml` contains a bypass property. (BA: No runtime bypass, Test user stays in tests)
- [x] 3.5 REFACTOR and commit: `feat(backend): deny-by-default stateless security with health-only actuator`.

### 4. WU3 CI, CodeQL, Dependabot, coverage summary

- [x] 4.1 RED: add a JVM Kover `report.xml` fixture case to `scripts/test-kover-summary.sh`; it fails against the current parser expectations if names differ. Also a workflow check (actionlint via lefthook) is the RED for YAML. (CI: Report Generation; QG: Coverage Report)
- [x] 4.2 GREEN: `scripts/kover-summary.sh` handles JVM `report.xml` (parser is report-agnostic; only paths/names change). (QG: Coverage Report: Summary reads JVM output)
- [x] 4.3 GREEN: `.github/workflows/ci.yml` adds `backend-tests` (no `needs`, JDK 21, runs `test integrationTest koverXmlReport koverHtmlReport` from `backend/`, `if: always()` uploads of `allure-results-backend` and `coverage-backend`, no service containers); `report` needs and reads `coverage/coverage-unit/**/{htmlDebug,reportDebug.xml}` and `coverage/coverage-backend/**/{html,report.xml}`, publishes `site/kover` and `site/kover-backend`, prints one coverage line per component; `ci-gate` needs `unit-tests`, `backend-tests`, `secret-scan` now (add `contract-tests` in PR2). Workflow default stays `contents: read`; `unit-tests` must not invoke Gradle in `backend/`. Update the Out of Scope list consistent with spec. (CI: Backend Test Job, Unit Test Job, Artifact Contract, Report, ci-gate, Workflow Triggers)
- [x] 4.4 GREEN: `.github/workflows/codeql.yml` per SP5 result: JDK 17 and 21 setup, second traced backend build with `--no-daemon --no-build-cache`; permissions unchanged (`contents: read`, `security-events: write`). (CI: CodeQL Analysis Workflow) (Done in commit 9ff5d4d.)
- [x] 4.5 GREEN: `.github/dependabot.yml` adds gradle `backend/` with groups `spring`, `testcontainers`, `pitest`, `contract`; ignore Kotlin minor/major; weekly, `target-branch: dev`, prefix `build`, `include: scope`, bounded open-PR limit. (CI: Dependabot Configuration)
- [x] 4.6 REFACTOR: run `actionlint`, `shellcheck`, `scripts/test-kover-summary.sh`; verify no write permission was added outside `report` or cleanup. Commit: `ci(backend): add backend-tests job, CodeQL backend build and Dependabot`. (PARTIAL: actionlint, shellcheck, test-kover-summary.sh run green; commit made as `ci(backend): add backend-tests job, backend coverage report and Dependabot`; CodeQL part is commit 9ff5d4d; final actionlint, shellcheck, test-kover-summary.sh green, no permission added.)

`[PR1a boundary: tasks 1-4 end here (confirmed split).]`

### 5. WU4 Domain, port and use cases (strict TDD, no Spring)

- [x] 5.1 RED: unit tests for `OwnerId` (non-blank, rejects blank/whitespace), `BookId`, `Rating` (0.5-5 in half steps; rejects 0.4, 5.5, 0.75), `Book` or `BookDraft` invariants (required fields, bounded lengths consistent with the contract values chosen in PR2). (BA: Hexagonal Domain and Use Cases)
- [x] 5.2 GREEN: `domain/model` classes (plain Kotlin). (BA: Hexagonal Domain)
- [x] 5.3 RED: use case tests with an in-memory fake `BookRepository`: create, get, list, update, delete each take `OwnerId` as a mandatory parameter and return sealed outcomes; another owner's book yields `NotFound` for get/update/delete; list returns only the caller's books. (BA: Use case requires owner, Cross-owner read/write denied, Listing is isolated)
- [x] 5.4 GREEN: `domain/port/BookRepository` (signature from design) and `domain/usecase/{Create,Get,List,Update,Delete}BookUseCase` plus outcome sealed types. (BA: Hexagonal Domain and Use Cases)
- [x] 5.5 RED then GREEN: architecture guard test that scans `domain/**` sources/classes and fails on imports of `org.springframework`, `java.sql`, `javax.sql`, `jakarta.servlet`, `org.springframework.jdbc`. (BA: Domain is framework-free)
- [x] 5.6 REFACTOR and commit: `feat(backend): add framework-free books domain and use cases`.

### 6. WU5 Owner-scoped JDBC persistence and integration tests

- [x] 6.1 RED: SQL guard unit test: every SELECT/UPDATE/DELETE constant in `BookSql` contains `owner_id = :owner`; INSERT sets `owner_id`; no query lacks the filter. (BA: Owner-Scoped Persistence)
- [x] 6.2 RED: `integrationTest` with Testcontainers PostgreSQL singleton and `@ServiceConnection` (no H2): Flyway applies V1 on an empty database; raw INSERT without `owner_id` is rejected; adapter CRUD round-trip; cross-owner get/update/delete return not-found/false and change nothing; listing is isolated. (BA: Integration Tests Against PostgreSQL, Owner-Scoped Persistence)
- [x] 6.3 GREEN: `db/migration/V1__create_books.sql` (`BIGINT GENERATED ALWAYS AS IDENTITY`, `owner_id VARCHAR(255) NOT NULL` indexed), `BookSql`, row mapper, `JdbcBookRepository` using `JdbcClient`, `UseCaseConfig` `@Bean` wiring, `flyway-database-postgresql` dependency. (BA: Owner-Scoped Persistence)
- [x] 6.4 REFACTOR: extract common SQL fragments only if the guard test still passes; verify `./gradlew build` green in `backend/` and `:app:testDebugUnitTest` green at root.
- [x] 6.5 Commit: `feat(backend): add owner-scoped PostgreSQL persistence with Flyway`.
- [x] 6.6 PR1 verification record: backend `./gradlew build`, root `./gradlew spotlessCheck`, `actionlint`, `scripts/test-kover-summary.sh`; rollback boundary: revert PR1 (nothing else depends on it). Review each commit's diff for secrets (security-audit: no real credentials; dummy test user only).

---

## PR2: OpenAPI contract, web layer, contract tests, PIT (`Refs #6`; stacks on PR1)

Start state: PR1 merged to `dev`. End state: books CRUD exposed per `openapi.yaml`, validated both ways, PIT gate blocking, `contract-tests` in `ci-gate`. Out of scope: Docker, GHCR, promote, README, auth.

### 7. Spike (first)

- [x] 7.1 **SP2 openapi-request-validator 3.0.0 on JDK 21 / Boot 4.1.2**. In a scratch test: `javap -v` the published jar for class-file major version; register the servlet validation filter in a `RANDOM_PORT` context; REST Assured (6.3.0 vs 7.0.0-RC1 chosen, prefer 6.3.0 stable) with `OpenApiValidationFilter` against a stub `openapi.yaml` (3.0.3); confirm Jackson 2 (validator) and Jackson 3 (Boot) coexist without breaking Boot serialization; confirm `path.missing` IGNORE gives Spring 404. Pass: filter validates a request and a response, a violation yields an exception/400 path, and Boot's Jackson 3 mapping is unaffected. Fallback (STOP and ask): SP3 openapi-generator 7.27.0 `kotlin-spring` (`interfaceOnly`, `useSpringBoot4`, `useJackson3`, `useTags`) with REST Assured `json-schema-validator`; if that also fails, hand-written `@Size` or `@Valid` with the same test module. (BA: Request Validation and Safe Errors, Contract Tests)

### 8. WU6 OpenAPI contract, controller, validation, safe errors

- [x] 8.1 RED: `OpenApiCoverageTest` (in `src/test`) comparing `openapi.yaml` operations to handler mappings; add fixtures proving a spec operation without route and a route without operation both fail naming the item. Initially red because `openapi.yaml` and controller are absent. (BA: OpenAPI Contract as Source of Truth)
- [x] 8.2 RED: web slice tests (MockMvc/`RANDOM_PORT`): 400 for unknown field, oversized string, invalid type, malformed JSON, with nothing persisted (fake use case/repo); 400 body is constant `ProblemDetail` (`title`, `status`, fixed `detail`) with no stack trace, exception class name, SQL or file path; unexpected exception yields generic 500 of same shape; anonymous still 401 before validation; unknown route 404 when authenticated. (BA: Request Validation and Safe Errors, Deny-by-Default Security)
- [x] 8.3 GREEN: `backend/src/main/resources/openapi.yaml` (3.0.3, `additionalProperties: false`, bounded `maxLength`, operations `createBook`, `listBooks`, `getBook`, `updateBook`, `deleteBook`, declared 200/201/204/400/401/404). (BA: OpenAPI Contract as Source of Truth)
- [x] 8.4 GREEN: `web/` `BooksController`, request/response models, `ApiErrorHandler`; `OpenApiValidationConfig` (validator filter order 0 after security -100, "/api/**" interceptor, `path.missing` IGNORE); owner comes from `Authentication.name` as `OwnerId`. (BA: Request Validation and Safe Errors, Hexagonal Domain)
- [x] 8.5 REFACTOR and commit: `feat(backend): add OpenAPI books contract, controller and request validation`.

`[PR2a boundary (confirmed split).]`

### 9. WU7 Contract tests, PIT gate, `contract-tests` CI job

- [x] 9.1 RED: contract test classes in `src/contractTest/.../contract/books/`: `CreateBookTest`, `ListBooksTest`, `GetBookTest`, `UpdateBookTest`, `DeleteBookTest`; each has a happy-path method plus separate methods for 401 (no credentials), 400 (schema violation, where applicable) and cross-owner access (404). REST Assured with the validator filter on request and response, JSON-path assertions only, status codes taken from the contract. Add `BookLifecycleFlowTest` (create, get, update, list, delete, get 404). (BA: Contract Tests)
- [x] 9.2 RED: `OpenApiCoverageTest` extension: each `operationId` must have a test class in `contract.books`; fixture proves an untested operation fails naming it. Add a contract-drift negative check proving a body deviating from the schema fails the filter. (BA: Contract Tests: Untested operation fails coverage, Contract drift fails)
- [x] 9.3 GREEN: implement `contractTest` suite wiring (Testcontainers PostgreSQL, test customizer, `RANDOM_PORT`), fix any controller/contract gaps found by the tests. (BA: Contract Tests)
- [x] 9.4 RED: add mutation-killing unit tests until `domain.*` meets threshold 80; run `./gradlew pitest` expecting a failing score first on weak tests. (BA: Mutation Testing Gate)
- [x] 9.5 GREEN: pitest config targeting domain and use case packages: `mutationThreshold` 80, `excludedMethods` equals/hashCode/toString/copy*/component*, `avoidCallsTo` kotlin.jvm.internal, `outputFormats` XML and HTML, `timestampedReports` false (per SP4 result); `./gradlew pitest` exits 0 at >=80 and non-zero below. (BA: Mutation Testing Gate)
- [x] 9.6 GREEN: `ci.yml` adds `contract-tests` (no `needs`, JDK 21, `contractTest pitest` from `backend/`, `if: always()` uploads `allure-results-contract`, `coverage-contract`, `pit-contract`, which is outside the `coverage-*` merge pattern per design); `ci-gate` needs now include `contract-tests`; `report` surfaces the PIT score and merges `allure-results-contract`. (CI: Contract Test Job With PIT, ci-gate, Report Generation, Artifact Contract)
- [x] 9.7 REFACTOR and commit: `test(backend): add REST Assured contract tests and PIT mutation gate`. Verification: `./gradlew build contractTest pitest` in `backend/`, `actionlint`, a throwaway run proving PIT below threshold fails the job and still uploads `pit-contract`.

---

## PR3: Image, GHCR, promote retag, docs (`Closes #6`; stacks on PR2)

Start state: PR2 merged. End state: hardened image published from green `dev` SHAs, promote retags the same digest, README documents backend. Out of scope: deployment, secrets, package visibility, manual QA, `qa:approved`, promote (delivery workflow, not tasks).

### 10. WU8 Dockerfile, publish-image, promote retag

- [ ] 10.1 RED: checks (shell/Gradle test or `scripts/test-*.sh`) asserting `backend/Dockerfile` has a single `FROM gcr.io/distroless/java21-debian13:nonroot@sha256:`, `USER nonroot`, `COPY build/libs/backend.jar`, the OCI `source` label, no `ENV` or `ARG` secrets; and a workflow check asserting `packages: write` appears only on `publish-image` and `retag-image`. Both fail initially. (BA: Hardened Container Image; CI: GHCR Image Publication, Least privilege)
- [ ] 10.2 GREEN: `backend/Dockerfile` (single stage, linux/amd64, digest resolved from the registry at implementation time) and `backend/.dockerignore`. (BA: Hardened Container Image)
- [ ] 10.3 RED: local smoke test script: build jar, build image, `docker inspect` user is non-zero non-root, start with a reachable PostgreSQL and "/actuator/health" returns 200 (no secrets baked in; use dummy credentials via env). Expected to fail before 10.2 is complete. (BA: Image starts, Non-root runtime)
- [ ] 10.4 GREEN: `.github/workflows/ci.yml` adds `publish-image`: `if: github.event_name == 'push' && github.ref == 'refs/heads/dev'`, `needs: ci-gate`, job-level `permissions: contents: read, packages: write`; steps: build `bootJar` on runner, log in to GHCR with `GITHUB_TOKEN`, build/push tags `sha-<commit>` and `dev`, `linux/amd64`; not a `ci-gate` need; no fork/PR publish. Update the Out of Scope job list to include `publish-image`. (CI: GHCR Image Publication, Out of Scope Boundaries, Workflow Triggers)
- [ ] 10.5 RED then GREEN: `promote.yml` adds `retag-image` (`needs: promote`, job-level `packages: write` and `contents: read`, no `issues: write`) using `crane tag` pinned by sha256 then asserting digest equality between `sha-<commit>` and `staging`; and adds a `check-image` job (job-level `packages: read`) that fails when `sha-<commit>` is missing, with `promote` declaring `needs: check-image`, so `main` is NOT fast-forwarded when the image is missing (design.md, revised 2026-11-20). (CI: Promote Retags the Same Image Digest)
- [ ] 10.6 GREEN: `.github/dependabot.yml` adds docker `backend/` (weekly, `target-branch: dev`, prefix `build`, `include: scope`, grouped, bounded PR limit), subject to the SP6 outcome. (CI: Dependabot Configuration)
- [ ] 10.7 REFACTOR and commit: `ci(backend): publish hardened image to GHCR and retag on promote`. Verification: `actionlint`, 10.1 and 10.3 checks, permissions inspection. Security review: confirm image contains no secrets or test user (`docker run --entrypoint` unavailable on distroless; use `docker save` layer listing/jar content check), `packages: write` scoped to two jobs only, third-party actions pinned to a major version, `pull_request_target` unused. Rollback boundary: revert PR3; delete GHCR package versions if any were pushed.

### 11. WU9 Documentation and config context

- [ ] 11.1 README: backend section (JDK 21, `cd backend && ./gradlew build`, `integrationTest`, `contractTest`, `pitest`, Kover reports, requires Docker for Testcontainers), security posture (deny-by-default, no auth until #26), image/GHCR and promote retag behavior, Dependabot section stating security PRs target `main` and must be retargeted to `dev` by hand, and (only if SP6 failed) a manual digest bump procedure. (CI: Dependabot Configuration: Retargeting documented)
- [ ] 11.2 `openspec/config.yaml`: fix stale context (Kotlin 2.4.21 for the app, Gradle 9.9.1, backend commands and stack). Optional; skip only with a note if scope pressure demands it.
- [ ] 11.3 Commit: `docs(backend): document backend commands, image flow and Dependabot retargeting`.
- [ ] 11.4 PR3 verification record: spec scenarios mapped to evidence; confirm `Closes #6` in the PR body. Manual QA in dev, `qa:approved` and promote are left to the delivery workflow.

---

## Dependencies and order

- Strictly sequential across PRs: PR1 -> PR2 -> PR3 (stacked-to-main, each merged into `dev` before the next opens against `dev`).
- Within PR1: tasks 1.1 -> 1.2 -> 1.3 -> 1.4 (spikes, 1.3 and 1.4 may run in parallel with each other after 1.1); then 2, 3 and 4 are mostly sequential (3 needs the scaffold from 2; 4.3 needs 3); 5 and 6 depend only on 2 and can run in parallel with 3 and 4 only by a different writer, which is not planned (single writer).
- Within PR2: 7.1 first; 8 before 9; 9.4/9.5 (PIT) can follow 5.x tests and run before 9.6.
- Within PR3: 10.2 before 10.4; 10.5 and 10.6 are independent of each other; 11 is independent of 10 and may be done first.
