# Tasks: Security and Quality Gates

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~380-450 authored (`baseline.xml` excluded; design estimate 365 + harness for the fail-closed case) |
| 400-line budget risk | Medium (within project budget of 800) |
| Chained PRs recommended | No |
| Suggested split | Single PR (work-unit commits) |
| Delivery strategy | single-pr |
| Chain strategy | pending |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: pending
400-line budget risk: Medium

Issue #4, branch `ci/4-security-quality-gates`. Project review budget is 800 lines (session preflight), so the single PR needs no `size:exception`. All paths stay inside `/Users/sergio.lopezm/Desktop/libros-leidos`.

### Orchestrator decisions (resolve design Open Questions)

1. `promote.yml` gets a comment-only fix: the ci-gate comment says "unit tests + secret scan".
2. Unreachable or invalid `before` on push to `dev`: `secret-scan-range.sh` FAILS CLOSED (non-zero, clear error), no head-only fallback. All-zeros `before` (new branch) still scans only the head commit. This supersedes the D8 row "unreachable `before` -> `-1 AFTER` + warning" and the Threat Matrix "Push state" row (task 1.6 updates design.md).
3. The detekt `--input` separator is decided by spike S1.

### Suggested Work Units (commits of the single PR, in order)

| Unit | Goal | Likely PR | Focused test command | Runtime harness | Rollback boundary |
|------|------|-----------|----------------------|-----------------|-------------------|
| 1 | RED commit: failing harness | PR 1 | `bash scripts/test-secret-scan-range.sh` (must FAIL) | same | `scripts/test-secret-scan-range.sh` |
| 2 | GREEN + REFACTOR: range script | PR 1 | `bash scripts/test-secret-scan-range.sh` + `shellcheck scripts/*.sh` | temp git repo cases inside the harness | `scripts/secret-scan-range.sh` |
| 3 | `ci.yml` secret-scan, ci-gate, report guard + `promote.yml` comment | PR 1 | `actionlint` | live probes (Phase 5) | `.github/workflows/ci.yml`, `.github/workflows/promote.yml` |
| 4 | CodeQL workflow | PR 1 | `actionlint` | S2 on this PR | `.github/workflows/codeql.yml` |
| 5 | Dependabot config | PR 1 | schema check of `dependabot.yml` | S5 after promotion | `.github/dependabot.yml` |
| 6 | detekt config + baseline + lefthook job | PR 1 | `lefthook validate`; `detekt` on `app/src` | S1 hook probes | `config/detekt/*`, `lefthook.yml` |
| 7 | Docs and openspec config | PR 1 | structural readback | N/A (docs only) | `README.md`, `openspec/config.yaml` |

## Phase 0: Spikes (before any implementation commit)

