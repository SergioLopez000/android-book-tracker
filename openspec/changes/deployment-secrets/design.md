# Design: Deployment secrets and credential rotation (#54)

## Technical Approach

Option B from the proposal: documentation plus an enforced contract, delivered in four work-unit commits, tests first in each. The backend validates the effective database connection details before the DataSource is built. Shell checks follow the existing checker plus harness pattern (`check-commit-msg.sh` / `test-check-commit-msg.sh`). The jar guard extends `verifyPackagedArtifact`. A scheduled workflow warns before `PROJECT_TOKEN` expires. No new dependency, service or infrastructure.

## Architecture Decisions

### Decision 1: Validate the effective `JdbcConnectionDetails` in a `BeanPostProcessor`

**Choice**: `config/DataSourceCredentialsValidator`, a `@Component` that implements `BeanPostProcessor`. In `postProcessAfterInitialization` it acts only on beans of type `JdbcConnectionDetails` (Spring Boot 4, `org.springframework.boot.jdbc.autoconfigure`; confirm the package while implementing). It reads `jdbcUrl`, `username` and `password`. A getter that throws counts as missing: `PropertiesJdbcConnectionDetails.getJdbcUrl()` throws when no URL is set and no embedded database is on the classpath. A value that is null or blank (`isNullOrBlank`) also counts as missing. If anything is missing it throws `MissingDatabaseConfigurationException` (an `IllegalStateException`) and returns any other bean unchanged. The validation logic is a pure `internal fun missingVariables(details): List<String>`.

**How it coexists with the test contexts** (the main risk):

| Context | `JdbcConnectionDetails` bean | Validator effect |
|---|---|---|
| Production / image | `PropertiesJdbcConnectionDetails` built from `SPRING_DATASOURCE_*` | Validated. Fails fast if a value is missing or blank |
| Unit suite (`NoDatabaseTestConfig` excludes `DataSourceAutoConfiguration`) | None | Never invoked, so no-op |
| `integrationTest` / `contractTest` (`@ServiceConnection`) | Container details with the container's real random credentials | Always passes |

`DataSourceAutoConfiguration` takes `JdbcConnectionDetails` as a factory-method argument, so the details bean is fully post-processed before Hikari is created. Our message therefore wins over Spring's generic "Failed to determine a suitable driver class". No test-only property, profile or bypass flag exists, so `verifyPackagedArtifact` has nothing new to allow.

**Alternatives considered**:

| Option | Tradeoff | Decision |
|---|---|---|
| `@ConfigurationProperties` + `@Validated` on `spring.datasource` | `@ServiceConnection` does not fill `DataSourceProperties`, so it fails every Testcontainers context | Rejected |
| `EnvironmentPostProcessor` / `ApplicationEnvironmentPreparedEvent` listener | Runs before beans exist and cannot see `@ServiceConnection`. It would need a disable flag in tests, which is a bypass in production code | Rejected |
| `${SPRING_DATASOURCE_PASSWORD}` placeholders with no default in `application.yaml` | A blank value still passes. The message is a generic placeholder error. Unit contexts would also need the values | Rejected |
| A `FailureAnalyzer` for a formatted startup banner | Clearer output, but needs one more class plus `spring.factories` | Deferred. The exception message is already explicit |

**Rationale**: Validating what Spring will actually use is correct in every context by construction, needs no switches, and lives in the `config/` composition root without touching `domain/`.

**Message** (names only, never values):
`Missing required database configuration: SPRING_DATASOURCE_PASSWORD. Set SPRING_DATASOURCE_URL, SPRING_DATASOURCE_USERNAME and SPRING_DATASOURCE_PASSWORD in the environment (see docs/secrets.md).`

### Decision 2: Smoke case "fails without credentials"

**Choice**: In `scripts/test-image-smoke.sh`, before starting the database, run the image twice. First with no `SPRING_DATASOURCE_*` at all. Second with the URL and username set and `SPRING_DATASOURCE_PASSWORD=` (blank), using an unreachable host to show that no connection is attempted. Poll `docker inspect -f '{{.State.Status}}'` until `exited` (at most 60 s). Then assert:

- `ExitCode != 0`
- the logs contain `Missing required database configuration` and the expected variable name
- the logs do not contain `smoke-dummy`

The script stays local only (it needs Docker), as it is today.

**Alternatives**: a `@SpringBootTest` in the unit suite with no DB. Rejected because `NoDatabaseTestConfig` is component-scanned into every unit-suite context, and the property-binding paths are covered by an `ApplicationContextRunner` test instead.

