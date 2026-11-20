# Design: Spring Boot Backend Skeleton With Contract Tests (#6)

## Technical Approach

A separate Gradle build under `/backend` (JDK 21, Kotlin 2.3.21, Boot 4.1.2 WebMVC) mirrors the app hexagon: a framework-free domain, Spring adapters at the edges, and wiring in `config`. Ports take `OwnerId` as a required parameter, so the type system enforces owner isolation and the SQL enforces it at runtime. Security denies by default and has no production authentication mechanism until #26. Contract tests (PR2) validate both request and response against `openapi.yaml`. Spikes run first in each PR. A failed spike switches to its fallback below.

## Package Layout (`com.librosleidos.backend`)

| Package | Contents | Spring? |
|---|---|---|
| `domain/model` | `Book`, `BookDraft`, `BookId`, `OwnerId` (non-blank), `Rating` (0.5-5, half steps) | No |
| `domain/port` | `BookRepository` | No |
| `domain/usecase` | `Create/Get/List/Update/DeleteBookUseCase`, outcome sealed types | No |
| `data/jdbc` | `JdbcBookRepository`, `BookSql` (SQL constants), row mapper | Yes |
| `web` | `BooksController`, request/response models, `ApiErrorHandler` | Yes |
| `config` | `SecurityConfig`, `OpenApiValidationConfig`, `UseCaseConfig` (`@Bean` wiring) | Yes |

```kotlin
interface BookRepository {
    fun create(owner: OwnerId, draft: BookDraft): Book
    fun findById(owner: OwnerId, id: BookId): Book?
    fun findAll(owner: OwnerId): List<Book>
    fun update(owner: OwnerId, id: BookId, draft: BookDraft): Book?
    fun delete(owner: OwnerId, id: BookId): Boolean
}
fun interface ApiAuthenticationCustomizer { fun customize(http: HttpSecurity) } // no main-code implementation
```

## Architecture Decisions

| Decision | Choice | Rejected | Rationale |
|---|---|---|---|
| Domain purity | Plain Kotlin and `@Bean` wiring | `@Service` in the domain | Matches the app (`config.yaml` rules.design). PIT and unit tests run without Spring. |
| Concurrency | Blocking functions | Coroutines like the app | WebMVC and `JdbcClient` are blocking. Fewer PIT junk mutants. |
| Book ID | `BIGINT GENERATED ALWAYS AS IDENTITY` | UUID | Aligns with the app `Long` id. The owner filter prevents enumeration. |
| `owner_id` | `VARCHAR(255) NOT NULL`, indexed, from `Authentication.name` | UUID | The #26 subject format is unknown (open question). |
| Other owner's book | 404 | 403 | Does not leak whether the book exists. |
| Test authentication | `ApiAuthenticationCustomizer` bean exists only in `src/test*`; enables httpBasic with an in-memory test user | Profile-gated user in main; `@WithMockUser` only | Nothing in the jar can authenticate. REST Assured needs real HTTP auth. |
| Default user | Exclude `UserDetailsServiceAutoConfiguration` | Keep the Boot default | Boot otherwise generates a password user, which is a runtime bypass. |
| Validation order | Security chain (-100), then the validator filter (order 0) and the `/api/**` interceptor | Validator before security | Anonymous callers get 401 and learn nothing about the schema. |
| 400 body | Constant `ProblemDetail` (`title`, `status`, fixed `detail`) | Validator messages | Satisfies "no internal details". Also set `server.error.include-*=never` and `fail-on-unknown-properties=true`. |
| Unknown route | Validator `path.missing` set to IGNORE; Spring returns 404 | 400 | Correct semantics. |
| OpenAPI version | 3.0.3 with `additionalProperties: false` | 3.1 | Validator support for 3.1 is unverified. |
| Image pipeline | `publish-image` job in `ci.yml`: `if: github.event_name == 'push' && github.ref == 'refs/heads/dev'`, `needs: ci-gate`, job-level `packages: write` | Separate `backend-image.yml` on every dev push; `workflow_run` | Satisfies the spec: only green SHAs reach GHCR. A separate workflow would push images of failing commits; `workflow_run` runs the default-branch file. `publish-image` is not a `ci-gate` need, so it never blocks merges. |
| Retag | `crane tag` pinned by sha256, then assert digest equality | `imagetools create` | `crane tag` copies manifest bytes unchanged, so the digest is identical. |
| Image | `FROM gcr.io/distroless/java21-debian13:nonroot@sha256:…`, `COPY build/libs/backend.jar`, `USER nonroot`, OCI `source` label; single linux/amd64 | Multi-stage build; Temurin | The CI runner builds the jar. Distroless has no shell. The base image is pinned. |
| Actuator | `exposure.include=health`, `access.default=none`, health `read-only`, `show-details=never` | Expose info/metrics | Issue criterion: health only. |
| Dependabot `/backend` | Groups `spring`, `testcontainers`, `pitest`, `contract` (rest-assured and atlassian oai); ignore Kotlin minor/major bumps | Ungrouped | Kotlin follows the Boot-managed version (2.3.21). |