- [x] 0.1 S1 detekt (local, read-only): `brew install detekt` if absent. Pass: `detekt --version` = 1.23.8; run over `app/src` shows no parse/PSI error on any file; probe `--input` with two files to fix the separator (comma vs `:`); a new issue aborts, a baselined one passes; hook <= 10 s on 3 files; record baseline entry count. Fallback: <=3 non-core parse failures go to `--excludes`; more, or any domain/usecase failure, escalates to the user; baseline >300 entries means tune noisy rules first. Record outcome in `design.md` (separator decided here).
- [x] 0.2 S4 gitleaks history (local): `brew install gitleaks` if absent (need v8.30.1 parity), run `gitleaks git --redact --no-banner .`. Pass: 0 findings, no allowlist. Fallback: false positive gets a `.gitleaksignore` fingerprint (D13); a real secret STOPS apply and escalates (no rotation or rewrite here). Record outcome in `design.md`.
- [x] 0.3 S2 CodeQL (OUTWARD, user confirmation first): runs on this change's own PR to `dev` after task 3.1. Pass: Kotlin files scanned >=90% of `app/src/main/**/*.kt`, job <=20 min, SARIF uploaded. Fallback in order: drop `-Pkotlin.compiler.execution.strategy=in-process`; add `--rerun-tasks`; `build-mode: autobuild`; else keep CodeQL non-gating, skip A2, escalate. Result (2026-11-17): PASS. PR #42 run: `:app:compileDebugKotlin` executed, not FROM-CACHE (8 executed); job 1m51s; SARIF uploaded (76 rules). The %-of-files metric is not exposed (uploaded SARIF is stripped), so extraction was proven instead by probe PR #43: CodeQL raised `java/insecure-trustmanager` on a `.kt` file. No fallback needed.
- [x] 0.4 S3 ruleset gate (OUTWARD, user confirmation first; after the first green `dev` push analysis). Probes: (a) docs-only PR becomes mergeable once CodeQL finishes; (b) throwaway High-alert probe PR (intent extra to `ProcessBuilder`) is blocked by the rule, then CLOSED UNMERGED and its branch deleted; (c) first Dependabot PR to `dev` is analyzed and not blocked by pre-existing alerts. Fallback: (a) stuck "waiting" or no rule evaluation on non-default `dev` means remove the rule, keep CodeQL informational, ask the user; (c) fails means document a manual Dependabot path. Result: PASS. (b) Probe PR #43 used a trust-all `X509TrustManager` instead of `ProcessBuilder` (no taint source needed): High alert, PR `BLOCKED`, closed unmerged, branch deleted. (c) Dependabot PR #45 was analyzed after the rule and is `CLEAN`, so pre-existing alerts do not block. (a) This change's closure PR (docs-only) is checked for mergeability after CodeQL.
- [x] 0.5 S5 Dependabot (OUTWARD observation, after promotion to `main`): version PRs target `dev` with `build(deps):`/`ci(deps):`; `[plugins]` versions bumped; groups match; `@dependabot rebase` on a retargeted security PR rebases onto `dev`. Fallback: document manual plugin bump; use close and reopen (D11); adjust group patterns in a follow-up. Result: PASS. After promotion (run 44061146160), PRs #45-#49 target `dev` with `build(deps):` and groups `test-tooling`, `kotlin-ksp`, `androidx-compose`. `version.ref` plugin versions are bumped (#46: kotlin, ksp). GitHub Actions had no updates. Not exercised: no security alert exists, so the retarget/rebase of a security PR is untested (README documents close and reopen).

## Phase 1: TDD range script (design D8; RED and GREEN are SEPARATE commits)

- [x] 1.1 RED: create `scripts/test-secret-scan-range.sh` (follow `scripts/test-kover-summary.sh` style) with a temp git repo helper and cases: PR -> `BASE..HEAD`; push -> `BEFORE..AFTER`; push with all-zeros `BEFORE` -> `-1 AFTER`; push with unreachable `BEFORE` (valid hex, not in clone) -> non-zero exit and stderr error, NOT `-1 AFTER`; malformed/short/uppercase SHA -> exit 2 (option-injection string such as `--output=x`); empty or unknown `EVENT_NAME` -> non-zero.
- [x] 1.2 Run the harness with no script present; confirm it FAILS. Capture the failing output for the TDD Cycle Evidence table.
- [x] 1.3 Commit RED alone (`test(ci): add failing harness for secret-scan-range`). Script MUST NOT be in this commit.
- [x] 1.4 GREEN: create `scripts/secret-scan-range.sh` reading `EVENT_NAME`, `BASE_SHA`, `HEAD_SHA`, `BEFORE_SHA`, `AFTER_SHA` from env; validate `^[0-9a-f]{40}$`; print the `--log-opts` value; fail closed on unreachable `BEFORE` via `git cat-file -e "$BEFORE^{commit}"`. Run the harness; confirm all pass. Capture the passing output.
- [x] 1.5 TRIANGULATE: add second cases for each branch (different SHAs, merge-commit PR head, zeros with 40 `0`s only; 39 zeros rejected). REFACTOR: tidy both scripts, `shellcheck` clean, re-run harness. Commit GREEN+REFACTOR (`ci: add secret-scan range script`).
- [x] 1.6 Update `design.md`: D8 range table and Threat Matrix "Push state" row now say unreachable `before` fails closed; mark the three Open Questions resolved.

