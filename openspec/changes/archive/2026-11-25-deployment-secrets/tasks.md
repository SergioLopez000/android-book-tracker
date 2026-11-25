# Tasks: deployment-secrets (#54)

Strict TDD is active: in every unit the failing test or harness (RED) comes before the implementation (GREEN), followed by triangulation and refactor. Shell checks follow the checker plus harness pattern (`scripts/check-commit-msg.sh` / `scripts/test-check-commit-msg.sh`, `scripts/test-image-config.sh`). Tasks flagged **[USER]** are manual steps performed by the user, not by the apply phase.

Spec refs: `BA` = backend-api, `CI` = ci-pipeline, `IW` = issue-workflow (requirement names). Design refs: `D1`..`D7` = Decisions 1 to 7.

## Review Workload Forecast

| Field | Value |
|---|---|
| Estimated changed lines | Unit 1 ~165; Unit 2 ~205; Unit 3 ~190; Unit 4 ~195; total ~755 |
| 800-line budget risk | Low in total; each unit is 150 to 250 lines |
| Chained PRs recommended | Optional: each unit is a candidate for its own PR |
| Delivery strategy | One commit per work unit, ordered 1 to 4 |

### Suggested Work Units

| Unit | Goal | Focused test command | Rollback boundary |
|---|---|---|---|
| 1 | Static contract checks, job permissions, lefthook job | `bash scripts/test-secrets-contract.sh` | Revert commit 1 (revert unit 3 drift guard with it) |
| 2 | Backend validator, smoke cases, jar scan, CI command | `cd backend && ./gradlew test verifyPackagedArtifact`; `bash scripts/test-image-smoke.sh` | Revert commit 2 |
| 3 | `docs/secrets.md`, drift guard, README, verify rule | `bash scripts/test-secrets-contract.sh` | Revert commit 3 |
| 4 | `PROJECT_TOKEN` expiry reminder, parser, harness | `bash scripts/test-token-expiry.sh`; `actionlint` | Revert commit 4 or disable the workflow in the Actions UI |

Commit rule for every unit: `lefthook` pre-commit and commit-msg hooks MUST run. `--no-verify` is forbidden. The lefthook summary of each commit is reported to the user as evidence.

---

## Unit 1: Static contract checks, job permissions, lefthook job (~165 lines)

Start: no secrets contract script. End: checker and harness green on the real repo, every workflow and job declares `permissions:`, lefthook job `secrets-contract` active. Files: `scripts/check-secrets-contract.sh`, `scripts/test-secrets-contract.sh`, `.github/workflows/{ci,board-sync,promote}.yml`, `lefthook.yml`.

### 1. RED: harness first

- [x] 1.1 RED: create `scripts/test-secrets-contract.sh` skeleton plus the allow-list fixtures (`mktemp -d`): a clean workflow passes; `secrets.NEW_API_KEY` fails with a `FAIL:` line naming workflow and secret; `secrets: inherit` fails; `secrets.GITHUB_TOKEN` fails (must be `github.token`). Files: `scripts/test-secrets-contract.sh`. Spec: CI Secrets Allow-List (Only allowed references, Unlisted secret). Expected: non-zero, checker missing.
- [x] 1.2 RED: add permissions fixtures: workflow without top-level `permissions:` fails naming the workflow; a job without its own `permissions:` fails naming workflow and job; `write-all` and `read-all` fail. Files: `scripts/test-secrets-contract.sh`. Spec: CI Workflow Permissions Declared (Missing top-level block, Job without permissions under a non-read-only default); D4 check 2.
- [x] 1.3 RED: add Dockerfile fixtures: no `ENV`/`ARG` passes; `ENV DB_PASSWORD=x` fails naming the instruction without printing the value; `ARG API_TOKEN` fails; a missing or non-digest-pinned single `FROM` fails. Files: `scripts/test-secrets-contract.sh`. Spec: CI No Secret-Like Dockerfile Variables (Clean Dockerfile, Secret-like ENV rejected, Secret-like ARG rejected; digest-pinned `FROM` stays enforced).
- [x] 1.4 RED: add a real-repo case (checker with default root must pass) and a secret-free `e2e-tests` fixture (no `secrets.*` inside that job body). Run `bash scripts/test-secrets-contract.sh`: expected non-zero with every case failing. Files: `scripts/test-secrets-contract.sh`. Spec: CI Secrets Allow-List (Secret-free job stays secret-free), Static Contract Checks Self-Tested (Self-test covers failures).