## Data Flow

```
HTTP -> SecurityFilterChain --(anonymous)--> 401 (HttpStatusEntryPoint, no body)
          |(authenticated; /actuator/health is permitAll)
          v
   OpenApiValidation filter+interceptor --(violation)--> ApiErrorHandler -> 400 ProblemDetail
          v
   BooksController --OwnerId(auth.name)--> UseCase --> BookRepository port
                                                          v
                                    JdbcBookRepository: WHERE owner_id = :owner [AND id = :id]
                                                          v
                                       PostgreSQL (Flyway V1__create_books.sql)
```

## File Changes

| File | Action | PR |
|---|---|---|
| `backend/{settings,build}.gradle.kts`, `gradle/libs.versions.toml`, wrapper, `gradle.properties` | Create (JVM test suites `integrationTest` and `contractTest`, Kover, foojay toolchain 21) | 1 |
| `backend/src/main/kotlin/.../{domain,data,config}/**`, `application.yaml`, `db/migration/V1__create_books.sql` | Create | 1 |
| `backend/src/{test,integrationTest}/**` | Create | 1 |
| `.github/workflows/ci.yml` | Add `backend-tests` (PR1) and `contract-tests` (PR2); update `report` and `ci-gate` needs | 1, 2 |
| `.github/workflows/codeql.yml` | Install JDK 17 and 21; add a second traced `-p backend` compile | 1 |
| `.github/dependabot.yml` | Gradle `/backend` (PR1); docker `/backend` (PR3) | 1, 3 |
| `build.gradle.kts` (spotless `kotlinGradle` adds `backend/*.gradle.kts`), `.gitignore` (`/backend/build/`, `.kotlin/`), `lefthook.yml` (globs) | Modify | 1 |
| `scripts/test-kover-summary.sh` | Add a JVM `report.xml` fixture case (the parser is already report-agnostic) | 1 |
| `backend/src/main/resources/openapi.yaml`, `web/**`, `OpenApiValidationConfig`, `src/contractTest/**`, pitest config | Create | 2 |
| `backend/Dockerfile`, `.dockerignore` | Create | 3 |
| `.github/workflows/ci.yml` | Add `publish-image` job (push to `dev` only, `needs: ci-gate`, job-level `packages: write`) | 3 |
| `.github/workflows/promote.yml` | Add `check-image` job (job-level `packages: read`, fails if `sha-<commit>` is missing); `promote` gets `needs: check-image`, so `main` is never fast-forwarded without an image; add `retag-image` job (`needs: promote`, job-level `packages: write`, `contents: read`) | 3 |
| `README.md`, `openspec/config.yaml` | Modify | 3 |

## CI Job Graph

```
unit-tests ----+---------------------------> report (never gates; app + backend coverage)
backend-tests -+  (JDK 21: test integrationTest koverXmlReport koverHtmlReport)
contract-tests +  (JDK 21: contractTest pitest)  [PR2]
secret-scan ---+--> ci-gate (needs unit-tests, secret-scan, backend-tests, contract-tests)
ci-gate --> publish-image (push to dev only): bootJar -> build/push sha-<sha>, dev   [PR3]
promote.yml: check-image (sha-<sha> exists) -> promote (ff main) -> retag-image sha-<sha> -> staging    [PR3]
```