### Decision 3: Jar scan in `verifyPackagedArtifact`

**Choice**: Add two regex classes. Scope them to text entries under `BOOT-INF/classes/` (`.yaml`, `.yml`, `.properties`, `.json`, `.xml`, `.sql`). Never scan `BOOT-INF/lib/`, so third-party jars cannot produce false positives.

| Pattern | Meaning |
|---|---|
| `(?i)^\s*[\w.-]*(password\|secret\|token\|api[-_.]?key\|credential)[\w.-]*\s*[:=]\s*(?!\$\{[A-Z0-9_]+\}\s*$)\S` | Credential key with a literal value or a placeholder default. Only `${NAME}` with no default is exempt |
| `(?i)jdbc:[a-z0-9]+://[^/\s:@]+:[^@\s]+@` and `(?i)jdbc:[^\s]*[?&;]password=` | `jdbc:` URL with embedded credentials |

Also add `^\s*(datasource\|spring\.datasource)` keys with literal `url`, `username` or `password` in `application*.yaml` (no credential defaults in any profile). Also add `verifyPackagedArtifact` to the `backend-tests` CI command, because CI currently runs `test integrationTest` and not `check`.

**False positives**: scope limits plus the placeholder exemption. There is no allow-list file. The current `application.yaml` and `openapi.yaml` produce no matches (to be confirmed by the first run).

**RED evidence**: add a temporary violating line to `application.yaml` locally, show that the task fails, then revert. Record this in the apply evidence (the build script has no test harness).

### Decision 4: Static contract as a checker plus harness, run locally through lefthook

**Choice**: `scripts/check-secrets-contract.sh [root]` (default: the repo root) and `scripts/test-secrets-contract.sh`. The harness runs the checker on the real repo (it must pass) and on `mktemp -d` fixtures (each must fail with the expected `FAIL:` line). Checks, awk/grep only, in the same style as `test-image-config.sh`:

1. Allow-list: the set of `secrets\.[A-Za-z0-9_]+` names across `.github/workflows/*.yml` must be a subset of `ALLOWED_SECRETS=(PROJECT_TOKEN)`. `secrets: inherit` is forbidden. `GITHUB_TOKEN` must be written as `github.token`.
2. Every workflow has a top-level `^permissions:`. Every job under `jobs:` (a two-space key) has a four-space `permissions:` line in its body. `write-all` and `read-all` are forbidden.
3. Dockerfile: no `ENV`/`ARG` whose name matches `(?i)(pass|secret|token|key|credential|datasource)`.
4. Unit 3 adds the drift guard (Decision 6).

Making this pass requires job-level `permissions:` on `ci.yml` (`unit-tests`, `backend-tests`, `contract-tests`, `secret-scan`, `ci-gate`: `contents: read`), `board-sync.yml` `sync` (`contents: read`) and `promote.yml` `promote` (`contents: write`, `checks: read`). The `promote.yml` workflow level drops to `contents: read`.

**Where it runs**: locally only, never in CI (user decision; matches the "No Static Analysis in CI" main spec). Add a lefthook `pre-commit` job `secrets-contract` with glob `{.github/workflows/*.{yml,yaml},backend/Dockerfile,docs/secrets.md,scripts/*secrets-contract*.sh}` running `bash scripts/test-secrets-contract.sh`, in the same style as the existing `actionlint` job. `test-image-config.sh` already follows this local-only pattern (README table and the `openspec/config.yaml` verify rule). No CI job or `ci-gate` need changes for this script. Commits must never bypass hooks (`--no-verify` is forbidden); the apply phase reports the lefthook summary for each commit as evidence.

**Alternatives**: extending `test-image-config.sh` (mixes concerns, has no fixtures); a Gradle `Exec` task in `backend/` (the backend build would reach outside its root); a new CI job (changes `ci-gate` semantics).

### Decision 5: `docs/secrets.md` (English)

Sections, with exact headings that the guard checks:

