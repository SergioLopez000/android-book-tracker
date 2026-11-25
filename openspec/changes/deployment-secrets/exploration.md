# Exploration: deployment-secrets (issue #54)

## 1. Current secret and credential inventory (as found in the repo)

| # | Credential | Defined in | Consumed by | Notes |
|---|---|---|---|---|
| 1 | `PROJECT_TOKEN` (classic PAT, `project` scope) | GitHub repo secret (UI only). Procedure in README "Token `PROJECT_TOKEN`" (Spanish): classic PAT, only `project`, expiry at most 90 days. | `.github/workflows/board-sync.yml` as `GH_TOKEN: ${{ secrets.PROJECT_TOKEN }}`; `scripts/board-sync.sh` (a missing token is a notice and exit 0). | Only `secrets.*` reference in all workflows. Repo-level, not Environment-scoped. Rotation text exists in README (generate a new PAT, replace the secret, revoke the old one). No owner, no expiry date recorded, no reminder. `board-sync` runs only for same-repo PRs and skips dependabot. |
| 2 | `GOOGLE_BOOKS_API_KEY` | Git-ignored `/local.properties` (the property `GOOGLE_BOOKS_API_KEY`), read in `app/build.gradle.kts` lines 16-22, 37 | `BuildConfig.GOOGLE_BOOKS_API_KEY`, then `AppContainer.kt:59`, then `GoogleBooksMetadataSource` builds `...?q=..&key=$apiKey` (the key is compiled into the APK and sent as a query parameter). | This is the key "that has shipped in the APK". It is not in CI (CI builds with an empty key, so the fallback is disabled). Not in the README. #29 will move third-party calls server-side. After #29, rotate/revoke this key. There is no release signing config and no keystore in the repo (`release { isMinifyEnabled = false }`). |
| 3 | `GITHUB_TOKEN` (`github.token`) | Automatic per run | `ci.yml` `publish-image` (`packages: write`, GHCR login); `promote.yml` `check-image` (`packages: read`), `promote` (`contents: write`, pushes `main`), `retag-image` (`packages: write`); `ci.yml` `report` (`contents: write`, `pull-requests: write`, gh-pages and sticky comment); `pr-cleanup.yml` (`contents: write`). | Not a stored secret and rotates automatically. Already least-privilege: workflow-level `contents: read` with job-level elevation. `scripts/test-image-config.sh` enforces that `packages: write` exists only in `publish-image` and `retag-image`, and that there is no `pull_request_target`. |
| 4 | Backend DB credentials (`SPRING_DATASOURCE_URL`, `_USERNAME`, `_PASSWORD`) | Nowhere. `backend/src/main/resources/application.yaml` has no `datasource`, no profile and no placeholder. | Spring Boot relaxed binding of `SPRING_DATASOURCE_*` env vars. README line 143: "la configuración llega al arrancar (`SPRING_DATASOURCE_*`)". Proven only by `scripts/test-image-smoke.sh` (a local script, not in CI) with throwaway dummy credentials (`smoke-dummy`). | There is no deployed backend yet. Flyway uses the same DataSource (no separate migration user). Tests use Testcontainers `postgres:17-alpine` through `@ServiceConnection` (`PostgresContainerConfig`, `ContractPostgresConfig`), so no credentials are needed. `NoDatabaseTestConfig` excludes DataSource/Flyway for unit tests. |
| 5 | Provider keys (server-side) | Do not exist. | `backend/src/main/resources/openapi.yaml` has no third-party client. Auth (#26) and server-side provider calls (#29) are not implemented. | Future: JWT or auth secrets (#26), Google Books server key (#29). |
| 6 | Android release signing keystore | Does not exist. | Mentioned only in #8 (APK distribution). | Future (#8). |
| 7 | Test dummies: `test-password-not-a-secret` | `backend/src/test/.../TestApiAuthentication.kt`, `backend/src/contractTest/.../ContractTestAuthentication.kt` | Tests only. | Deliberately inert. `verifyPackagedArtifact` (backend/build.gradle.kts lines 119-158) fails `check` if the jar contains `testsupport`/`TestApi*` classes or a security-bypass property (`spring.security.user`, `permit-all`, etc.). |

Hard-coded credential check:
- Dockerfile: distroless base pinned by digest, a single `COPY`, `USER nonroot`, no `ENV`/`ARG`. `scripts/test-image-config.sh` asserts "no ENV or ARG" and a single digest-pinned `FROM`.
- `.gitignore` covers `/local.properties` and `/backend/build/`.
- No docker-compose, no `.env*`, no `.env.example`, no `.gitleaks.toml` (gitleaks uses its default ruleset), no `SECURITY.md`, no `docs/` directory, no `CODEOWNERS`.
- No real secret found in tracked files. `local.properties` contents were not read (git-ignored). The only credential-like literals are the test dummies and the smoke script's `smoke-dummy`.
- CI never scans image layers, only the commit range. The image check is static (`test-image-config.sh`). A scan of the built jar/image for secret patterns does not exist.
- One workflow comment documents a naming workaround: the `AVD_CACHE` env var is "named without key so gitleaks' generic-api-key rule does not flag it".
- GitHub Environments: `promote.yml` uses `environment: staging` (QA approval gate) but it holds no secrets. No `dev` Environment is referenced by any workflow.

## 2. How the backend gets DB config today

- `application.yaml` sets only app name, jackson strictness, error-detail hiding and a health-only actuator. No profile files (`application-dev/staging`), no placeholders such as `${DB_URL}`.
- Starters: `spring-boot-starter-jdbc`, `starter-flyway`, `flyway-postgresql`, `postgresql`. With no `SPRING_DATASOURCE_URL` the app fails to start (no DataSource can be configured), but with a generic Spring error rather than a clear "missing required secret" message. An empty password is accepted syntactically.
- Fail-fast on missing config: implicit only, not tested, not documented.
- Test setups: `test` (no DB, mocked JdbcClient), `integrationTest` and `contractTest` (Testcontainers Postgres 17 via `@ServiceConnection`, which injects the container's own random credentials, never from repo).
- No test starts the app without DB env and asserts a clear failure, and none asserts the image or jar contains no credentials beyond `verifyPackagedArtifact`'s two regex classes.

## 3. Existing docs and specs

Docs: only `README.md` (Spanish), with a "Flujo de issues" section including `PROJECT_TOKEN` creation and rotation, a "Imagen del backend (GHCR)" section ("no lleva variables ni secretos"), and a CI section describing `secret-scan`. No `docs/` folder, no SECURITY.md. Documents are in Spanish in this repo (README) while openspec artifacts are English.

Main specs relevant (requirements to respect):
- `backend-api` "Hardened Container Image" (line 191): the image MUST NOT contain secrets or the test-only user; jar built outside the image. Intro (line 5) states "deployment and secrets (#54)" is out of scope of that base, so #54 is explicitly deferred work and will need a delta (ADDED requirement for config via env and fail-fast).
- `issue-workflow`: `PROJECT_TOKEN` MUST be read by `board-sync`, a missing secret MUST be a notice and exit 0 (line 114), a classic PAT with `project` scope only and a finite expiry (line 163), and the README MUST document creation and rotation (line 171). Any switch to a GitHub App or fine-grained token would MODIFY these requirements (the "missing token" behavior can stay).
- `ci-pipeline`: `ci-gate` depends only on four jobs; `secret-scan` job requirement (line 315); least-privilege checks (e2e-tests holds only `contents: read` and "no `secrets.*` reference", line 473); `packages: write` only in the publish/retag jobs; `staging` retag requirement (line 443). New workflow jobs must not touch the fixed job names.
- `code-quality-gates`: gitleaks local pre-commit plus CI scan (line 4, 118). If custom gitleaks rules or a config are added they must keep the scan passing and the range script tests green.
- `e2e-testing` (line 108): no secrets in tests or logs.

## 4. Scope now vs. blocked on #8

No environment is deployed. The only artifacts are GHCR images tagged `dev` and `staging` (private package).

| Acceptance criterion | Can be fully met now? | What is possible now | What waits for #8 (or #29 / #26) |
|---|---|---|---|
| 1. Dev/staging backend reads DB credentials and provider keys from environment secrets, never from repo or image | Partially | Make the contract explicit and enforced: required env vars named and documented (`SPRING_DATASOURCE_URL/USERNAME/PASSWORD`), fail-fast startup validation with a clear message, tests that the jar, image and Dockerfile contain no credentials, extend the smoke test to prove startup fails without them. | Real values in a host's secret store, Neon dev/staging branches and DB users per env (#8). Provider keys do not exist server-side until #29. |
| 2. Inventory doc: owner, scope, location, rotation per secret | Yes | `docs/` secret inventory covering rows 1-6 above (including planned secrets marked "not yet created"), plus a doc-format test. | Rows for the host, Neon, keystore and per-env DB users get filled when #8 creates them. |
| 3. Leaked provider key: rotation revokes old and activates new in every environment | Procedure only | Document the runbook (create the new key, set in each environment's secret store, redeploy or restart, revoke the old key, verify). Rotating a secret store value with no code change is satisfied by design if config is env-only. | An actual drill and "every environment" are untestable until #8 has environments and #29 moves the key server-side. The shipped APK key cannot be rotated "without a redeploy" of the app; it needs revoke plus a new APK, which is why it waits for #29. |
| 4. CI uses least-privileged token; `PROJECT_TOKEN` has expiry and documented rotation | Mostly yes | Workflow `permissions:` are already minimal and enforced for packages; add static checks (all workflows declare `permissions:`, only the expected `secrets.*` references exist, no `pull_request_target`). Record `PROJECT_TOKEN` expiry date, owner and rotation in the inventory; optionally a scheduled expiry reminder. Existing README already has the procedure. | Per-environment CI secrets (staging deploy credentials, keystore, Neon API) only arrive with #8. |

Explicit dependency: the issue says #54 blocks #8, so #8 should not start until at least the inventory, the env-var contract and the CI token policy exist. That is consistent with doing the scope above first.

## 5. Approach options

### A. Documentation plus conventions only
Add `docs/secrets.md` (inventory and rotation runbook), update README links. No code or tests.
- Pros: smallest, no risk. Meets criterion 2 and the procedural part of 3 and 4.
- Cons: criterion 1 and "never from repo or image" are not enforced; the strict-TDD project convention (tests first) has nothing to drive.
- Effort: small.

### B. Docs plus enforced contract (recommended)
Everything in A, plus:
1. Backend: a `@ConfigurationProperties`/startup validator (or `spring.datasource.*` placeholders in `application.yaml` with no defaults) that fails fast with a clear message when `SPRING_DATASOURCE_URL`, `USERNAME` or `PASSWORD` is missing or blank. No credential defaults in any profile file. Keep the hexagonal layering (config lives in `config/`).
2. Tests (written first): unit test for the validator; extend `verifyPackagedArtifact` to also scan the jar for credential-like patterns and `jdbc:` URLs with embedded passwords; static script (like `test-image-config.sh`) asserting workflows reference only the allow-listed `secrets.*` names (currently only `PROJECT_TOKEN`), every workflow and job declares `permissions:`, no `ENV`/`ARG` with secret-like names in the Dockerfile; a test that the inventory doc lists every `secrets.*` name found in workflows (drift guard); extend `scripts/test-image-smoke.sh` with a "fails without credentials" case.
3. GitHub Environments `dev` and `staging` documented as the home of env-scoped secrets (staging already exists; `dev` to be created), with required reviewers on staging. Creating them is a UI/admin step, same pattern the repo already uses for the board.
4. Optional gitleaks config (`.gitleaks.toml`) with an allowlist for known test dummies, only if false positives appear.
- Pros: criteria 1, 2 and 4 enforceable in CI, 3 documented, fits the repo's existing "static check script" pattern, no new infrastructure.
- Cons: more files to review; the validator is a behavior change to a base backend that has no deployment yet; static checks must be kept in sync with workflows.
- Effort: medium.

### C. B plus full secret manager (Vault, cloud secret manager, SOPS)
- Pros: stronger rotation story.
- Cons: no host exists yet (#8 chooses it), would pre-empt #8's choice, extra cost and complexity for a free-tier hobby app. Not recommended now.

### PROJECT_TOKEN options (orthogonal)
- Keep a classic PAT with `project` scope, 90-day maximum, owner and expiry tracked in the inventory (current spec and README). Pro: no change, spec-compliant. Con: classic PATs cannot be restricted to this repo and expire manually.
- Fine-grained PAT: for a user-owned account, fine-grained PATs may not access user-owned Projects v2 (README line 265 reached the same conclusion for `GITHUB_TOKEN`; not re-verified against live GitHub docs, confirm before relying on it).
- GitHub App installed on the account: short-lived installation tokens, no manual expiry, can be limited to one repo and the Projects permission. Pro: best long-term security and no human rotation. Con: setup effort (create app, store app id and private key as secrets, add a token-minting step using `actions/create-github-app-token`), the private key becomes the new long-lived secret with its own rotation, changes the `issue-workflow` spec text, and a user-owned project may not be reachable by installation tokens (verify first).
- Recommendation: keep the classic PAT for #54 (meets "has an expiry and documented rotation"), record owner and expiry, optionally add an expiry-reminder workflow, and evaluate a GitHub App as a follow-up issue.

## 6. Recommendation

Option B, delivered in this order (work-unit commits, tests first):
1. Static contract tests for workflows and Dockerfile (secrets allow-list, `permissions:` present, no secret-like ENV/ARG).
2. Backend fail-fast config validation plus its unit test; smoke test extended for the missing-credentials case; `verifyPackagedArtifact` extended.
3. `docs/secrets.md` (inventory and rotation runbook) and drift-guard test; README link and a short "Secretos y rotación" pointer. Mark rows for future secrets (Neon, host, keystore, server-side provider key) as "created by #8/#29".
4. Document the Environments model (`dev`, `staging`) and least-privilege rules for new CI credentials; keep `PROJECT_TOKEN` as classic PAT with documented owner, expiry and rotation.

Defer: the actual rotation of the shipped Google Books key until #29 lands (document it as a gated step in the runbook); GitHub App migration; any managed secret store.

## 7. Open product decisions for the user

1. Scope of #54 vs #8: accept that criteria 1 and 3 are met as enforced contract plus documented procedure only, with the live drill and real environment values deferred to #8? Or should #54 wait for #8?
2. Fail-fast startup validation: add application code (a config validator that rejects missing or blank DB credentials with a clear error) now, or rely on Spring's implicit failure and only document it?
3. Language and location of the inventory: `docs/secrets.md` in English (like openspec artifacts) or Spanish (like README)? Or inside README? Include planned-but-not-yet-existing secrets as rows?
4. Owner field: who is the named owner for every secret (a single person, the GitHub user, or a role)?
5. PROJECT_TOKEN: keep the classic PAT (maximum 90 days) or migrate to a GitHub App now? If keeping it, add an automated expiry reminder (scheduled workflow opening an issue) or rely on the documented manual rotation?
6. GitHub Environments: create `dev` now (empty) and attach `staging` reviewers and secrets conventions, or create them only in #8? Environment-scoped, repo-scoped, or both?
7. Google Books key: confirm rotation is gated on #29 and whether the key is restricted now (API restriction, Android app restriction by package and signing SHA) as a stop-gap; confirm it is acceptable to leave the APK key live until #29.
8. Rotation cadence: define the policy per secret class (PAT 90 days max, DB passwords, provider keys, keystore) or only the "on leak" procedure?
9. Extra scanning: scan image layers or the built jar for secrets in CI (gitleaks on jar contents, or Trivy secret scan), or is the extended `verifyPackagedArtifact` plus commit-range gitleaks enough?
10. Spec changes: confirm this change adds a delta to `backend-api` (config via env and fail-fast) and `ci-pipeline` (least-privilege and secret allow-list static check), and possibly `issue-workflow` if the PROJECT_TOKEN approach changes.

## 8. Risks

- Documentation-only criteria (3) and environment-dependent criteria (1) cannot be verified end to end until #8; the acceptance review must accept contract-level evidence.
- The drift-guard and static tests are string/awk based (like `test-image-config.sh`) and can be brittle against workflow edits.
- A startup validator changes behavior for local runs and tests; it must not break `NoDatabaseTestConfig` (DataSource excluded) or Testcontainers `@ServiceConnection` contexts (validation must read from the resolved DataSource properties or be disabled in test slices).
- The Google Books key already shipped in APKs must be treated as leaked; revocation needs a new APK, and users on old builds lose the fallback (the app degrades to no Google Books lookup, per the `isBlank()` guard).
- Free-tier host and Neon constraints (decided in #8) may force different secret mechanisms (for example host dashboard variables rather than GitHub Environments); keep the inventory host-agnostic.
- `PROJECT_TOKEN` expiry is currently untracked: if the PAT expires, `board-sync` fails visibly but is not a required check, so a silent expiry degrades the board workflow without blocking anything.
- Live state (existing repo secrets, Environment settings, branch rulesets, key restrictions in Google Cloud) was not inspected and must be confirmed while writing the inventory.
- #26 (auth) will add JWT/signing secrets; the inventory and fail-fast validator should be extensible so #26 adds rows rather than reworking the mechanism.

## 9. Size estimate

Option B: medium. Roughly 8 to 12 files: `docs/secrets.md`, one backend config class plus its test, one or two static test scripts, edits to `backend/build.gradle.kts`, `scripts/test-image-smoke.sh`, README, and spec deltas. Estimated 300 to 500 changed lines, within the session's 800-line review budget; plan work-unit commits (static checks, backend validator, docs and inventory). No new infrastructure or dependencies.
