# Tasks: freeze-backend (#91)

Strict TDD is active: in every unit the failing harness case or fixture (RED) comes before the implementation (GREEN), followed by triangulation and refactor. Shell checks follow the checker plus harness pattern (`scripts/check-secrets-contract.sh` / `scripts/test-secrets-contract.sh`). Tasks flagged **[USER]** are manual steps performed by the owner or the orchestrator after the merge, not by the apply phase. `backend/` and `app/` MUST stay byte-identical throughout.

Spec refs: `CI` = ci-pipeline delta (requirement names). Design refs: `D1`..`D7` = product decisions in `preproposal.md`; `F0`..`F4` = guard rules in `design.md`.

## Review Workload Forecast

| Field | Value |
|---|---|
| Estimated changed lines | Unit 1 ~560 (guard ~90, harness ~140, workflows ~-300, dependabot ~-40, `test-image-config.sh` deletion, lefthook ~+6); Unit 2 ~85 (mostly deletions); Unit 3 ~120; code total ~765; about 900-1000 including specs and SDD artifacts |
| 400-line budget risk | High: over 400 |
| 800-line budget risk | Medium: at or slightly over 800 including artifacts, about 60% deletions |
| Chained PRs recommended | No: D1 requires one revertible commit, so a single squash commit restores everything on `git revert` |
| Delivery strategy | exception-ok: ONE PR labeled `size:exception`, squash-merged into one commit |
| Decision needed before apply | No: `size:exception` was approved by the owner on 2026-11-26 (D7) |

### Suggested Work Units

The PR is one. The units below are reviewable commits inside it (work-unit-commits); they are squashed on merge, so none is a chained PR.

| Unit | Goal | Focused test command | Rollback boundary |
|---|---|---|---|
| 1 | Guard pair RED then GREEN, backend CI/CodeQL/promote/Dependabot removal, `test-image-config.sh` retired, lefthook `backend-frozen` job | `bash scripts/test-backend-frozen.sh`; `actionlint` | Revert commit 1 (workflows, Dependabot, guard pair, lefthook job, deleted harness) |
| 2 | Dockerfile checks and fixtures leave the secrets contract; lefthook glob narrowed | `bash scripts/test-secrets-contract.sh` | Revert commit 2 (secrets scripts and the glob) |
| 3 | Frozen docs: README (Spanish), `docs/secrets.md`, `openspec/config.yaml` | `bash scripts/test-secrets-contract.sh`; `grep -Eil 'frozen\|congelad' README.md docs/secrets.md openspec/config.yaml` | Revert commit 3 (docs only) |

Commit rule for every unit: `lefthook` pre-commit and commit-msg hooks MUST run. `--no-verify` is forbidden. The lefthook summary of each commit is reported as evidence. Conventional commits only, no AI attribution.

---

## Unit 1: Backend freeze guard, CI removals and Dependabot (~560 lines)

Start: no guard; workflows still build, publish and promote the backend. End: `bash scripts/test-backend-frozen.sh` green on fixtures and on the real repo, `actionlint` clean, no backend automation left. Files: `scripts/check-backend-frozen.sh`, `scripts/test-backend-frozen.sh`, `.github/workflows/{ci,codeql,promote}.yml`, `.github/dependabot.yml`, `scripts/test-image-config.sh` (delete), `lefthook.yml`.

### 1. RED: harness first