### 2. GREEN: checker and permissions

- [x] 2.1 GREEN: create `scripts/check-secrets-contract.sh [root]` (awk/grep only; root from `BASH_SOURCE` or argument; no git commands) with check 1 (`ALLOWED_SECRETS=(PROJECT_TOKEN)`, `secrets: inherit`, `github.token`) and the `e2e-tests` secret-free rule. Files: `scripts/check-secrets-contract.sh`. Spec: CI Secrets Allow-List. Allow-list fixtures from 1.1 and 1.4 now pass.
- [x] 2.2 GREEN: add check 2 (top-level `^permissions:`, four-space job `permissions:`, no `write-all`/`read-all`) and check 3 (Dockerfile `ENV`/`ARG` pattern `(?i)(pass|secret|token|key|credential|datasource)` plus single digest-pinned `FROM`). Output names the instruction, never its value. Files: `scripts/check-secrets-contract.sh`. Spec: CI Workflow Permissions Declared, No Secret-Like Dockerfile Variables.
- [x] 2.3 GREEN: add job-level `permissions: contents: read` to `unit-tests`, `backend-tests`, `contract-tests`, `secret-scan`, `ci-gate` (and any other job lacking a block, keeping existing write blocks) in `ci.yml`; add `contents: read` to `board-sync.yml` job `sync`. Files: `.github/workflows/ci.yml`, `.github/workflows/board-sync.yml`. Spec: CI Workflow Permissions Declared (All workflows declare permissions); CI Workflow Triggers and Runner (Least privilege).
- [x] 2.4 GREEN: `promote.yml`: workflow level to `contents: read`; job `promote` gets `contents: write` and `checks: read` (keep any `packages: write` the retag job already has). Files: `.github/workflows/promote.yml`. Spec: CI Workflow Triggers and Runner (Least privilege). Real-repo case from 1.4 passes.
- [x] 2.5 TRIANGULATE: mutate each real file in turn (remove a top-level block, remove a job block, add `secrets.X`, add `ENV TOKEN_X=1`); the checker must fail naming the check; revert. Files: none committed. Spec: CI Static Contract Checks Self-Tested (Self-test covers failures).
- [x] 2.6 GREEN: run `actionlint` and `shellcheck` on the new scripts and edited workflows; fix findings. Files: scripts and workflows above.

### 3. Lefthook job and docs of the local-only rule

- [x] 3.1 RED: with no job yet, stage a violating workflow change (add `secrets.X`) and run `lefthook run pre-commit`: expected exit 0 (gate missing). Record in the apply evidence. Spec: CI Static Contract Checks (Runs in local hooks).
- [x] 3.2 GREEN: add pre-commit job `secrets-contract` to `lefthook.yml`, glob `{.github/workflows/*.{yml,yaml},backend/Dockerfile,docs/secrets.md,scripts/*secrets-contract*.sh}`, command `bash scripts/test-secrets-contract.sh`, same style as the `actionlint` job. Re-run 3.1: expected non-zero. Files: `lefthook.yml`. Spec: CI Static Contract Checks (Runs in local hooks, Not a CI job).
- [x] 3.3 TRIANGULATE: a clean staged workflow exits 0; only a staged `.md` outside the glob skips the job; confirm `ci.yml` has no step invoking `check-secrets-contract.sh` or `test-secrets-contract.sh`. Files: none. Spec: CI Static Contract Checks (Not a CI job).
- [x] 3.4 Docs: add `scripts/check-secrets-contract.sh` and `scripts/test-secrets-contract.sh` to the README script table and the `openspec/config.yaml` verify rule (local-only, same wording as `test-image-config.sh`). Files: `README.md`, `openspec/config.yaml`. Spec: CI Static Contract Checks (Self-tested and Run Locally).
- [x] 3.5 Commit unit 1, for example `ci(secrets): add static contract checks, job permissions and lefthook job`. Commit with a conventional commit message. The lefthook pre-commit and commit-msg hooks MUST run (never `--no-verify`). Report the lefthook summary to the user as evidence.

