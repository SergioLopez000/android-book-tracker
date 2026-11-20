# Proposal: Spring Boot Backend Skeleton With Contract Tests (#6)

## Intent

The app is local-only. Later issues (#10, #26, #54) need a secure, tested Kotlin + Spring Boot backend base with contract-first tests.

## Scope

### In Scope
- Separate Gradle build under `/backend`: own wrapper, settings and catalog. JDK 21, Kotlin 2.3.21 (Boot 4.1 managed). The app stays on JDK 17 and Kotlin 2.4.21.
- Hexagonal domain, ports and use cases (Strict TDD). PostgreSQL persistence through a `JdbcClient` adapter with an explicit `owner_id` filter in every query. The first Flyway migration creates the books table with a non-null `owner_id`. Integration tests run against PostgreSQL via Testcontainers.
- Deny-by-default security: stateless, 401, Actuator health only. No runtime bypass. Tests use a test-only user.
- `backend/src/main/resources/openapi.yaml` as the source of truth for books CRUD. Hand-written controllers. openapi-request-validator 3.0.0 as a runtime filter and as a REST Assured filter. One spec-coverage test per operation.
- Requests that violate the OpenAPI schema (unknown fields, oversized strings, invalid types) get 400 without stack traces or internal details.
- PIT on domain and use cases, `mutationThreshold` 80, blocking. Kotlin-generated junk mutants are excluded (`equals`, `hashCode`, `toString`, `copy`, `componentN`).
- CI: `backend-tests` and `contract-tests` (with PIT) added to `ci-gate` needs and shown in `report`. CodeQL builds the backend. Dependabot covers gradle and docker for `/backend`.
- Distroless `java21-debian13:nonroot` image pinned by digest. The jar is built on the runner. GHCR push on `dev` with tags `sha-<commit>` and `dev`. `promote.yml` retags the same digest as `staging`, with `packages: write` on that job only. The package stays private.
- README updates. Optional fix of the stale `openspec/config.yaml` context: Kotlin 2.4.21, Gradle 9.9.1, backend commands.

### Out of Scope
- Authentication (#26), deployment and secrets (#54), package visibility (#8).
- SonarCloud. It is replaced in the DoD by local hooks, CodeQL and secret-scan.
- openapi-generator. It is used only if SP2 fails.

## Capabilities

### New Capabilities
- `backend-api`: the backend build, domain, persistence isolation, security, OpenAPI contract, contract tests, PIT and container image.

### Modified Capabilities
- `ci-pipeline`: Unit Test Job, Artifact Contract, ci-gate needs, Out of Scope Boundaries, CodeQL, Dependabot, GHCR least privilege, promote retag.
- `code-quality-gates`: Formatting, Detekt, Coverage Report and Pre-commit Hook now cover `/backend`.

## Approach

- Apply spikes SP1, SP2, SP4, SP5, SP6 (research.md) run first. A failed spike reopens its decision.
- 3 sequential PRs into `dev` (stacked-to-main):
  - PR1 (`feat/6-backend-skeleton`): build, domain, persistence, security, CI; SP1, SP4, SP5, SP6.
  - PR2: OpenAPI, controllers, contract tests, PIT; SP2.
  - PR3: Docker, GHCR, promote retag, Dependabot docker, README.
- `Refs #6` on PR1 and PR2, `Closes #6` on PR3. Promote only after PR3.

## Affected Areas

| Area | Impact |
|---|---|
| `backend/**` | New |
| `.github/workflows/ci.yml`, `codeql.yml`, `promote.yml`, new GHCR workflow | Modified/New |
| `.github/dependabot.yml`, `lefthook.yml`, root spotless, `scripts/kover-summary.sh`, `.gitignore` | Modified |
| `README.md`, `openspec/config.yaml` | Modified |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| A spike fails (validator, PIT, CodeQL) | Med | Spikes run first and reopen the affected decision. |
| Jackson 2 (validator) and Jackson 3 (Boot) coexist | Med | SP2. Use JSON-path assertions. |
| Report job misses JVM Kover names | High | Update `kover-summary.sh`. |
| The API cannot be used before #26 | Certain | Accepted. Deny-by-default; no deployment. |
| Two version catalogs drift | Med | Dependabot covers both directories. |

## Rollback Plan

Revert each PR in reverse order (PR3, PR2, PR1). Delete the GHCR package versions if needed. Restore the previous `ci-gate` needs. The app build is unaffected because the backend build is separate.

## Dependencies

- Issues #26 (auth) and #54 (secrets) block deployment. Issue #8 decides package visibility.

## Success Criteria

- [ ] `ci-gate` is green with `backend-tests` and `contract-tests` as needs.
- [ ] Every OpenAPI operation has a route and a contract test.
- [ ] PIT score is at least 80 on domain and use cases.
- [ ] Unauthenticated requests get 401. Every query filters by `owner_id`, and `owner_id` is non-null.
- [ ] Schema-violating requests get 400 with no stack trace or internal details.
- [ ] Integration tests run against PostgreSQL via Testcontainers.
- [ ] CodeQL analyzes backend Kotlin files.
- [ ] The image runs as nonroot, is pinned by digest, and is pushed to GHCR. The same digest is retagged on promote.