- **Purpose and rules**: env-only, never in the repo or image, least privilege.
- **`## Inventory`**: table `| Secret | Class | Owner | Scope | Location | Consumers | Rotation | Status |`. The owner is always `` `SergioLopez000` ``. Active rows: `PROJECT_TOKEN`, `GITHUB_TOKEN` (automatic), `GOOGLE_BOOKS_API_KEY` (local `local.properties`, shipped in the APK, treated as leaked). Planned rows with status `planned: created by #8` or `#29`/`#26`: `SPRING_DATASOURCE_URL`/`USERNAME`/`PASSWORD` per environment, Neon/host credentials, Android release keystore, server-side Google Books key, JWT signing secret. The `PROJECT_TOKEN` row records its expiry date.
- **`## Environments`**: repo-scoped secrets only for CI automation (`PROJECT_TOKEN`). Runtime secrets go to the GitHub Environments `dev` and `staging` (`staging` keeps required reviewers; `dev` is created by #8). There is a host-agnostic fallback if #8 picks host dashboard variables.
- **`## Rotation cadence`**: one row per class. Classic PAT: at most 90 days. DB passwords: 180 days or on leak. Provider API keys: 365 days or on leak. Signing secrets: 180 days. Keystore: never rotated, backed up, with a compromise procedure. `GITHUB_TOKEN`: automatic.
- **`## Runbook`**: the on-leak steps are create new, set it in each environment store, restart or redeploy, verify, revoke the old one, then record the date. `PROJECT_TOKEN` rotation links to the README and covers updating the expiry row and closing the reminder issue. The Google Books section is gated on #29, with manual restriction steps for now: Google Cloud Console, APIs & Services, Credentials, the key, API restriction to Books API only, then application restriction to Android apps using the `applicationId` from `app/build.gradle.kts` plus the SHA-1 fingerprints of the signing certificates. It also notes that revoking the key needs a new APK and that old builds lose the fallback.
- **Adding a secret**: checklist (inventory row, allow-list entry, scoped permissions).

### Decision 6: Drift guard

**Choice**: The checker extracts the backticked names in the first column of the `## Inventory` table. It fails if any `secrets.*` name used in workflows, or any `ALLOWED_SECRETS` entry, is missing. It fails if a required heading is missing. It fails if an active row lacks `` `SergioLopez000` ``. Harness fixtures cover a missing row and a missing heading. The README gets a Spanish section "Secretos y rotación" that links to the doc and the reminder, plus a row in the script table.

### Decision 7: `PROJECT_TOKEN` expiry reminder

**Choice**: `.github/workflows/token-expiry.yml`.

- Triggers: `schedule` (weekly, `17 6 * * 1`, which with a 14-day threshold gives at least two warnings) and `workflow_dispatch`.
- Concurrency: `token-expiry`, with `cancel-in-progress: false`, which prevents duplicate issues from racing runs.
- Workflow-level permissions: `contents: read`. Actions are pinned to their major version (`actions/checkout@v7`), as in the repo.

| Job | Permissions | Token | Work |
|---|---|---|---|
| `read-expiry` | `contents: read` | `secrets.PROJECT_TOKEN` | Sparse checkout of `scripts`, `persist-credentials: false`. Runs `curl -s -D - -o /dev/null --max-time 20 -K - https://api.github.com/rate_limit`, with the `Authorization: Bearer` header passed on stdin config (not in argv), piped to `scripts/token-expiry.sh`. Writes outputs `state`, `days`, `expires`. An empty token gives a notice and exit 0 |
| `notify` | `issues: write` only (no checkout) | `github.token` | `if: state != 'ok'`. Runs `gh label create secret-rotation --force`. Looks up an open issue with label `secret-rotation` and an exact title that depends on the state: `PROJECT_TOKEN rotation needed` for expiring/expired/invalid, `PROJECT_TOKEN expiry header missing` for missing/unparseable (two distinct issues, each unique while open, per the issue-workflow spec) (`gh issue list --json` + `jq`), then `gh issue edit` or `gh issue create`, always with `--body-file` |

`PROJECT_TOKEN` and a write-capable token never share a job.

`scripts/token-expiry.sh` reads the headers on stdin, strips CR, and matches `github-authentication-token-expiration` case-insensitively. It parses with `date -u -d` and honors `NOW_EPOCH` for tests. It prints one of these states:

- `ok`
- `expiring` (fewer than 14 days)
- `expired`
- `invalid` (HTTP 401)
- `missing-header`
- `unparseable` (the raw date value is echoed; it is not secret)

`missing-header` and `unparseable` open the warning issue. The header format is undocumented, so the first run is a manual `workflow_dispatch` to confirm it. The harness `scripts/test-token-expiry.sh` covers every state, CRLF line endings and header-name casing.

## Data Flow

    env SPRING_DATASOURCE_* -> PropertiesJdbcConnectionDetails -> Validator(BPP) -> Hikari DataSource -> Flyway
    @ServiceConnection container -> container JdbcConnectionDetails -> Validator (passes)
    NoDatabaseTestConfig -> no details bean -> validator idle

    schedule/dispatch -> read-expiry (PROJECT_TOKEN, curl headers) -> token-expiry.sh -> outputs
                      -> notify (GITHUB_TOKEN issues:write) -> find-or-create single issue

## File Changes

| File | Action | Unit | Est. lines |
|---|---|---|---|
| `scripts/check-secrets-contract.sh` | Create | 1 (+drift in 3) | 110 |
| `scripts/test-secrets-contract.sh` | Create | 1, 3 | 100 |
| `.github/workflows/ci.yml`, `board-sync.yml`, `promote.yml` | Modify (job permissions, `verifyPackagedArtifact` in `backend-tests`) | 1, 2 | 28 |
| `lefthook.yml` | Modify (`secrets-contract` pre-commit job) | 1 | 3 |
| `backend/src/main/kotlin/com/librosleidos/backend/config/DataSourceCredentialsValidator.kt` | Create | 2 | 45 |
| `backend/src/test/kotlin/com/librosleidos/backend/config/DataSourceCredentialsValidatorTest.kt` | Create | 2 | 90 |
| `backend/build.gradle.kts` | Modify | 2 | 25 |
| `scripts/test-image-smoke.sh` | Modify | 2 | 35 |
| `docs/secrets.md` | Create | 3 | 110 |
| `README.md`, `openspec/config.yaml` (verify rule) | Modify | 1, 3, 4 | 25 |
| `.github/workflows/token-expiry.yml` | Create | 4 | 70 |
| `scripts/token-expiry.sh`, `scripts/test-token-expiry.sh` | Create | 4 | 120 |

Total is about 760 lines (budget 800; each unit is 150 to 250 lines and is a candidate for its own PR).

## Interfaces / Contracts

```kotlin
class MissingDatabaseConfigurationException(val missing: List<String>) : IllegalStateException(/* message above */)
internal fun missingVariables(details: JdbcConnectionDetails): List<String> // ordered URL, USERNAME, PASSWORD
```

`token-expiry.sh` contract: stdin is the raw headers. stdout is `state=<s>`, `days=<n|>`, `expires=<raw|>`. The exit code is always 0 for a parsed state and non-zero only on a usage error.

## Testing Strategy

| Layer | What | Approach |
|---|---|---|
| Unit | `missingVariables`: each var null, blank or whitespace; a throwing getter; all present; non-details beans pass through untouched | Fake `JdbcConnectionDetails` in Kotlin, written first |
| Unit (context) | Real binding path | `ApplicationContextRunner` with `DataSourceAutoConfiguration` and the validator: no properties fails with our message, blank password fails, full properties start (Hikari is lazy, so no connection) |
| Regression | Coexistence | The existing unit, `integrationTest` and `contractTest` suites stay green unchanged |
| Shell | Contract, drift, expiry | Harnesses with fixtures and negative controls |
| Packaging | Jar scan | `verifyPackagedArtifact` in `check` and CI, plus manual RED evidence |
| Smoke (local) | Missing and blank credentials | `test-image-smoke.sh` |

## Threat Matrix

| Boundary | Applicability | Design response | Planned RED tests |
|---|---|---|---|
| Documentation-like paths | N/A: `docs/secrets.md` is parsed as text by awk and never executed | None | None |
| Git repository selection | N/A: scripts resolve the root from `BASH_SOURCE` or an explicit argument and run no git commands | None | None |
| Commit state | N/A: no commit automation | None | None |
| Push state | N/A: no push automation (`promote` only changes its permissions block) | None | None |
| PR/issue commands | Applicable: `gh issue list/create/edit` in `notify` | `--repo "$GITHUB_REPOSITORY"`, fixed title, body via `--body-file`, untrusted header value never `eval`'d or interpolated into the shell | Harness: a header value with `$(...)` and quotes is echoed literally; `unparseable` state |
| Token handling (extra) | Applicable | Bearer header on curl stdin config, never in argv or logs; curl body discarded (`-o /dev/null`), only headers reach the parser | Harness asserts the script output never contains the token fixture |

## Migration / Rollout

No data migration. Local runs now need `SPRING_DATASOURCE_*`, which was already implicitly required. Roll back by reverting any unit independently. If unit 1 is reverted, the drift guard in unit 3 must be reverted with it. Disable the reminder in the Actions UI. Scheduled workflows run only from the default branch, so the reminder becomes active once it reaches that branch.

## Open Questions

- [ ] Confirm the Spring Boot 4 package and the throwing behavior of `PropertiesJdbcConnectionDetails` getters (covered by the context test).
- [ ] Confirm the `github-authentication-token-expiration` format on the first manual dispatch.
- [ ] Confirm the repository default branch for the `schedule` trigger.