- [x] 1.1 RED: create `scripts/test-backend-frozen.sh` skeleton (`mktemp -d` fixture roots, style of `test-secrets-contract.sh`, cleanup trap) with the clean root (a `ci.yml` with a `ci-gate` job, one non-backend workflow, a `dependabot.yml` with gradle at the repository root and npm in the e2e directory) expected to pass, plus the positive control: the same root with a `# server module frozen (#91)` comment expected to pass. Run `bash scripts/test-backend-frozen.sh`: expected non-zero because the checker is missing. Files: `scripts/test-backend-frozen.sh`. Spec: CI Backend Frozen in CI (Backend and app bytes untouched is covered later in V.5; this is the clean-root control); D1.
- [x] 1.2 RED: add the F0 non-vacuity fixtures, each expected to fail with a `FAIL: frozen:` line naming the rule: root with an empty `.github/workflows/`, root with no `dependabot.yml`, root where `ci.yml` has no `ci-gate` job, and `dependabot.yml` without `package-ecosystem:`. Files: `scripts/test-backend-frozen.sh`. Spec: CI Backend Frozen in CI (guard cannot pass on an empty tree).
- [x] 1.3 RED: add the F1 to F3 fixtures: a root whose only violation is a `# backend` comment (fails F1, proves comments are scanned); `ci.yml` job named `backend-tests`, `contract-tests` or `publish-image` (F2); `promote.yml` with `needs: [check-image]` and a `retag-image` job (F2); a `docker/login-action` step, a `ghcr.io` reference, a `packages: write` permission and a `test-image-` reference (F3). Matching is case-insensitive: add one upper-case `BACKEND` fixture. Files: `scripts/test-backend-frozen.sh`. Spec: CI Backend Frozen in CI (No workflow touches the backend); CI Workflow Triggers and Runner (Least privilege: `packages: write` nowhere); D1.
- [x] 1.4 RED: add the F4 fixtures: a `directory:` set to the backend directory, a `directories:` list containing the backend directory, `package-ecosystem: docker` and `package-ecosystem: "docker"` (quoted); each fails. Files: `scripts/test-backend-frozen.sh`. Spec: CI Backend Frozen in CI (Dependabot has no backend entry); CI Dependabot Configuration (No backend or docker entry).
- [x] 1.5 RED: add the real-repository case (checker with default root must pass). Run `bash scripts/test-backend-frozen.sh`: expected non-zero with every case failing because the checker does not exist. Files: `scripts/test-backend-frozen.sh`. Spec: CI Backend Frozen in CI.

### 2. GREEN: the checker

- [x] 2.1 GREEN: create `scripts/check-backend-frozen.sh [root]` (awk/grep only, no git, no network; root from `BASH_SOURCE` or the argument) with F0 to F4 exactly as the design table: case-insensitive scan of the full workflow text including comments; F4 scoped to `dependabot.yml`; one `FAIL: frozen: <file> line <n> (<rule>)` per hit; non-zero on any failure. Run the harness: every fixture case passes; the real-repo case still fails. Files: `scripts/check-backend-frozen.sh`. Spec: CI Backend Frozen in CI; design Interfaces / Contracts.
- [x] 2.2 RED (real repo): run `bash scripts/check-backend-frozen.sh` and record the full hit list as RED evidence (expected hits in `ci.yml`, `codeql.yml`, `promote.yml`, `dependabot.yml`). This is the failing real-repo case that units 1 section 3 turns GREEN. Files: none. Spec: CI Backend Frozen in CI.
- [x] 2.3 REFACTOR: run `shellcheck scripts/check-backend-frozen.sh scripts/test-backend-frozen.sh`; fix findings.

### 3. GREEN: workflow, Dependabot and harness edits