---

## Unit 2: Backend validator, smoke cases, jar scan, CI command (~205 lines)

Start: no validator. End: unit and context tests green, `verifyPackagedArtifact` scans credentials, smoke cases pass locally, CI `backend-tests` runs `verifyPackagedArtifact`. Files: `backend/src/main/kotlin/com/librosleidos/backend/config/DataSourceCredentialsValidator.kt`, `backend/src/test/kotlin/com/librosleidos/backend/config/DataSourceCredentialsValidatorTest.kt`, `backend/build.gradle.kts`, `scripts/test-image-smoke.sh`, `.github/workflows/ci.yml`.

### 4. Validator (RED then GREEN)

- [x] 4.1 RED: `DataSourceCredentialsValidatorTest.kt` unit cases for `missingVariables` with a fake `JdbcConnectionDetails`: each of URL, username, password null, empty and whitespace-only is reported by name; a throwing `getJdbcUrl()` counts as missing; all present returns empty; order is URL, USERNAME, PASSWORD; several missing names all appear. Run `./gradlew test --tests '*DataSourceCredentialsValidatorTest'`: expected compile error (unresolved class). Spec: BA Database Credentials From Environment Only (Missing setting, Blank setting, Several settings missing, All credentials present).
- [x] 4.2 RED: add a non-details bean pass-through case and a message case: the exception message contains `Missing required database configuration`, the variable names and the `docs/secrets.md` hint, and never a value (use real-looking values for the other settings). Spec: BA (Values are never echoed).
- [x] 4.3 RED: add the `ApplicationContextRunner` cases with `DataSourceAutoConfiguration` plus the validator: no properties fails with our message naming all three; blank password fails; full properties start (Hikari is lazy). Confirm the Spring Boot 4 package of `JdbcConnectionDetails` here. Spec: BA (Several settings missing, Missing setting, All credentials present); design Open Question 1.
- [x] 4.4 GREEN: create `DataSourceCredentialsValidator.kt` (`@Component` `BeanPostProcessor`, acts only on `JdbcConnectionDetails`, `MissingDatabaseConfigurationException : IllegalStateException`, `internal fun missingVariables`). Tests 4.1 to 4.3 pass. Spec: BA Database Credentials From Environment Only; D1.
- [x] 4.5 TRIANGULATE (regression): run the unmodified unit suite (`NoDatabaseTestConfig`), `integrationTest` and `contractTest` (`@ServiceConnection`); all stay green with no new property or bypass. Spec: BA (Test slices unaffected).
- [x] 4.6 REFACTOR: tidy naming and message constant; `./gradlew spotlessCheck detekt` (or the repo's lint tasks) clean.

### 5. Jar scan (RED then GREEN)

- [x] 5.1 RED: add the planned violating line to `backend/src/main/resources/application.yaml` locally (a literal `password: x`), then run `./gradlew verifyPackagedArtifact`: expected pass today (no scan). Record as the RED baseline. Files: temporary only. Spec: BA Packaged Artifact Free of Credentials.
- [x] 5.2 GREEN: extend `verifyPackagedArtifact` in `backend/build.gradle.kts` with the credential-key regex, the `jdbc:` embedded-credentials regexes and the datasource literal rule, scoped to text entries under `BOOT-INF/classes/` (`.yaml`, `.yml`, `.properties`, `.json`, `.xml`, `.sql`), never `BOOT-INF/lib/`. Failure output names the entry and never the matched value. Re-run 5.1: expected failure naming the entry. Files: `backend/build.gradle.kts`. Spec: BA (Credential-like pattern rejected, Embedded JDBC password rejected, Wired into check); D3.
- [x] 5.3 TRIANGULATE: plant (a) `url: jdbc:postgresql://u:secretpw@host/db`, (b) `jdbc:postgresql://host/db?password=secretpw`, (c) `api-key: abc` in turn; each fails naming the entry and does not print `secretpw`/`abc`. Then revert and confirm the clean jar passes (placeholders `${NAME}` with no default are exempt). Spec: BA (Clean jar passes, Embedded JDBC password rejected, Credential-like pattern rejected).
- [x] 5.4 **[USER-VISIBLE EVIDENCE]** Record proof that the jar-scan regexes fail on a planted credential: paste the failing task output from 5.1/5.3 and the passing clean run into the apply evidence (the build script has no harness). Spec: BA Packaged Artifact Free of Credentials; design RED evidence note.
- [x] 5.5 GREEN: add `verifyPackagedArtifact` to the `backend-tests` job command in `ci.yml` (alongside `test integrationTest`). Run `actionlint`. Files: `.github/workflows/ci.yml`. Spec: BA (Wired into check); CI Static Contract Checks (note on `verifyPackagedArtifact` in `backend-tests`); D3.

### 6. Smoke cases (RED then GREEN)

- [x] 6.1 RED: add the "fails without credentials" cases to `scripts/test-image-smoke.sh` before the database starts: (a) no `SPRING_DATASOURCE_*`, (b) URL and username set to an unreachable host and `SPRING_DATASOURCE_PASSWORD=` blank. Poll `docker inspect -f '{{.State.Status}}'` until `exited` (max 60 s) and assert `ExitCode != 0`, logs contain `Missing required database configuration` and the variable name, logs do not contain `smoke-dummy`. Run against an image built before 4.4: expected failure. Files: `scripts/test-image-smoke.sh`. Spec: BA Hardened Container Image (Image fails without credentials).
- [x] 6.2 GREEN: build the image with the validator and run `bash scripts/test-image-smoke.sh`: both new cases and the existing "Image starts" case pass. Run `shellcheck`. Files: `scripts/test-image-smoke.sh`. Spec: BA (Image fails without credentials, Image starts).
- [x] 6.3 Verify no secret-like `ENV`/`ARG` was added and `bash scripts/test-secrets-contract.sh` still passes.
- [x] 6.4 Commit unit 2, for example `feat(backend): validate database credentials at startup and scan jar for credentials`. Commit with a conventional commit message. The lefthook pre-commit and commit-msg hooks MUST run (never `--no-verify`). Report the lefthook summary to the user as evidence.

---

## Unit 3: `docs/secrets.md`, drift guard, README, verify rule (~190 lines)

Start: unit 1 merged (checker exists). End: inventory document, drift guard with self-test, Spanish README section, config verify rule. Files: `docs/secrets.md`, `scripts/check-secrets-contract.sh`, `scripts/test-secrets-contract.sh`, `README.md`, `openspec/config.yaml`.

### 7. Drift guard (RED then GREEN)

- [x] 7.1 RED: extend `scripts/test-secrets-contract.sh` with drift fixtures: inventory omitting `PROJECT_TOKEN` fails naming it; an `ALLOWED_SECRETS` entry absent from the inventory fails naming it; missing `## Inventory`, `## Environments`, `## Rotation cadence` or `## Runbook` heading fails; an active row without `` `SergioLopez000` `` fails; a complete fixture inventory passes; `GITHUB_TOKEN` is excluded. Run: expected failure (guard missing, and real `docs/secrets.md` absent). Files: `scripts/test-secrets-contract.sh`. Spec: CI Inventory Drift Guard (Inventory matches workflows, Undocumented secret, Guard has a self-test); D6.
- [x] 7.2 GREEN: add the drift guard (check 4) to `scripts/check-secrets-contract.sh`: extract backticked names from the first column of the `## Inventory` table, compare against `secrets.*` names in workflows and `ALLOWED_SECRETS`, required headings, owner check. Fixture cases pass; the real-repo case fails until 8.x. Files: `scripts/check-secrets-contract.sh`. Spec: CI Inventory Drift Guard; D6.

### 8. Documentation (GREEN against the guard)

- [x] 8.1 GREEN: create `docs/secrets.md` (English) with purpose and rules, `## Inventory` table (`| Secret | Class | Owner | Scope | Location | Consumers | Rotation | Status |`). Active rows: `PROJECT_TOKEN` (scope `project`, repository secret, at most 90 days, expiry date recorded), `GITHUB_TOKEN` (automatic), `GOOGLE_BOOKS_API_KEY` (local `local.properties`, shipped in the APK, treated as leaked). Planned rows `planned: created by #8` or `#29`/`#26`: `SPRING_DATASOURCE_URL`/`USERNAME`/`PASSWORD`, Neon/host credentials, Android release keystore, server-side Google Books key, JWT signing secret. Owner always `` `SergioLopez000` ``. No secret values. Files: `docs/secrets.md`. Spec: CI Secret Inventory Document (Row fields complete, Planned rows marked); IW Admin Setup Steps (Token recorded in the inventory).
- [x] 8.2 GREEN: add `## Environments` (repo-scoped secrets only for CI automation; `dev` created by #8 and `staging` with required reviewers; host-agnostic fallback), `## Rotation cadence` (classic PAT at most 90 days, DB passwords 180 days or on leak, provider API keys 365 days or on leak, signing secrets 180 days, keystore never rotated with a compromise procedure, `GITHUB_TOKEN` automatic), and a checklist for adding a secret. Files: `docs/secrets.md`. Spec: CI Secret Inventory Document (Runbook and cadence present).
- [x] 8.3 GREEN: add `## Runbook`: on-leak steps (create new, set in each environment store, restart or redeploy, revoke old, verify, record the date); `PROJECT_TOKEN` rotation (updates the expiry row, closes the reminder issue); Google Books section gated on #29 with the manual restriction steps (Google Cloud Console, APIs & Services, Credentials, API restriction to Books API only, Android app restriction with `applicationId` from `app/build.gradle.kts` plus SHA-1 fingerprints) and the note that revoking needs a new APK. Files: `docs/secrets.md`. Spec: CI (Google Books key gated); IW (Rotation updates the record).
- [x] 8.4 GREEN: run `bash scripts/test-secrets-contract.sh`: fixtures and the real-repo case pass. Confirm `PROJECT_TOKEN` is the only `secrets.*` name and that it appears in the inventory. Spec: CI Inventory Drift Guard (Inventory matches workflows).
- [x] 8.5 TRIANGULATE: delete the `PROJECT_TOKEN` row locally and a heading in turn; the real-repo run fails naming `PROJECT_TOKEN` and the heading; revert. Spec: CI Inventory Drift Guard (Undocumented secret).

### 9. README and verify rule

- [x] 9.1 GREEN: add the Spanish README section "Secretos y rotación": where secrets live, a link to `docs/secrets.md`, the rotation entry point, a pointer to the expiry reminder (unit 4), and the rotation cadence summary. Files: `README.md`. Spec: CI Secrets Section in README (Section and link).
- [x] 9.2 GREEN: update the `openspec/config.yaml` verify rule to mention the drift guard and `docs/secrets.md` (local-only). Files: `openspec/config.yaml`. Spec: CI Static Contract Checks (Self-Tested and Run Locally).
- [x] 9.3 Run `lefthook run pre-commit` with the unit staged: `secrets-contract` job runs and passes (glob matches `docs/secrets.md`).
- [x] 9.4 **[USER]** N/A (2026-11-25): no Google Books key exists, so there is nothing to restrict; inventory row set to "not provisioned" and runbook corrected (Android-app restriction would block the direct REST calls). Original task: Google Books key restriction: the user performs the manual restriction in Google Cloud Console (API restriction to Books API only, then Android application restriction with package name and SHA-1 fingerprints) following the runbook in `docs/secrets.md`. Apply only provides the steps. Spec: CI Secret Inventory Document (Google Books key gated).
- [x] 9.5 Commit unit 3, for example `docs(secrets): add inventory, runbook and drift guard`. Commit with a conventional commit message. The lefthook pre-commit and commit-msg hooks MUST run (never `--no-verify`). Report the lefthook summary to the user as evidence.

---

## Unit 4: `PROJECT_TOKEN` expiry reminder (~195 lines)

Start: no reminder. End: parser script and harness green, workflow validated by `actionlint`, contract script passes. Files: `scripts/token-expiry.sh`, `scripts/test-token-expiry.sh`, `.github/workflows/token-expiry.yml`, `openspec/config.yaml`, `README.md`.

### 10. Parser (RED then GREEN)

- [x] 10.1 RED: create `scripts/test-token-expiry.sh` with state cases using `NOW_EPOCH`: expiry in 14+ days gives `state=ok`; fewer than 14 days gives `expiring` with `days`; already past gives `expired`; HTTP 401 status line gives `invalid`; no header gives `missing-header`; garbage date gives `unparseable` with the raw value echoed. Run: expected failure (script missing). Files: `scripts/test-token-expiry.sh`. Spec: IW PROJECT_TOKEN Expiry Reminder Workflow (Plenty of time left, Expiry is near, Already expired); IW Expiry Header Parsing (Header missing, Header unparseable).
- [x] 10.2 RED: add robustness cases: CRLF line endings; header name in `GITHUB-AUTHENTICATION-TOKEN-EXPIRATION` and lowercase forms; a value like `$(touch x) "q" ; rm` is echoed literally, yields `unparseable` and executes nothing (assert the marker file is not created); output never contains a token fixture passed on stdin; usage error exits non-zero. Files: `scripts/test-token-expiry.sh`. Spec: IW Expiry Header Parsing (Header present and valid in any letter case, Value never reaches a shell unvalidated); design Threat Matrix (PR/issue commands, Token handling).
- [x] 10.3 GREEN: create `scripts/token-expiry.sh`: read headers from stdin, strip CR, match header name case-insensitively, validate the value against a strict date shape before calling `date -u -d`, honor `NOW_EPOCH`, print `state=`, `days=`, `expires=`; exit 0 for every parsed state. No `eval`, no interpolation of the raw value. Files: `scripts/token-expiry.sh`. Spec: IW Expiry Header Parsing Is Defensive; D7.
- [x] 10.4 TRIANGULATE: boundary cases at exactly 14 days (ok) and 13 days (expiring), empty header value (`unparseable`), duplicate header lines; run `shellcheck`. Spec: IW (Plenty of time left, Expiry is near).

### 11. Workflow (RED then GREEN)

- [x] 11.1 RED: add to `scripts/test-secrets-contract.sh` (or run it as-is) a check that a `token-expiry.yml` without job permissions fails; confirm `bash scripts/test-secrets-contract.sh` and `actionlint` report the new file's missing blocks before writing it fully (start from a draft with no `permissions:`). Spec: CI Workflow Permissions Declared.
- [x] 11.2 GREEN: create `.github/workflows/token-expiry.yml`: triggers `schedule` (`17 6 * * 1`) and `workflow_dispatch` only; concurrency `token-expiry` with `cancel-in-progress: false`; workflow permissions `contents: read`; actions pinned to a major (`actions/checkout@v7`). Job `read-expiry` (`contents: read`, `secrets.PROJECT_TOKEN`): sparse checkout of `scripts` with `persist-credentials: false`, `curl -s -D - -o /dev/null --max-time 20 -K -` with the Authorization header on stdin config (never argv), pipe to `scripts/token-expiry.sh`, outputs `state`, `days`, `expires`; empty token emits a notice and exits 0. Files: `.github/workflows/token-expiry.yml`. Spec: IW Reminder Workflow (Triggers, Missing secret, Least privilege); D7.
- [x] 11.3 GREEN: job `notify` (`issues: write` only, no checkout, `github.token`, `if: needs.read-expiry.outputs.state != 'ok'`): `gh label create secret-rotation --force`; find the open issue by label and exact title with `gh issue list --json` plus `jq`; `gh issue edit` or `gh issue create`, always `--repo "$GITHUB_REPOSITORY"` and `--body-file`; never close issues or comment. Use distinct fixed titles and bodies for the expiry reminder (`expiring`, `expired`, `invalid`) and the warning (`missing-header`, `unparseable`); the body links to the runbook in `docs/secrets.md`, and for the warning asks for a manual check and the first manual dispatch. Files: `.github/workflows/token-expiry.yml`. Spec: IW (Expiry is near, No duplicate issues, Already expired, No closing or commenting; Warning issue is not duplicated); see risk note on distinct titles.
- [x] 11.4 TRIANGULATE: run `actionlint`; `bash scripts/test-secrets-contract.sh` passes (permissions declared, only `PROJECT_TOKEN` secret, `PROJECT_TOKEN` and the write token never share a job); verify no `pull_request`/`pull_request_target` trigger and that `ci-gate` does not depend on it. Spec: IW (Triggers, Least privilege); CI Out of Scope Boundaries (Only expected jobs).
- [x] 11.5 Docs: README (Spanish) mention of the reminder under "Secretos y rotación", `openspec/config.yaml` verify rule entry for `test-token-expiry.sh`. Files: `README.md`, `openspec/config.yaml`. Spec: CI Secrets Section in README.
- [x] 11.6 Commit unit 4, for example `ci(secrets): add PROJECT_TOKEN expiry reminder workflow`. Commit with a conventional commit message. The lefthook pre-commit and commit-msg hooks MUST run (never `--no-verify`). Report the lefthook summary to the user as evidence.

---

## Post-merge verification (user steps)

- [x] V.1 (2026-11-25, run 45597007631 on main: header found, parsed `state=ok`, `days=82`, `expires=2027-02-15 12:52:01 UTC`; default branch is `main`) **[USER]** First manual `workflow_dispatch` of `token-expiry.yml` (after it reaches the default branch) to confirm the real `github-authentication-token-expiration` header format; the run output must show whether the header was found. If the format differs, adjust the parser through a new RED fixture first. Confirm the repository default branch for the `schedule` trigger. Spec: IW (Manual first run); design Open Questions 2 and 3.
- [x] V.2 (2026-11-25, run 45595404774: image check, fast-forward main and retag to staging all passed with the narrowed permissions) **[USER]** Run a real promote (`promote.yml`) after the narrowed permissions merge, to confirm the workflow works with `contents: read` at workflow level and `contents: write`/`checks: read` on the job. If it fails, report the missing scope before changing anything. Spec: CI Workflow Triggers and Runner (Least privilege).
- [x] V.3 **[USER]** N/A (2026-11-25), see 9.4. Original: Google Books key restriction in Google Cloud Console (see task 9.4). Rotation remains gated on #29.
- [x] V.4 (recorded in apply-progress.md, unit 2, tasks 5.2 to 5.4) Jar-scan regex proof recorded (task 5.4): failing planted-credential run attached to the apply evidence.
- [x] V.5 N/A (2026-11-25): the first run returned `ok`, so no reminder issue exists to re-dispatch against; duplicate handling is exercised when the token is within 14 days of expiry. Original: Run a second manual dispatch with the reminder issue already open (if one exists) to confirm that the same issue is edited and no duplicate is created. Spec: IW (No duplicate issues).

---

## Remediation (verify 2026-11-25)

Scope approved by the owner after the final verify found 8 PARTIAL scenarios (the `notify` job never ran; spec text stale).

- [x] R.1 RED: create `scripts/test-token-expiry-notify.sh` (fake `gh` and `curl` on PATH, real `jq`): create-vs-edit without duplicates, distinct warning title, ok/skipped gating, never close or comment, always `--repo` and `--body-file`, label `--force`, hostile `EXPIRES`, empty-token skip and token off argv. Run before the scripts exist: expected failure (exit 127). Spec: IW (Expiry is near, No duplicate issues, Already expired, Missing secret, Header missing, Header unparseable, Warning issue is not duplicated).
- [x] R.2 GREEN: extract the notify logic into `scripts/token-expiry-notify.sh` and the read-expiry step into `scripts/token-expiry-read.sh`; wire `.github/workflows/token-expiry.yml` (notify gets a sparse checkout of `scripts` with `persist-credentials: false`, hence `contents: read` next to `issues: write`; still no `PROJECT_TOKEN`). `actionlint`, `test-secrets-contract.sh` and `check-secrets-contract.sh` pass.
- [x] R.3 TRIANGULATE: mutate the script (drop `--repo`, add `issue close`, break the dedupe lookup, drop label `--force`, drop `--body-file`, warning shares the reminder title, ungated state; token on curl argv in the reader): each fails a named case; reverted.
- [x] R.4 Spec text: delta scenario "Google Books key gated" now states no client key exists, superseded by `GOOGLE_BOOKS_SERVER_API_KEY` (#29), and the Android-app restriction warning; inventory row `PROJECT_TOKEN` consumers list `board-sync.yml` and `token-expiry.yml`.
- [x] R.5 Docs and hooks: README script table (Spanish), `openspec/config.yaml` verify rule, new lefthook job `token-expiry-notify`.
- [x] R.6 Commit with a conventional message; lefthook hooks run (never `--no-verify`).
- [x] R.7 Assert the reminder body for "Expiry is near" and "Already expired": expiry date, runbook link `<server>/<repo>/blob/<ref>/docs/secrets.md#runbook` (expiring, expired, invalid and the edited body) in `scripts/test-token-expiry-notify.sh`. Proven load-bearing by two body mutations.
- [x] R.8 Assert "Missing secret" end to end in the reader harness: exact `::notice::PROJECT_TOKEN is not set; skipping the expiry check.` line, exit 0, single `state=skipped`, curl and gh never called (no issue opened). Proven by notice change/removal, wrong state and exit 1 mutations.
- [x] R.9 "Google Books key gated": `scripts/check-secrets-contract.sh` requires the `GOOGLE_BOOKS_API_KEY` row to state not provisioned and superseded by `GOOGLE_BOOKS_SERVER_API_KEY` (#29), and the runbook to keep the Android-app restriction warning; three fixtures in `scripts/test-secrets-contract.sh` (old wording, missing row, missing warning) RED against the old checker.
- [x] R.10 Commit `test(secrets): assert reminder runbook link, missing-secret notice and Google Books row`; lefthook hooks run (never `--no-verify`).

---

## Follow-ups (not part of this change)

- GitHub App migration for `PROJECT_TOKEN`: a follow-up issue is to be created. It needs the user's explicit approval before any GitHub change; apply MUST NOT create it on its own.
- `FailureAnalyzer` for a formatted startup banner: deferred (D1, the exception message is already explicit).

---

## Dependencies and order

- Sequential units: 1 -> 2 -> 3 -> 4. Unit 3 depends on unit 1 (checker and harness). Unit 2 is independent of units 1 and 3 and could run in parallel in a separate worktree, but a single writer is the default. Unit 4 depends on unit 1 for the contract check and on unit 3 for the README section and runbook link.
- Inside each unit: RED tasks first, then GREEN, then TRIANGULATE/REFACTOR, then the commit task.
- Reverting unit 1 requires reverting the drift guard from unit 3 as well.
- Total estimate: about 755 lines (budget 800).
