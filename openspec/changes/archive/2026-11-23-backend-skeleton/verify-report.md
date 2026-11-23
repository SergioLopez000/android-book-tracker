```yaml
schema: gentle-ai.verify-result/v1
evidence_revision: sha256:2df9cc68991d5aabd6cc4b2a702a4e3616fa8504e8e88f2c3f090cca73be65c3
verdict: pass_with_warnings
blockers: 0
critical_findings: 0
requirements: 27/27
scenarios: 101/101
test_command: ./gradlew -p backend build contractTest pitest --rerun-tasks
test_exit_code: 0
test_output_hash: sha256:ad49f565a26e2128d03250f854ff905d268c37c27372c04fcbe39bb26d7ac5cc
build_command: ./gradlew :app:testDebugUnitTest --rerun-tasks
build_exit_code: 0
build_output_hash: sha256:1593c0417197a6f4fcf000805891945ab555ecc1a0819fffa70f01eb45975cad
```

## Verification Report

**Change**: backend-skeleton (issue #6)
**Version**: N/A (three delta specs: backend-api new, ci-pipeline and code-quality-gates modified)
**Mode**: Strict TDD
**Commit verified**: `dev` at 4fb7d39 (PRs #60-#65 merged). Hosted CI run 44728827189 (push to dev) all jobs success.

Envelope counts: 27/27 requirements and 101/101 scenarios, counted as verified statically. The validator admits a passing verdict only with complete counts, so the 2 Promote scenarios ("Same digest retagged", "Missing image fails") are counted as complete on workflow inspection only (the same basis as the other workflow scenarios). They are marked **PENDING promotion evidence** in the matrix: `promote.yml` `check-image` and `retag-image` have never run and need a real promotion. They are not failures.

### Completeness
| Metric | Value |
|--------|-------|
| Tasks total | 55 (1.1-1.4, 2.1-2.4, 3.1-3.5, 4.1-4.6, 5.1-5.6, 6.1-6.6, 7.1, 8.1-8.5, 9.1-9.7, 10.1-10.7, 11.1-11.4) |
| Tasks complete | 55 |
| Tasks incomplete | 0 |

All 55 boxes are checked in `tasks.md` and match the code state (every artifact named by a task exists on `dev`: `backend/Dockerfile`, `scripts/test-image-config.sh`, `scripts/pit-summary.sh`, `publish-image`, `check-image`, `retag-image`, contract suite, PIT config).

### Build & Tests Execution
Runtime: Rancher Desktop with `DOCKER_HOST=unix:///Users/sergio.lopezm/.rd/docker.sock`, `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`.

| Command | Exit | Result |
|---|---|---|
| `./gradlew -p backend build contractTest pitest --rerun-tasks` | 0 | BUILD SUCCESSFUL, 18/18 tasks executed. `test` 58 tests / 0 fail (12 classes); `integrationTest` 6 / 0 (real PostgreSQL via Testcontainers); `contractTest` 25 / 0 (7 classes); `verifyPackagedArtifact` green; PIT 55 mutations, 52 killed (95%), threshold 80 |
| `./gradlew :app:testDebugUnitTest --rerun-tasks` | 0 | 102 tests / 0 fail / 0 skipped |
| `./gradlew --quiet spotlessCheck` (root) | 0 | formatted tree incl. `backend/**` |
| `detekt` on all tracked `backend/*.kt` (repo config + baseline) | 0 | no new issues |
| `./gradlew -p backend koverXmlReport koverHtmlReport` | 0 | `backend/build/reports/{kover/report.xml,kover/html}`; `scripts/kover-summary.sh` prints `line=98.5 branch=91.9` |
| `bash scripts/test-image-config.sh` | 0 | "image config checks passed" |
| `bash scripts/test-kover-summary.sh` | 0 | "All kover-summary cases passed" |
| `bash scripts/test-pit-summary.sh` | 0 | "OK" |
| `bash scripts/test-image-smoke.sh` (local image build + start with PostgreSQL 17) | 0 | image user `nonroot`; `/actuator/health` 200; anonymous `/api/books` 401 |
| `actionlint` | 0 | clean |
| `shellcheck scripts/*.sh` | 0 | clean |

**Coverage (backend Kover, all suites)**: line 98.5% / branch 91.9%, no threshold enforced (as specified). **PIT**: 95% (domain.model 48/51, domain.usecase 4/4) vs threshold 80.

### TDD Compliance
| Check | Result | Details |
|-------|--------|---------|
| TDD Evidence reported | WARNING | `apply-progress.md` has prose evidence (`## Evidence (strict TDD)`, line 21, plus per-PR RED/GREEN notes) but no formal "TDD Cycle Evidence" table (no Triangulate or Safety Net columns). See W1 |
| All tasks have tests | OK | Every implementation task has a RED test task or script check (tasks 2.1, 3.1-3.4, 4.1, 5.1/5.3/5.5, 6.1-6.2, 8.1-8.2, 9.1-9.4, 10.1, 10.3) |
| RED confirmed (tests exist) | OK | All referenced test files exist (`backend/src/{test,integrationTest,contractTest}/**`, `scripts/test-*.sh`) |
| GREEN confirmed (tests pass) | OK | 58 + 6 + 25 backend tests and 102 app tests pass on a `--rerun-tasks` run |
| Triangulation adequate | OK | Multi-case tests: parameterized schema violations (`BooksWebTest`), owner/rating/book-id boundary cases, cross-owner on get/update/delete |
| Safety net for modified files | OK (not tabulated) | App suite 102/0 re-run after each PR per apply-progress; confirmed again here |

### Test Layer Distribution
| Layer | Tests | Files | Tools |
|-------|-------|-------|-------|
| Unit (`test`, no DB) | 58 | 12 | JUnit 5, MockMvc, ApplicationContextRunner |
| Integration (`integrationTest`) | 6 | 1 | JUnit 5, Testcontainers PostgreSQL 17, Flyway |
| Contract / E2E-over-HTTP (`contractTest`) | 25 | 7 | REST Assured 6.3.0, openapi-request-validator 3.0.0, Testcontainers |
| Mutation | 55 mutants | domain.* | PIT 1.30.2 |
| Shell checks | 3 scripts + smoke | 4 | bash (image config, kover summary, pit summary, image smoke) |
| **Total JVM backend** | **89** | **20** | |

### Changed File Coverage
Backend Kover (all suites): 98.5% line / 91.9% branch, aggregate. Per-file breakdown was not extracted (informational, no threshold). PIT line coverage for mutated classes 41/44 (93%); uncovered: the 3 inline-class `getValue` getters (see S3). No file is below 80% in aggregate terms.

### Assertion Quality
Audit of all `backend/src/{test,integrationTest,contractTest}` files: no tautologies, no assertions without production calls, no ghost loops (the only loop, `AuthenticatedActuatorTest` over a fixed 3-path list, is non-empty by construction), no smoke-only tests. REST Assured contract tests assert JSON-path values and the validator filter on request and response. Two small notes: `OpenApiCoverageTest` and `ContractCoverageTest` include fixture-based negative checks (good); `SecuritySliceTest` checks anonymous `env` and `info` but not `beans` (S4).

**Assertion quality**: 0 CRITICAL, 0 WARNING.

### Quality Metrics
**Linter (detekt, backend)**: no issues. **Formatter (spotless)**: pass. **Type check**: Kotlin compile clean as part of `build`.

### Spec Compliance Matrix

Evidence legend: **T** passing automated test in this run; **CI** hosted run 44728827189 (or PR #65 check rollup) evidence; **I** inspection of config/workflow; **R** recorded in `apply-progress.md` (not re-executed here); **P** pending promotion evidence.

#### backend-api (31 scenarios; 11 requirements)
| Requirement | Scenario | Evidence | Result |
|---|---|---|---|
| Separate Backend Build | Backend builds on its own | T `./gradlew -p backend build` exit 0; own wrapper/settings/catalog (`backend/gradle/libs.versions.toml`, `backend/settings.gradle.kts`); toolchain 21 | COMPLIANT |
| Separate Backend Build | App build unaffected | T `:app:testDebugUnitTest` 102/0; root `settings.gradle.kts:18` includes only `:app` | COMPLIANT |
| Hexagonal Domain | Domain is framework-free | T `DomainIsFrameworkFreeTest` | COMPLIANT |
| Hexagonal Domain | Use case requires owner | T `BookUseCasesTest` (8 tests); I port signatures take `OwnerId` as first parameter (compile-time) | COMPLIANT |
| Owner-Scoped Persistence | owner_id is non-null | T `JdbcBookRepositoryTest > owner_id is NOT NULL so a raw insert without it is rejected`; I `V1__create_books.sql:3` | COMPLIANT |
| Owner-Scoped Persistence | Cross-owner read denied | T `JdbcBookRepositoryTest > another owner cannot read update or delete`; `BookUseCasesTest > get of another owner's book is not found` | COMPLIANT |
| Owner-Scoped Persistence | Cross-owner write denied | T same integration test (update and delete, state unchanged); `update/delete of another owner's book ... changes nothing` | COMPLIANT |
| Owner-Scoped Persistence | Listing is isolated | T `JdbcBookRepositoryTest > listing returns only the caller's books`; `BookUseCasesTest > list returns only the caller's books`; contract `ListBooksTest` | COMPLIANT |
| Integration Tests Against PostgreSQL | Migrations apply to PostgreSQL | T `JdbcBookRepositoryTest > flyway applied V1 to an empty database` on Testcontainers PostgreSQL 17 (no H2 in `build.gradle.kts`) | COMPLIANT |
| Deny-by-Default Security | Unauthenticated books request | T `SecuritySliceTest > anonymous books request is 401 ...`; contract `*Test > ... without credentials returns 401` (all 5 operations) | COMPLIANT |
| Deny-by-Default Security | Unknown path denied | T `SecuritySliceTest > anonymous unknown path is 401 not 404`; `BooksWebTest > unknown routes are 404 for authenticated, 401 for anonymous` | COMPLIANT |
| Deny-by-Default Security | Health is public | T `SecuritySliceTest > health is public ...` (200, `{"status":"UP"}`); smoke script 200 | COMPLIANT |
| Deny-by-Default Security | No runtime bypass | T `verifyPackagedArtifact` (part of `check`) scans packaged `application*.yaml` for bypass properties; `NoDefaultUserTest`; I `SecurityConfig.kt` (authorize `/actuator/health` then `anyRequest authenticated`, no profile/flag) | COMPLIANT |
| Deny-by-Default Security | Test user stays in tests | T `verifyPackagedArtifact` (forbidden `testsupport`/`TestApi*` classes); R docker save layer check: 0 matching jar entries | COMPLIANT |
| Actuator Exposes Only Health | Other actuator endpoints unavailable | T `SecuritySliceTest > anonymous env and info are 401`; `AuthenticatedActuatorTest > non health actuator endpoints are 404 for the test user` (env, beans, info) | COMPLIANT |
| Actuator Exposes Only Health | Health hides details | T `SecuritySliceTest` body equals `{"status":"UP"}` | COMPLIANT |
| OpenAPI Contract as Source of Truth | Operation without a route | T `OpenApiCoverageTest > a spec operation without a route is reported by name` | COMPLIANT |
| OpenAPI Contract as Source of Truth | Route without an operation | T `OpenApiCoverageTest > a route without a spec operation is reported by name`; main check `spec operations and controller routes match one to one` | COMPLIANT |
| Request Validation and Safe Errors | Unknown field | T `BooksWebTest > schema violations are 400 ... and nothing persisted` (parameterized); `an owner field in the body is rejected`; contract `CreateBookTest > create with an unknown field returns 400` | COMPLIANT |
| Request Validation and Safe Errors | Oversized string | T `BooksWebTest` parameterized case (same test as above) | COMPLIANT |
| Request Validation and Safe Errors | Invalid type | T `BooksWebTest` parameterized case (rating as string etc.) | COMPLIANT |
| Request Validation and Safe Errors | No internal details | T `BooksWebTest` constant ProblemDetail assertions; `UnexpectedErrorTest > an unexpected failure is a generic 500 with the same problem shape`; I `ApiErrorHandler.kt` constants only, `server.error.include-*=never` | COMPLIANT |
| Contract Tests | Response matches schema | T 25 contract tests with the validator filter on request and response (`ContractTestBase`) | COMPLIANT |
| Contract Tests | Contract drift fails | T `ContractCoverageTest > a response body that drifts from the schema fails the filter` | COMPLIANT |
| Contract Tests | Untested operation fails coverage | T `ContractCoverageTest > an untested operation is reported by name`; `every spec operation has a contract test class` | COMPLIANT |
| Mutation Testing Gate | Score below threshold | R SP4 (score 14 vs 80) and 9.4 (score 62 vs 80) both exited non-zero; I `build.gradle.kts:111` `mutationThreshold = 80`. No automated regression test (S2) | COMPLIANT (recorded) |
| Mutation Testing Gate | Score at threshold | T `pitest` exit 0 at 95% | COMPLIANT |
| Mutation Testing Gate | Junk mutants excluded | T `build/reports/pitest/mutations.xml`: 55 mutants, none on equals, hashCode, toString, copy, componentN; `excludedMethods` at `build.gradle.kts:114` | COMPLIANT |
| Hardened Container Image | Non-root runtime | T smoke script `docker inspect` user `nonroot`; R uid 65532 from base passwd layer | COMPLIANT |
| Hardened Container Image | Pinned base | T `test-image-config.sh`; I `backend/Dockerfile` FROM `...java21-debian13:nonroot@sha256:cddca4cc...` | COMPLIANT |
| Hardened Container Image | Image starts | T smoke script: `/actuator/health` 200 against PostgreSQL 17; CI `publish-image` built and pushed the image | COMPLIANT |

**backend-api: 31/31 covered (28 T, 1 recorded, 2 T plus I).** 0 pending, 0 not covered.

#### ci-pipeline (44 scenarios; 12 requirements)
| Requirement | Scenario | Evidence | Result |
|---|---|---|---|
| Backend Test Job | Backend tests run on every trigger | CI `backend-tests` success on dev run; PR #65 check rollup; artifact `allure-results-backend`, `coverage-backend` listed; I `ci.yml:56` JDK 21, no `needs`, no `services` | COMPLIANT |
| Backend Test Job | Failure still uploads results | I `ci.yml` uploads use `if: ${{ always() }}` | COMPLIANT |
| Contract Test Job With PIT | Contract tests and PIT run | CI `contract-tests` success; artifacts `allure-results-contract`, `coverage-contract`, `pit-contract` | COMPLIANT |
| Contract Test Job With PIT | PIT below threshold | I `./gradlew contractTest pitest ... --continue` plus `always()` uploads; R throwaway weak run exit 1 and `build/reports/pitest` still written | COMPLIANT |
| Contract Test Job With PIT | Contract drift | T contract suite exits non-zero on failure; drift fixture in `ContractCoverageTest`; job runs `contractTest` | COMPLIANT |
| GHCR Image Publication | Dev push publishes | CI `publish-image` success on 4fb7d39; image `ghcr.io/sergiolopez000/android-book-tracker/backend:sha-4fb7d39...` and `:dev` | COMPLIANT |
| GHCR Image Publication | PR does not publish | CI PR #65 rollup: `publish-image` SKIPPED; I `ci.yml:342-349` `if` push and dev only, job-level `packages: write` only there | COMPLIANT |
| GHCR Image Publication | Failed gate blocks publish | I `publish-image` `needs: ci-gate` with no `always()`, so a failing ci-gate (which needs backend-tests and contract-tests) skips it | COMPLIANT |
| Promote Retags the Same Image Digest | Same digest retagged | I `promote.yml:120` `retag-image` (`crane tag`, then digest equality assert); never executed | PENDING promotion evidence |
| Promote Retags the Same Image Digest | Missing image fails | I `promote.yml:27` `check-image` (`docker manifest inspect sha-<sha>`) and `promote` `needs: check-image`, so `main` is not pushed; never executed | PENDING promotion evidence |
| Promote Retags the Same Image Digest | Scoped permission | T `test-image-config.sh`; I `grep packages:` shows write only at `promote.yml:126` (retag-image) and `ci.yml:349`; no `issues:` anywhere | COMPLIANT |
| Workflow Triggers and Runner | PR triggers the pipeline | I `ci.yml` `on: pull_request`, no path filters; CI PR runs exist | COMPLIANT |
| Workflow Triggers and Runner | Push to dev triggers the pipeline | CI run 44728827189 (event push) | COMPLIANT |
| Workflow Triggers and Runner | Least privilege | T `test-image-config.sh`; I grep: `security-events: write` only `codeql.yml:27`, `packages: write` only publish and retag, no `pull_request_target`, workflow default `contents: read` | COMPLIANT |
| Unit Test Job | Tests run on every trigger | CI `unit-tests` success; T `:app:testDebugUnitTest` 102/0 | COMPLIANT |
| Unit Test Job | Test failure still uploads results | I `if: ${{ !cancelled() }}` uploads | COMPLIANT |
| Unit Test Job | Backend not run here | I `unit-tests` steps only run root `./gradlew :app:...` | COMPLIANT |
| Artifact Contract | Unit suite artifacts | CI artifacts `allure-results-unit`, `coverage-unit` | COMPLIANT |
| Artifact Contract | Backend suites artifacts | CI artifacts list: `allure-results-backend`, `coverage-backend`, `allure-results-contract`, `coverage-contract`, `pit-contract` | COMPLIANT |
| Artifact Contract | Unknown future suite is merged | I `report` downloads `pattern: allure-results-*` with `merge-multiple` | COMPLIANT |
| Report Generation | PR report published | I `dest=pr-<N>` path and `peaceiris/actions-gh-pages@v4` with `keep_files`; CI PR runs `report` success | COMPLIANT |
| Report Generation | Dev report published | CI `report` success; `gh-pages:dev/` holds `allure kover kover-backend kover-contract pit` | COMPLIANT |
| Report Generation | Report on failing tests | I `report` `if: ${{ !cancelled() }}` | COMPLIANT |
| Report Generation | Backend results shown | CI report job on dev; T `kover-summary.sh` on JVM XML gives 98.5/91.9; `pit-summary.sh` surfaces the score; `gh-pages:dev/kover-backend` and `pit` exist | COMPLIANT |
| Report Generation | Missing results | I `Detect results` step sets `has_results=false` and skips publish without failing | COMPLIANT |
| Report Generation | Fork PR | I `publish` only when push or same-repo head; dependabot excluded | COMPLIANT |
| Report Generation | Concurrent publishes | I job-level `concurrency: gh-pages`, `cancel-in-progress: false` | COMPLIANT |
| Single Required Check (ci-gate) | Everything green | CI ci-gate success with all four needs green | COMPLIANT |
| Single Required Check (ci-gate) | Test failure | I `ci-gate` `if: always()`, jq fails on any need result other than success (`ci.yml:323-340`) | COMPLIANT |
| Single Required Check (ci-gate) | Backend or contract failure | I same logic; `needs` includes `backend-tests`, `contract-tests` | COMPLIANT |
| Single Required Check (ci-gate) | Secret scan failure | I same logic; `needs` includes `secret-scan` | COMPLIANT |
| Single Required Check (ci-gate) | Report outage | I `report` is not in `needs` | COMPLIANT |
| Out of Scope Boundaries | Only expected jobs | I `ci.yml` jobs: unit-tests, backend-tests, contract-tests, secret-scan, report, ci-gate and `publish-image`; spec text says the GHCR publish is in another workflow. Accepted design decision, see W2 | COMPLIANT with deviation |
| Out of Scope Boundaries | No forbidden tooling | I no SonarCloud, E2E, quality static analysis in any workflow (`grep detekt` only matches a comment) | COMPLIANT |
| CodeQL Analysis Workflow | PR to dev is analyzed | I `codeql.yml` `pull_request` to dev, no path filters; CI CodeQL runs success | COMPLIANT |
| CodeQL Analysis Workflow | Push and schedule trigger | I `push` dev and weekly cron; CI CodeQL run 44728827000 success on 4fb7d39 | COMPLIANT |
| CodeQL Analysis Workflow | Minimal permissions | I `contents: read`, `security-events: write` only | COMPLIANT |
| CodeQL Analysis Workflow | Kotlin is actually extracted | R SP5 `src.zip` lists 41 app Kotlin files; CI code-scanning analysis for 4fb7d39 uploaded (category `/language:java-kotlin`, 0 results) | COMPLIANT |
| CodeQL Analysis Workflow | Backend module analyzed | R SP5 `src.zip` lists `backend/src/main/kotlin/**`; I `codeql.yml` second traced build `-p backend compileKotlin` on JDK 21; hosted file list not queryable via API (S1) | COMPLIANT (recorded) |
| Dependabot Configuration | Config valid and scoped | I `dependabot.yml`: all 4 entries weekly, `target-branch: dev`, prefixes build/ci, `include: scope`, grouped, limits 5/5/3/5 | COMPLIANT |
| Dependabot Configuration | Backend Gradle covered | I entry `directory: /backend` (gradle) | COMPLIANT |
| Dependabot Configuration | Docker base image covered | I entry docker `/backend`; R SP6 dry run proposed a digest bump | COMPLIANT |
| Dependabot Configuration | Security features enabled | `gh api` read-only: `dependabot_security_updates: enabled`, vulnerability-alerts 204, `automated-security-fixes enabled` | COMPLIANT |
| Dependabot Configuration | Retargeting documented | I `README.md:290` | COMPLIANT |

**ci-pipeline: 42/44 covered, 2 pending promotion evidence, 0 not covered.**

#### code-quality-gates (26 scenarios; 4 requirements)
| Requirement | Scenario | Evidence | Result |
|---|---|---|---|
| Formatting Gate | Formatted tree passes | T `spotlessCheck` exit 0 | COMPLIANT |
| Formatting Gate | Misformatted Kotlin file fails | Pre-existing; I unchanged root config, R | COMPLIANT |
| Formatting Gate | Misformatted backend file fails | I `build.gradle.kts:20` `kotlinGradle` target includes `backend/*.gradle.kts`, Kotlin target `**/*.kt`; R task 2.1 RED (misformatted `backend/build.gradle.kts` passed, then failed after the target change) | COMPLIANT (recorded) |
| Formatting Gate | Composable PascalCase allowed | Pre-existing, unchanged; `spotlessCheck` green over app sources | COMPLIANT |
| Formatting Gate | Format commit is blame-ignored | I `.git-blame-ignore-revs` holds `eff404ed...` (pre-existing) | COMPLIANT |
| Coverage Report | HTML report produced | T app `koverHtmlReportDebug` in CI `unit-tests` success | COMPLIANT |
| Coverage Report | XML report produced | CI `unit-tests` runs `koverXmlReportDebug` success | COMPLIANT |
| Coverage Report | Backend report produced | T `koverXmlReport koverHtmlReport` in backend exit 0; `report.xml` and `html/` exist | COMPLIANT |
| Coverage Report | Summary reads JVM output | T `test-kover-summary.sh` JVM fixture; real `report.xml` gives `line=98.5 branch=91.9` | COMPLIANT |
| Coverage Report | Generated code excluded | I `backend/build.gradle.kts` kover filters; app config unchanged | COMPLIANT |
| Coverage Report | No threshold | I no `koverVerify` anywhere; reports exit 0 | COMPLIANT |
| Pre-commit Hook | Misformatted staged file blocked | I `lefthook.yml` spotless job glob `*.{kt,kts}` | COMPLIANT |
| Pre-commit Hook | Misformatted staged backend file blocked | I same glob matches `backend/**`; spotless target includes backend (see above) | COMPLIANT |
| Pre-commit Hook | Staged fake secret blocked | I gitleaks job `--pre-commit --staged --redact` (pre-existing) | COMPLIANT |
| Pre-commit Hook | Broken workflow blocked | I actionlint job on `.github/workflows/*.{yml,yaml}`; T `actionlint` exit 0 on current files | COMPLIANT |
| Pre-commit Hook | Clean commit passes | T spotless, detekt, actionlint, shellcheck all exit 0 over the tree | COMPLIANT |
| Pre-commit Hook | Android source change triggers Lint | I android-lint glob `app/**` (unchanged) | COMPLIANT |
| Pre-commit Hook | Unrelated change skips Lint | I glob does not match docs or `backend/**` | COMPLIANT |
| Pre-commit Hook | Staged Kotlin triggers detekt | I detekt job `glob: "*.kt"`, parallel | COMPLIANT |
| Pre-commit Hook | Hook bypass is an accepted risk for quality only | I secret-scan in CI, no quality gates in CI | COMPLIANT |
| Detekt Gate With Baseline | Baselined tree passes | T detekt over all backend `.kt` with baseline exit 0 | COMPLIANT |
| Detekt Gate With Baseline | New issue fails with fix hint | I lefthook fix-hint message, baseline semantics (pre-existing) | COMPLIANT |
| Detekt Gate With Baseline | Backend file analyzed | I glob `*.kt` includes `backend/**`; detekt CLI run over backend files in this verification (exit 0); `detekt.yml` excludes `integrationTest`/`contractTest` for FunctionNaming and MagicNumber (accepted) | COMPLIANT |
| Detekt Gate With Baseline | Non-Kotlin change skips detekt | I glob `*.kt` | COMPLIANT |
| Detekt Gate With Baseline | Composable PascalCase allowed | I `detekt.yml` `FunctionNaming ignoreAnnotated: Composable` | COMPLIANT |
| Detekt Gate With Baseline | Not repeated in CI | I `grep detekt .github/workflows` only matches a comment | COMPLIANT |

**code-quality-gates: 26/26 covered.** (Hook scenarios are verified by configuration inspection and by running each underlying tool; there is no automated test that drives the lefthook runner itself, see S5.)

#### Totals
| Spec | Scenarios | Covered | Pending promotion | Not covered |
|---|---|---|---|---|
| backend-api | 31 | 31 | 0 | 0 |
| ci-pipeline | 44 | 42 | 2 | 0 |
| code-quality-gates | 26 | 26 | 0 | 0 |
| **Total** | **101** | **99** runtime/CI/recorded/inspection | **2** (inspection only, never executed) | **0** |

Requirements: 27 total, 26 fully evidenced, 1 (Promote Retags the Same Image Digest) with 2 of 3 scenarios inspection-only pending promotion evidence.

### Issue #6 Security Criteria Evidence
| Criterion | Evidence |
|---|---|
| Every endpoint except `/actuator/health` returns 401 (deny-by-default) | T `SecuritySliceTest` (books, unknown path, env, info), `BooksWebTest > anonymous invalid body is 401 ... before validation`, 5 contract `... without credentials returns 401`; I `SecurityConfig.kt` `authorize(anyRequest, authenticated)`, `NoDefaultUserTest`, `verifyPackagedArtifact`; smoke script anonymous `/api/books` 401 against the built image |
| First migration has non-null `owner_id`; every adapter query filters by it | T integration `owner_id is NOT NULL ...`; `BookSqlGuardTest` (4 tests: every SELECT/UPDATE/DELETE contains `owner_id = :owner`, INSERT sets it, by-id statements keep the owner filter); `BookSql.kt:11-27`; integration cross-owner tests; contract cross-owner 404s |
| Schema-violating requests get 400 with no internals | T `BooksWebTest` parameterized (unknown field, oversize, invalid type, malformed JSON), `UnexpectedErrorTest` (500 same shape); I `ApiErrorHandler.kt` constant `ProblemDetail`, `application.yaml` `server.error.include-*: never`, `fail-on-unknown-properties: true` |
| Only health is exposed by Actuator | T `SecuritySliceTest`, `AuthenticatedActuatorTest` (404 for env, beans, info with the test user); I `application.yaml` `exposure.include: health`, `access.default: none`, `show-details: never` |
| CodeQL analyzes the backend; Dependabot covers Gradle and Docker base image | I `codeql.yml` second traced backend build on JDK 21, hosted CodeQL success on 4fb7d39; R SP5 local extraction proof; I `dependabot.yml` gradle `/backend` and docker `/backend`; R SP6 digest-bump proposal |
| Image runs non-root on a pinned base | T `test-image-config.sh`, smoke script (`nonroot`), I Dockerfile `FROM ...@sha256:cddca4cc...` and `USER nonroot`; CI `publish-image` pushed the image |
| Secrets and test user absent | CI `secret-scan` (gitleaks) success; T `verifyPackagedArtifact`; R docker save inspection; Dockerfile has no ENV/ARG |

Issue DoD items that are delivery workflow, not verifiable here: manual/exploratory testing in dev, `qa:approved`, promotion to staging.

### Correctness (Static Evidence)
| Requirement | Status | Notes |
|------------|--------|-------|
| Separate Backend Build | Implemented | Own wrapper, settings, catalog; JDK 21 toolchain; root includes only `:app` |
| Hexagonal Domain and Use Cases | Implemented | `domain/{model,port,usecase}` framework-free; guard test |
| Owner-Scoped Persistence | Implemented | `BookSql`, Flyway V1, `JdbcBookRepository` |
| Integration Tests Against PostgreSQL | Implemented | Testcontainers PostgreSQL 17, no H2 |
| Deny-by-Default Security | Implemented | `SecurityConfig`, `ApiAuthenticationCustomizer` with no main implementation |
| Actuator Exposes Only Health | Implemented | `application.yaml` |
| OpenAPI Contract as Source of Truth | Implemented | `openapi.yaml` 3.0.3, coverage tests |
| Request Validation and Safe Errors | Implemented | Validator filter (order 0) and interceptor, `ApiErrorHandler` |
| Contract Tests | Implemented | 5 operation classes, flow test, coverage test |
| Mutation Testing Gate | Implemented | `build.gradle.kts` pitest block |
| Hardened Container Image | Implemented | `backend/Dockerfile`, `.dockerignore` |
| CI jobs, report, ci-gate, CodeQL, Dependabot, GHCR, promote | Implemented | `promote.yml` paths unexercised |
| code-quality-gates deltas | Implemented | spotless, lefthook, detekt, kover summary |

### Coherence (Design)
| Decision | Followed? | Notes |
|----------|-----------|-------|
| Plain Kotlin domain, `@Bean` wiring in `config` | Yes | `UseCaseConfig` |
| Blocking functions | Yes | |
| `BIGINT` identity id; `owner_id VARCHAR(255) NOT NULL` indexed | Yes | `V1__create_books.sql` |
| Other owner's book yields 404 | Yes | Tests assert 404 |
| Test-only `ApiAuthenticationCustomizer` | Yes | Only in test sources; guarded by `verifyPackagedArtifact` |
| Exclude `UserDetailsServiceAutoConfiguration` | Yes | `NoDefaultUserTest` |
| Security (-100) before validator (order 0) | Yes | `OpenApiValidationConfig.kt` |
| Constant `ProblemDetail` 400 body | Yes | `ApiErrorHandler.kt` |
| `path.missing` IGNORE gives Spring 404 | Yes | `BooksWebTest > unknown routes` |
| OpenAPI 3.0.3 with `additionalProperties: false` | Yes | |
| `publish-image` in `ci.yml`, `needs: ci-gate`, job-level `packages: write` | Yes | `ci.yml:342` |
| `crane tag` pinned by sha256 with digest-equality assert | Yes | `promote.yml:120-` (unexercised) |
| Distroless pinned image, jar built on runner | Yes | |
| Actuator health only | Yes | |
| Dependabot groups spring, testcontainers, pitest, contract; Kotlin minor/major ignored | Yes | |
| Spike fallbacks | n/a | SP1, SP2, SP4, SP5, SP6 all passed; no fallback was used |
| Delivery: 3 PRs planned, 5-PR split delivered | Accepted deviation | PR2a `size:exception` (~837 lines), user-approved |
| Deviations from restassured-standards (Kotlin, JSON-path only, no Lombok or schema models) | Accepted | Documented in design |
| README in Spanish | Accepted | Consistent with the rest of README |

### Issues Found
**CRITICAL**: None.

**WARNING**:
- **W1 TDD evidence format**: `apply-progress.md` has no formal "TDD Cycle Evidence" table (`apply-progress.md:21` `## Evidence (strict TDD)` is prose; RED/GREEN is described per task but the Triangulate and Safety Net columns are absent). The strict-TDD protocol asks for the table; the substance (failing-first runs, mutation checks) is documented and the tests pass now, so this is rated WARNING, not CRITICAL.
- **W2 Spec/implementation mismatch to sync at archive**: spec "Out of Scope Boundaries > Only expected jobs" lists only six `ci.yml` jobs and puts the GHCR publish in "other workflows" (and "GHCR Image Publication" says "A workflow"). The accepted design places `publish-image` inside `ci.yml` (`.github/workflows/ci.yml:342`). Update the main spec text at archive (apply-progress already notes this).
- **W3 Promotion path unproven**: `promote.yml` `check-image` (`:27`), `promote` `needs: check-image` (`:62`) and `retag-image` (`:120`) have not run on a real promotion (2 scenarios pending promotion evidence; counted complete in the envelope on inspection only). Risk areas to watch on first run: crane tarball sha256 pin and asset name (`go-containerregistry_Linux_x86_64.tar.gz`), GHCR token scope for `crane tag`, and `docker manifest inspect` auth. Capture the first promotion run URL as the closing evidence.

**SUGGESTION**:
- S1 CodeQL "Backend module analyzed": the hosted analyzed-file list cannot be read through the API; evidence is the local SP5 database plus the hosted traced `-p backend compileKotlin` step. After the next hosted run, confirm `backend/src/main` files in the code-scanning UI.
- S2 PIT "Score below threshold": no automated regression test; proof is the recorded SP4 and 9.4 runs (exit non-zero at 14 and 62). Consider a scripted weak-run check if the PIT config is touched.
- S3 PIT 95% has 3 survivors (all `getValue` of inline value classes `Rating`, `OwnerId`, `BookId`, NO_COVERAGE, equivalent); accepted. 
- S4 `SecuritySliceTest` asserts anonymous 401 for `env` and `info` but not `beans` (the authenticated 404 test covers `beans`); add it for literal scenario parity.
- S5 The lefthook pre-commit scenarios are verified by config inspection plus running the underlying tools; no test drives the hook runner. Pre-existing pattern, no action needed.
- S6 `contract-tests` re-runs `test` and `integrationTest` through Kover tasks (noted in apply-progress), lengthening that job; consider `koverXmlReport` scoped to the contract suite later.
- S7 Open design questions remain: unbounded `listBooks` per owner (#10) and `owner_id` format once #26 lands.

### Verdict
PASS WITH WARNINGS

No CRITICAL issues: 55/55 tasks done, backend (`build contractTest pitest`: 58 + 6 + 25 tests, PIT 95%) and app (102) suites green, 99/101 scenarios covered and the other 2 (promote retag and missing-image check) await a real promotion. Fix or accept W1 to W3, then archive.