- [x] 3.1 GREEN: edit `.github/workflows/ci.yml` per the design: rewrite header (1-7) without the words `backend` or `contract-tests` (use "The server module is frozen (#91), see README."); delete `backend-tests` and `contract-tests` (58-143) and `publish-image` with its comment (436-474); `report.needs: [unit-tests, e2e-tests]`; delete "Download PIT report" (293-298); in "Build report site" change comment 328 to "Coverage figure for the Android app", delete 341, 343, 345-357, 363-364 and set line 358 to `coverage="app ${app_coverage}"`; in "Compose PR comment" delete 406-407; `ci-gate.needs: [unit-tests, secret-scan]`. Files: `.github/workflows/ci.yml`. Spec: CI Unit Test Job (Backend not run here); CI Artifact Contract (No backend artifacts); CI Report Generation and Publishing (Report needs only unit and e2e); CI Single Required Check (No removed job in needs); CI Out of Scope Boundaries (Only expected jobs); CI Workflow Triggers and Runner (Least privilege); CI E2E Test Job (Non-gating).
- [x] 3.2 GREEN: edit `.github/workflows/codeql.yml`: `java-version: "17"` (drop the comment and `21`), delete step "Build backend (JDK 21)" (49-53), header "CodeQL analysis for the Android app (Kotlin). The server module is frozen (#91) and not compiled, see README." plus the existing ruleset sentence. Remaining steps: checkout, setup-java 17, setup-gradle, init, Build app, analyze. Files: `.github/workflows/codeql.yml`. Spec: CI CodeQL Analysis Workflow (Backend not built, Minimal permissions, Push and schedule trigger, PR to dev is analyzed).
- [x] 3.3 GREEN: edit `.github/workflows/promote.yml`: delete `check-image` (26-60) and `retag-image` (119-155); `promote` drops `needs` and `outputs`; "Resolve commit" env becomes `INPUT_SHA: ${{ inputs.sha }}` with the existing `rev-parse --verify`, dev-ancestor and fast-forward checks and the `ci-gate` check untouched (empty input falls back to `origin/dev`); permissions stay `contents: write` and `checks: read`, no `packages:`; rewrite the header (1-7) as whole sentences per the design (no `backend`, no `ghcr.io`). Files: `.github/workflows/promote.yml`. Spec: CI REMOVED Promote Retags the Same Image Digest; CI Promotion Requires Green ci-gate (unchanged, must still hold); CI Workflow Triggers and Runner (Least privilege); design Threat Matrix (Push state).
- [x] 3.4 GREEN: edit `.github/dependabot.yml`: line 1 becomes "Version updates target dev."; delete both backend-directory entries (25-61). Gradle at the root, npm in e2e and github-actions stay. Files: `.github/dependabot.yml`. Spec: CI Dependabot Configuration (Config valid and scoped, Root Gradle covered, No backend or docker entry, npm e2e covered); D6 (no `ignore` entry added).
- [x] 3.5 GREEN: delete `scripts/test-image-config.sh`; `grep -rn 'test-image-config' . --exclude-dir=.git --exclude-dir=openspec --exclude-dir=node_modules` and remove every hit outside README (`lefthook.yml`, scripts). The README rows are removed in 5.1. `scripts/test-image-smoke.sh` and `scripts/pit-summary.sh` with its harness stay untouched and dormant. Files: `scripts/test-image-config.sh`, any other referrer. Spec: CI Backend Frozen in CI (Image-config harness retired); D3.
- [x] 3.6 RED then GREEN (lefthook): with the job missing, stage a workflow containing the word `backend` and run `lefthook run pre-commit`: expected exit 0 (gate missing), record it. Then add the pre-commit job `backend-frozen` to `lefthook.yml`, glob `{.github/workflows/*.{yml,yaml},.github/dependabot.yml,scripts/*backend-frozen*.sh}`, command `bash scripts/test-backend-frozen.sh`, same style as the `secrets-contract` job; re-run: expected non-zero. Not added to any workflow ("No Static Analysis in CI"). Files: `lefthook.yml`. Spec: CI Static Contract Checks Self-Tested and Run Locally (Not a CI job); CI No Static Analysis in CI (unchanged).
- [x] 3.7 TRIANGULATE: against the real files, plant in turn a `# backend` comment in `ci.yml`, a `needs: [check-image]` in `promote.yml`, `packages: write` in `codeql.yml`, a backend directory entry and a quoted `docker` ecosystem in `dependabot.yml`, and an empty `ci-gate`-less `ci.yml`; the checker fails each naming the rule; revert every mutation. Files: none committed. Spec: CI Backend Frozen in CI.
- [x] 3.8 GREEN: run and record: `bash scripts/test-backend-frozen.sh` (all fixtures and the real-repo case pass); `actionlint` exits 0 (no dangling `needs`); `bash scripts/test-e2e-ci-config.sh`; `bash scripts/test-pit-summary.sh`; `bash scripts/test-kover-summary.sh`; `bash scripts/test-secrets-contract.sh` and `bash scripts/check-secrets-contract.sh` still pass (Dockerfile section is untouched until unit 2); the spec grep returns nothing: `grep -rEn 'backend|contract-tests|publish-image|check-image|retag-image|ghcr\.io|docker/login-action|packages: *write' .github/workflows/`; `grep -En '/backend|package-ecosystem: *"?docker' .github/dependabot.yml` returns nothing; `grep -En 'backend|java-version: *"?21' .github/workflows/codeql.yml` returns nothing; `shellcheck` on the new scripts. If `test-e2e-ci-config.sh` fails, keep the `ci-gate` and `report` wiring it expects and fix its expectation only through a RED case first. Spec: CI Workflows stay valid; No workflow touches the backend; Report needs only unit and e2e; No removed job in needs.
- [x] 3.9 Commit unit 1, for example `ci: freeze the backend and add the freeze guard (#91)`. Conventional commit message; lefthook pre-commit and commit-msg MUST run; report the summary.