## Phase 2: ci.yml (secret-scan, ci-gate, report guard)

- [x] 2.1 `.github/workflows/ci.yml`: update the header comment (secrets scanned in CI); add the `secret-scan` job exactly as in design (gitleaks 8.30.1 pinned by sha256, `fetch-depth: 0`, SHAs via `env:`, `--redact --no-banner --exit-code 1`).
- [x] 2.2 `ci-gate`: `needs: [unit-tests, secret-scan]`; rename the step "Require the gating jobs to succeed". Keep the `jq` loop unchanged.
- [x] 2.3 `report` "Build report site": add `ACTOR: ${{ github.actor }}` env and force `publish=false` when `$ACTOR = dependabot[bot]` (guards publish and comment steps).
- [x] 2.4 `actionlint` clean on `.github/workflows/ci.yml`; `shellcheck` on extracted run blocks via the hook.

## Phase 3: codeql.yml

- [x] 3.1 Create `.github/workflows/codeql.yml` per design (PR to `dev`, push `dev`, weekly cron, `build-mode: manual`, `:app:compileDebugKotlin` no daemon / no build cache, `security-events: write` on the job only). `actionlint` clean.

## Phase 4: dependabot.yml, detekt, docs, promote comment

- [x] 4.1 Create `.github/dependabot.yml` per design (gradle + github-actions, weekly, `target-branch: dev`, prefixes, groups with `test-tooling` first, limit 5); validate against the Dependabot schema.
- [x] 4.2 Create `config/detekt/detekt.yml` (`--build-upon-default-config` overrides only): ktlint-duplicate rules off and Compose tuning; confirm every rule id with `detekt --generate-config` (S1).
- [x] 4.3 Generate `config/detekt/baseline.xml` over `app/src` with the S1 separator (`detekt --create-baseline`); record the entry count; no CI baseline regeneration.
- [x] 4.4 `lefthook.yml`: add the `detekt` pre-commit job (glob `*.kt`, `{staged_files}`, `IFS` per S1, `brew install detekt` hint and baseline fix hint). `lefthook validate` OK.
- [x] 4.5 `README.md` (Spanish): "que corre donde" table, accepted-risk update (quality bypass accepted, secrets caught in CI), detekt (brew, commands, baseline regeneration) and SonarQube for IDE, local gitleaks/detekt version note, admin steps A1-A3 (ordered), retarget + reopen process for security PRs, rollback order.
- [x] 4.6 `openspec/config.yaml`: add `quality.static_analysis` (detekt) and the verify rule (`detekt` command).
- [x] 4.7 `.github/workflows/promote.yml`: comment-only change, the ci-gate comment says unit tests + secret scan. No functional diff (`git diff` shows comment lines only); `actionlint` clean.
- [x] 4.8 Conditional: `.gitleaksignore` only if S4 (0.2) found false positives. (N/A: S4 found 0 findings, no file created.)
- [x] 4.9 Local verify: `bash scripts/test-secret-scan-range.sh`, `bash scripts/test-kover-summary.sh`, `shellcheck scripts/*.sh`, `actionlint`, `lefthook validate`, `./gradlew :app:testDebugUnitTest`.

## Phase 5: Admin (OUTWARD, user confirmation required for each)