Artifacts: `allure-results-backend`, `coverage-backend`, `allure-results-contract`, and `pit-contract` (not under the `coverage-*` pattern). `report` stops merging coverage. It reads `coverage/coverage-unit/**/{htmlDebug,reportDebug.xml}` and `coverage/coverage-backend/**/{html,report.xml}`, publishes `site/kover` and `site/kover-backend`, and prints one coverage line per component.

## Testing Strategy (Strict TDD seams)

| Layer | What | Approach |
|---|---|---|
| Unit (`test`, JUnit 5) | Model invariants, use-case outcomes | In-memory fake `BookRepository`; no Spring. PIT target. |
| SQL guard (`test`) | Every `BookSql` SELECT/UPDATE/DELETE contains `owner_id = :owner` | Plain string assertions |
| Security slice (`test`) | 401 on `/api/books` and `/actuator/{env,info}`; 200 on health; no session cookie; context without customizer has no `UserDetailsService` | MockMvc plus `ApplicationContextRunner` (SP1) |
| Integration (`integrationTest`) | CRUD, cross-owner isolation, `NOT NULL owner_id` rejects raw insert, Flyway applies | Testcontainers PostgreSQL singleton, `@ServiceConnection` |
| Contract (`contractTest`) | One class per operation (`CreateBookTest`, `ListBooksTest`, `GetBookTest`, `UpdateBookTest`, `DeleteBookTest`) in `contract.books`; one method per error/security case; `BookLifecycleFlowTest`; `OpenApiCoverageTest` (spec operations match handler mappings, and each `operationId` has a test class) | `RANDOM_PORT` with REST Assured and the validator filter; JSON-path assertions only; status codes taken from the contract |
| Mutation | `domain.*`, threshold 80 | PIT 1.30.2; `excludedMethods` covers equals, hashCode, toString, copy*, component*; `avoidCallsTo` kotlin.jvm.internal |

Deviations from the restassured-standards skill: the tests are in Kotlin, without Lombok or schema models (JSON-path avoids the Jackson 2/3 mapping), and paths come from the spec instead of `.env`.

## Spike Fallbacks

| Spike | Fallback |
|---|---|
| SP1 Security DSL | Use the Java lambda `HttpSecurity` API. The behavior tests stay the same. |
| SP2 Validator (bytecode, Jackson 2/3, Spring 7) | SP3: openapi-generator 7.27.0 `kotlin-spring` (`interfaceOnly`, `useSpringBoot4`, `useJackson3`, `useTags`). Generated Bean Validation enforces the schema; contract tests use REST Assured `json-schema-validator`. If SP3 also fails, use hand-written `@Size`/`@Valid` and the same test module. |
| SP4 PIT on Gradle 9.9.1 | Pin the backend wrapper to a supported Gradle 8.14.x (separate build). Otherwise run PIT through a `JavaExec` task with the PIT CLI. Use `excludedClasses` for leftover Kotlin junk. |
| SP5 CodeQL two builds | Split into a matrix of two jobs with categories `/language:java-kotlin/app` and `/language:java-kotlin/backend`. |
| SP6 Dependabot digest | Document a manual digest bump in the README. |

## Threat Matrix

N/A for every row (documentation paths, repository selection, commit, push, PR commands). The change adds no git, shell, or PR automation. `promote.yml`'s git push is unchanged. The retag writes a registry tag, guarded by a digest-equality assertion. The HTTP routing threats (401, 400, owner isolation) are covered by the RED tests above.

## Migration / Rollout

Flyway V1 runs on an empty database. Nothing is deployed until #26 and #54. Roll back by reverting PR3, then PR2, then PR1.

## Open Questions

- [ ] `owner_id` format once #26 picks an identity provider (string subject assumed).
- [ ] Should `listBooks` get a page limit now or in #10? It is currently unbounded per owner.
- [ ] Does the dev ruleset "Require code scanning results" accept two CodeQL categories (only if SP5 fails)?