---

## Unit 2: Secrets contract without Dockerfile checks (~85 lines)

Start: `check-secrets-contract.sh` still requires and scans `backend/Dockerfile`. End: no Dockerfile check or fixture anywhere in the pair; the lefthook glob no longer lists it. Files: `scripts/check-secrets-contract.sh`, `scripts/test-secrets-contract.sh`, `lefthook.yml`.

### 4. RED then GREEN

- [x] 4.1 RED: in `scripts/test-secrets-contract.sh` change the clean fixture so it has no `backend/` directory and drop the `DIGEST` variable, the `backend` in `mkdir` and the Dockerfile `printf` (65) but keep the Dockerfile section (136-159) for now. Run `bash scripts/test-secrets-contract.sh`: expected failure, because the current checker fails a root without `backend/Dockerfile`. Record the failing output. Files: `scripts/test-secrets-contract.sh`. Spec: CI Static Contract Checks (No Dockerfile checks or fixtures); CI REMOVED No Secret-Like Dockerfile Variables; D2.
- [x] 4.2 GREEN: in `scripts/check-secrets-contract.sh` delete header item 3, the `dockerfile=` variable (19) and section 3 (121-143); renumber items 4 to 3 and 5 to 4 in the header and in comments. Files: `scripts/check-secrets-contract.sh`. Spec: CI Static Contract Checks (No Dockerfile checks or fixtures); D2.
- [x] 4.3 GREEN: delete the "--- Dockerfile ---" fixture block (136-159) from `scripts/test-secrets-contract.sh`. Run it: all remaining fixtures and the real-repo case pass. Files: `scripts/test-secrets-contract.sh`. Spec: CI Static Contract Checks (Self-test covers failures).
- [x] 4.4 GREEN: in `lefthook.yml` drop `backend/Dockerfile` from the `secrets-contract` glob; keep `.github/workflows/*.{yml,yaml}`, `docs/secrets.md`, `scripts/*secrets-contract*.sh`. Files: `lefthook.yml`. Spec: CI Static Contract Checks (Runs in local hooks).
- [x] 4.5 TRIANGULATE: `grep -in 'dockerfile' scripts/check-secrets-contract.sh scripts/test-secrets-contract.sh` returns nothing; `grep -n 'Dockerfile' lefthook.yml` returns nothing; a staged `backend/Dockerfile` edit alone does not trigger `secrets-contract` while a staged workflow still does; the other rules (allow-list, permissions, drift guard) still fail on their violating fixtures (mutate one rule and revert); `ci.yml` still has no step running either script. Files: none. Spec: CI Static Contract Checks (No Dockerfile checks or fixtures, Runs in local hooks, Not a CI job).
- [x] 4.6 REFACTOR and verify: `shellcheck` on both scripts, `bash scripts/test-secrets-contract.sh`, `bash scripts/check-secrets-contract.sh`.
- [x] 4.7 Commit unit 2, for example `ci(secrets): drop the frozen Dockerfile checks from the secrets contract (#91)`. lefthook hooks MUST run; report the summary.

---

## Unit 3: Frozen documentation (~120 lines)

Start: docs still describe backend CI, GHCR and image promotion. End: README (Spanish), `docs/secrets.md` and `openspec/config.yaml` mark the backend frozen and revertible; the drift guard still passes. Files: `README.md`, `docs/secrets.md`, `openspec/config.yaml`.

### 5. Docs (GREEN against the guards)