- [x] 5.1 A1: after the PR merges to `dev`, `PUT /repos/{o}/{r}/vulnerability-alerts`, then `PUT .../automated-security-fixes`, in that order. Verify via the repo API. Done 2026-11-17: `vulnerability-alerts` 204, `automated-security-fixes` enabled=true, paused=false. 0 open alerts.
- [x] 5.2 A2: only after the first green `dev` push CodeQL analysis and S3 (a): GET ruleset 27200640, append the `code_scanning` rule (CodeQL, `errors`, `high_or_higher`), PUT. Verify the rule via GET. Done after the first green `dev` analysis (CodeQL run 44056296882, 0 results). Rule verified via GET: CodeQL, `errors`, `high_or_higher`. S3 (a) was checked afterwards on the closure PR instead of before.
- [x] 5.3 A3: no admin action; promote to `main` (activates `dependabot.yml` and the CodeQL cron). Then run S5 (0.5). Done: promoted `16923d4` to `main` (run 44061146160). The dynamic `Dependabot Updates` workflow is active and the CodeQL workflow is active (cron from `main`).

## Phase 6: Live probes (OUTWARD, user confirmation first)

- [x] 6.1 Commit a fake secret with `--no-verify` on a throwaway branch/PR: `secret-scan` and `ci-gate` go red; log shows redacted output. Close unmerged, delete branch. Done: PR #44 had a generic fake key (`generic-api-key`) committed with `--no-verify`. Push protection did not block it (generic). `secret-scan` found 1 leak, `ci-gate` failed, PR `BLOCKED`, the value appeared 0 times in the log. Closed unmerged, branch deleted, 0 secret-scanning alerts.
- [x] 6.2 Stage a `.kt` file with a new detekt violation: commit aborts with the fix hint; baselined issue passes; docs-only commit skips detekt (local probe). Done: a real commit of a `.kt` file with an empty catch block was aborted (`EmptyCatchBlock` + fix hint, 1.8 s). Baselined-file pass and docs-only skip were verified in S1 (`lefthook run --job detekt`). The detekt hook also passed on a real commit in probe #43.
- [x] 6.3 First Dependabot PR to `dev`: `report` skips publish and comment; `ci-gate` still evaluates. Done: Dependabot PR #45. In `report`, Publish to gh-pages, Compose PR comment and Sticky PR comment were skipped; 0 sticky comments; `ci-gate` passed.
- [x] 6.4 CodeQL blocks a High alert (S3 b result) and a clean PR merges (S3 a). Done: High alert blocked on #43; clean Dependabot PR #45 is `CLEAN` (mergeable).

## Phase 7: Verify, archive, board (issue #4)

- [x] 7.1 (OUTWARD) On PR open: move issue #4 to "In review" on GitHub Project #2. Done: #4 moved to In review when PR #42 opened.
- [ ] 7.2 `sdd-verify` against both spec deltas, including Strict TDD evidence.
- [x] 7.3 (OUTWARD) After the PR merges to `dev`: move issue #4 to "In dev (QA)". Done: #4 moved to In dev (QA) after the #42 merge (`16923d4`).
- [ ] 7.4 `sdd-archive`: merge deltas into living specs `ci-pipeline` and `code-quality-gates`.
- [ ] 7.5 (OUTWARD) At Done: post the summary comment on issue #4, close it manually, move to "Done".
- [x] 7.6 Delete the local branch `ci/4-security-quality-gates` after merge. Done: the branch was auto-deleted on GitHub and deleted locally after the merge.
- [x] 7.7 In `apply-progress`, record the TDD Cycle Evidence table for `secret-scan-range.sh`: Done: recorded in Engram apply-progress (obs #109). RED `e48d66a` (harness only, fails 'range script not found'), GREEN `5f3d653` (9 cases), TRIANGULATE to 21 cases plus a head-only mutant caught by 2 cases, SAFETY NET `test-kover-summary.sh` green, REFACTOR shellcheck clean.

| Task | Test file | Layer | Safety Net | RED | GREEN | TRIANGULATE | REFACTOR |
|------|-----------|-------|------------|-----|-------|-------------|----------|
| 1.1-1.5 | `scripts/test-secret-scan-range.sh` | Unit (shell) | existing `test-kover-summary.sh` green | failing run + RED commit SHA | passing run + GREEN commit SHA | second case per branch | shellcheck-clean tidy |