- [x] 5.1 GREEN: edit `README.md` (Spanish): replace 122-151 with "## 🧩 Backend (congelado)" stating the code is kept, nothing runs in CI, CodeQL, Dependabot or GHCR, the manual build and test commands, that `scripts/test-image-smoke.sh` is manual only, and the reactivation steps (`git revert` of the squash commit) with the gaps listed in the design Migration section (closed Dependabot PRs, no image for freeze-period commits, stale `:staging`, closed CodeQL alerts, expired caches); line 161: drop `backend/Dockerfile`, add `backend-frozen`; delete rows 203, 204, 207; redraw the diagram 214-222 without backend jobs; delete 226 and 228; update 229, 231, 232, 234; artifact row 300 becomes `(unit)`; delete 302; add the new harness to the Comandos table; remove every `test-image-config` reference. Keep the Dependabot section that says security PRs target `main` and must be retargeted to `dev`, and add one sentence that backend security-update PRs are closed by hand linking #91 (D6). Files: `README.md`. Spec: CI Frozen state documented; CI Dependabot Configuration (Retargeting documented); CI Backend Frozen in CI (Image-config harness retired); D3, D6.
- [x] 5.2 GREEN: edit `docs/secrets.md`: prefix the status of the `SPRING_DATASOURCE_*`, `NEON_HOST_CREDENTIALS`, `GOOGLE_BOOKS_SERVER_API_KEY` and `JWT_SIGNING_SECRET` rows with `frozen with the backend (#91); `. The `GOOGLE_BOOKS_API_KEY` row stays byte-identical. Run `bash scripts/test-secrets-contract.sh` and `bash scripts/check-secrets-contract.sh`: the drift guard and the pinned Google Books wording pass. Files: `docs/secrets.md`. Spec: CI Frozen state documented; CI Inventory Drift Guard and Secret Inventory Document (unchanged, must still hold).
- [x] 5.3 GREEN: edit `openspec/config.yaml`: context 10-15 states the backend is frozen since #91, revert to reactivate, no image publication; remove the `backend` testing project and set `contract` and `mutation` to `available: false` (frozen); in the verify rules delete line 56, drop "secret-like Dockerfile ENV/ARG" from line 57 and add `bash scripts/test-backend-frozen.sh`; remove any `test-image-config` reference. Files: `openspec/config.yaml`. Spec: CI Frozen state documented; CI Static Contract Checks.
- [x] 5.4 TRIANGULATE: `grep -Eil 'frozen|congelad' README.md docs/secrets.md openspec/config.yaml` lists all three files; `grep -rn 'test-image-config' . --exclude-dir=.git --exclude-dir=openspec --exclude-dir=node_modules` matches nothing; `bash scripts/test-backend-frozen.sh` and `bash scripts/test-secrets-contract.sh` still pass; staged docs run through `lefthook run pre-commit` cleanly. Files: none. Spec: CI Frozen state documented; CI Backend Frozen in CI (Image-config harness retired).
- [x] 5.5 Commit unit 3, for example `docs: mark the backend frozen in README, secrets inventory and config (#91)`. lefthook hooks MUST run; report the summary.

---

## Final verification (apply phase, before opening the PR)

- [x] V.1 `actionlint` exits 0 on the final tree.
- [x] V.2 `shellcheck scripts/check-backend-frozen.sh scripts/test-backend-frozen.sh scripts/check-secrets-contract.sh scripts/test-secrets-contract.sh` exits 0.
- [x] V.3 `bash scripts/test-backend-frozen.sh`, `bash scripts/test-secrets-contract.sh`, `bash scripts/check-secrets-contract.sh`, `bash scripts/test-e2e-ci-config.sh`, `bash scripts/test-pit-summary.sh` and `bash scripts/test-kover-summary.sh` all exit 0.
- [x] V.4 `grep -rEn 'backend|contract-tests|publish-image|check-image|retag-image|ghcr\.io|docker/login-action|packages: *write' .github/workflows/` returns nothing, and `grep -En '/backend|package-ecosystem: *"?docker' .github/dependabot.yml` returns nothing.
- [x] V.5 `git diff --stat origin/dev...HEAD -- backend/ app/` prints nothing (compare against `dev`, since `main` lags it).
- [x] V.6 `./gradlew :app:testDebugUnitTest` is still green.
- [x] V.7 `test -e scripts/test-image-config.sh` fails and the `test-image-config` grep (excluding `.git`, `openspec`, `node_modules`) matches nothing; `ci.yml` jobs are exactly `unit-tests`, `e2e-tests`, `secret-scan`, `report`, `ci-gate`.
- [x] V.8 Open the PR to `dev` with the `size:exception` label, body linking #91 and stating D7; do not use `--no-verify`.

## Post-merge steps ([USER] / orchestrator)

- [x] P.1 **[USER]** Verify CI on the PR: `ci-gate` green (it depends only on `unit-tests` and `secret-scan`), `report` published, no backend job present. Spec: CI Single Required Check (Everything green, Report outage).
- [x] P.2 **[USER]** After the merge, promote `dev` to `main` and staging with the new `promote.yml` (one `workflow_dispatch` on `dev`); the owner approves the `staging` environment. Confirm the job resolves the SHA without `check-image` or `retag-image` and fast-forwards `main`. Spec: CI Promotion Requires Green ci-gate; CI Workflow Triggers and Runner (Least privilege).
- [x] P.3 **[USER]** Close Dependabot PRs #66, #67 and #69 with a comment linking #91 (D5). Issue #84 stays open (D4).
- [x] P.4 **[USER]** Watch for backend security-update PRs (D6): close any by hand with a comment linking #91; do not add an `ignore` entry.
- [x] P.5 Archive phase: append the frozen sentence from the delta spec "Notes for archive" to the Purpose paragraph of `openspec/specs/backend-api/spec.md`; leave `code-quality-gates` unchanged.

---

## Traceability: delta spec to tasks

| Requirement and scenario | Tasks |
|---|---|
| ADDED Backend Frozen in CI / Backend and app bytes untouched | V.5 |
| ADDED / No workflow touches the backend | 1.3, 2.1, 3.1-3.3, 3.8, V.4 |
| ADDED / Workflows stay valid | 3.8, V.1 |
| ADDED / Dependabot has no backend entry | 1.4, 3.4, 3.8, V.4 |
| ADDED / Image-config harness retired | 3.5, 5.1, 5.4, V.7 |
| ADDED / Frozen state documented | 5.1, 5.2, 5.3, 5.4 |
| MODIFIED Workflow Triggers and Runner / PR triggers, Push to dev triggers | 3.1, 3.8 (triggers untouched, checked by `actionlint` and `test-e2e-ci-config.sh`) |
| MODIFIED Workflow Triggers and Runner / Least privilege | 1.3, 3.1-3.3, 3.7, V.4 |
| MODIFIED Unit Test Job / Tests run on every trigger, Test failure still uploads results | 3.1, 3.8, V.6 |
| MODIFIED Unit Test Job / Backend not run here | 3.1, 3.8, V.4 |
| MODIFIED Artifact Contract / Unit suite artifacts, E2E suite artifact, Unknown future suite is merged | 3.1, 3.8 (untouched logic, `test-e2e-ci-config.sh`) |
| MODIFIED Artifact Contract / No backend artifacts | 3.1, V.4 |
| MODIFIED Report Generation and Publishing / Report needs only unit and e2e | 3.1, 3.8, V.7 |
| MODIFIED Report Generation and Publishing / PR report, Dev report, Report on failing tests, Report waits for e2e, E2E results shown, Missing results, Fork PR, Concurrent publishes | 3.1, 3.8 (publishing logic untouched; `test-e2e-ci-config.sh`, `test-kover-summary.sh`) |
| MODIFIED Single Required Check / Everything green, Test failure, Secret scan failure, Report outage | 3.1, 3.8, P.1 |
| MODIFIED Single Required Check / No removed job in needs | 3.1, 3.8, V.1 |
| MODIFIED Out of Scope Boundaries / Only expected jobs | 3.1, 3.2, 3.3, V.7 |
| MODIFIED Out of Scope Boundaries / No forbidden tooling | 3.8 (no tooling added) |
| MODIFIED CodeQL Analysis Workflow / PR to dev is analyzed, Push and schedule trigger, Minimal permissions | 3.2, 3.8 |
| MODIFIED CodeQL Analysis Workflow / Backend not built | 3.2, 3.8 |
| MODIFIED CodeQL Analysis Workflow / Kotlin is actually extracted | 3.2 (Build app step kept); live check in P.1 |
| MODIFIED Dependabot Configuration / Config valid and scoped, Root Gradle covered, npm e2e covered | 3.4 |
| MODIFIED Dependabot Configuration / No backend or docker entry | 1.4, 3.4, V.4 |
| MODIFIED Dependabot Configuration / Security features enabled | D6; unchanged, no task (repository settings) |
| MODIFIED Dependabot Configuration / Retargeting documented | 5.1 |
| MODIFIED E2E Test Job / Runs on every trigger, Failure still uploads results, Read-only and secret-free, Fork PR is safe, Non-gating | 3.1, 3.8 (job untouched; `test-e2e-ci-config.sh`) |
| MODIFIED Static Contract Checks / Self-test covers failures | 4.1, 4.3, 4.6 |
| MODIFIED Static Contract Checks / No Dockerfile checks or fixtures | 4.1, 4.2, 4.3, 4.5 |
| MODIFIED Static Contract Checks / Runs in local hooks | 3.6, 4.4, 4.5 |
| MODIFIED Static Contract Checks / Not a CI job | 3.6, 4.5 |
| REMOVED Backend Test Job | 3.1, 3.7 |
| REMOVED Contract Test Job With PIT | 3.1, 3.7 |
| REMOVED GHCR Image Publication | 3.1, 1.3 (F2/F3 fixtures) |
| REMOVED Promote Retags the Same Image Digest | 3.3, 3.7, P.2 |
| REMOVED No Secret-Like Dockerfile Variables | 4.1, 4.2, 4.3 |
| Notes for archive (backend-api Purpose sentence) | P.5 |

## Dependencies and order

- Sequential units: 1 -> 2 -> 3, single writer. Unit 2 touches `lefthook.yml` and the secrets scripts only, so it is independent of unit 1 except for the shared `lefthook.yml` edit; keep the order to avoid conflicts. Unit 3 depends on units 1 and 2 only for the final wording of the README and config verify rules.
- Inside each unit: RED tasks first, then GREEN, then TRIANGULATE or REFACTOR, then the commit task.
- Bottleneck: unit 1 carries about 70% of the changed lines and cannot be split without breaking D1 (one revertible commit on squash). Reviewers should read it in the order 1 (guard), 3.1 to 3.4 (deletions).
- Total estimate: about 765 authored lines, 900-1000 with specs and artifacts; over the 800 budget by design, covered by the approved `size:exception` (D7).

## Completion evidence (2026-11-26)

- 3.9, 4.7 and 5.5: the three units were committed as one commit, `28b7671` (`ci: freeze the backend and disable its automation`). That commit is the single revertible change required by D1. The lefthook pre-commit and commit-msg hooks ran green: gitleaks, actionlint, shellcheck, backend-frozen, secrets-contract and conventional-commit.
- V.8: PR #92 into `dev`, labelled `type:chore`, `area:infra` and `size:exception` (D7). Its body links #91 and was squash-merged as `4979faf`. That squash commit, not the branch commit `28b7671`, is the one to revert to reactivate the backend.
- P.1: every check on PR #92 passed: `unit-tests`, `e2e-tests`, `secret-scan`, `report`, `ci-gate`, CodeQL `analyze (java-kotlin)` and `sync`. No backend job ran. The `dev` push run `45784298363` also passed.
- P.2: promote run `45784872454` succeeded after the owner approved `staging`. Its only job was "Fast-forward main", with no image check or retag. `main` is now `4979faf`, the same commit as `dev`, and the `staging` deployment points at `4979faf`.
- P.3: Dependabot PRs #66, #67 and #69 were closed, each with a comment linking #91 and #92. #84 stays open and deferred (D4).
- P.4: the README records the rule for closing backend security PRs by hand (D6). No such PR was open on 2026-11-26. This is an ongoing watch, not a one-off task.
- P.5: the frozen sentence was appended to the Purpose of `openspec/specs/backend-api/spec.md` on branch `docs/91-close-freeze-backend`. `code-quality-gates` is unchanged.
