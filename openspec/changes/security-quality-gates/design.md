# Design: Security and Quality Gates

## Technical Approach

This change refines archived D11 (`pr-pipeline-with-reports`): **quality stays local, security runs in CI.**

- **Local.** detekt joins the lefthook pre-commit hook as a brew CLI on staged `.kt` files.
- **CI.** Security moves into CI because it must not be bypassable:
  - `secret-scan` (pinned gitleaks over the event range) feeds `ci-gate`.
  - CodeQL runs in its own workflow and gates through the dev ruleset, not through `ci-gate`.
- **Dependabot.** Version updates target `dev`. Security updates are enabled (they target `main`).
- **Order.** Phase 0 spikes run first. Then there is one work unit per tool. Admin steps come last, after green runs.
- **Unchanged.** No app, Gradle or layering changes. `promote.yml` keeps working unchanged because it checks `ci-gate`.

## Architecture Decisions

| # | Decision | Chosen | Rejected | Rationale |
|---|---|---|---|---|
| D1 | Gate split (refines archived D11) | Quality (spotless, lint, detekt, actionlint, shellcheck, commit-msg) stays local-only. Security (gitleaks range, CodeQL) runs in CI | Keep everything local; move all gates to CI | `--no-verify` and web commits must not leak secrets or vulnerable code. Quality bypass stays an accepted risk (user decision). |
| D2 | detekt delivery | detekt 1.23.8 CLI from brew, run by the lefthook job on `{staged_files}` | Gradle plugin 1.x; 2.0 alpha (`dev.detekt`); pinned CLI jar plus script | The CLI is the documented pre-commit path. A Gradle task has no `--input` and couples the detekt compiler to AGP 9. The alphas match no exact toolchain. Kotlin 2.0.21 parsing risk → S1. |
| D3 | detekt config | `--build-upon-default-config` + `config/detekt/detekt.yml` (overrides only). Baseline `config/detekt/baseline.xml` generated over `app/src`. No `formatting` plugin. ktlint-duplicate rules off. Compose tuning | Full generated config; detekt-formatting | A minimal diff is reviewable. Spotless/ktlint already own style. Baseline IDs use the file name, so partial `--input` runs match. |
| D4 | CodeQL mode | Advanced workflow `codeql.yml`, `java-kotlin`, `build-mode: manual`, compiles `:app:compileDebugKotlin` with no daemon, no build cache and an in-process compiler | Default setup; `autobuild`; `none` | `none` skips Kotlin. Default setup cannot pin the build. A FROM-CACHE or daemon compile hides kotlinc from the tracer. Autobuild is the S2 fallback. |
| D5 | CodeQL triggers | `pull_request` to `dev` (no path filters), `push` to `dev`, weekly cron | Path filters; PR only | A missing analysis blocks merge, so every PR must produce one. `push: dev` gives the base-branch analysis the rule compares against. The cron fires only from `main` (default branch). |
| D6 | CodeQL gating | Ruleset 27200640 rule "Require code scanning results": CodeQL, Alerts=Errors, Security=High or higher. Added after the first green `dev` analysis | Add `analyze` to `ci-gate` `needs`; non-gating | Job success says nothing about alert severity. The ruleset evaluates alerts. Adding the rule early would block every PR. |
| D7 | gitleaks in CI | `secret-scan` job in `ci.yml`. v8.30.1 tarball pinned by sha256 (Allure pattern). `--redact --no-banner --exit-code 1` | `gitleaks-action`; separate workflow | Only a `ci.yml` job can feed `ci-gate`. The name stays `ci-gate`, so no ruleset or `promote.yml` change. No third-party action or license. |
| D8 | Range logic | `scripts/secret-scan-range.sh` prints the `--log-opts` value from env vars, with harness `scripts/test-secret-scan-range.sh` | Inline shell in `ci.yml` | Strict TDD plus precedent (`kover-summary.sh`). It has four branches plus SHA validation, so it is testable offline. |
| D9 | `ci-gate` logic | `needs: [unit-tests, secret-scan]`. The existing `jq` loop (every need must be `success`) is unchanged | Per-job `if` checks | The loop already treats skipped/cancelled as failure. Minimal diff. |
| D10 | Dependabot version updates | `.github/dependabot.yml`: `gradle` + `github-actions`, weekly, `target-branch: dev`, prefix `build`/`ci` + `include: scope`, groups, limit 5 | Renovate; no grouping | Free, native. Groups contain catalog churn. `build(deps):` passes `check-commit-msg.sh`. |
| D11 | Security updates | Alerts + security updates enabled (admin, confirmed). PRs target `main`. Process: retarget to `dev`, then close and reopen the PR (or `@dependabot rebase`, if S5 shows it rebases onto `dev`) | Leave disabled | Retargeting fires `edited`, which is not a default trigger. Without a new `synchronize`/`reopened` event, CodeQL never runs on `dev` and the rule blocks. |
| D12 | Dependabot report guard | In the `report` "Build report site" step, force `publish=false` when `github.actor == 'dependabot[bot]'`. This guards both publish and comment steps | `if:` on each step; raising `permissions` | One place to guard. `github.actor` survives human re-runs. Write tokens on bot dependency bumps are unnecessary. |
| D13 | gitleaks allowlist | None by default. If S4 finds false positives, use `.gitleaksignore` fingerprints; `.gitleaks.toml` (`[extend] useDefault = true`) only if a rule-level allowlist is needed | Pre-emptive allowlist | Narrowest exception that unblocks. A real secret stops apply and escalates. |
| D14 | Docs and config | README (Spanish): "qué corre dónde", accepted-risk update, SonarQube for IDE, detekt in brew/commands, admin steps, rollback. `openspec/config.yaml`: `quality.static_analysis` (detekt) + verify rule | — | Required by the specs `Documented Setup` and `Documentation of CI and Admin Steps`. |

## Data Flow

```
commit ─ lefthook pre-commit: spotless | lint | detekt(staged .kt) | gitleaks(staged) | actionlint | shellcheck
PR→dev / push dev:
  ci.yml:     unit-tests ─┬─> ci-gate (required check) ──> promote.yml
              secret-scan ┘
              unit-tests ──> report (non-gating; no publish for dependabot[bot])
  codeql.yml: analyze ──> code scanning alerts ──> ruleset "Require code scanning results"
main (after promote): dependabot.yml active; weekly CodeQL cron
```

## Job Shapes

**ci.yml diff.**
- Update the header comment: secrets are now scanned in CI.
- New job:

```yaml
  secret-scan:
    name: secret-scan
    runs-on: ubuntu-24.04
    env:
      GITLEAKS_VERSION: "8.30.1"
      GITLEAKS_SHA256: 551f6fc83ea457d62a0d98237cbad105af8d557003051f41f3e7ca7b3f2470eb
    steps:
      - uses: actions/checkout@v7
        with: { fetch-depth: 0 }
      - name: Install gitleaks
        run: |
          curl -fsSL -o gitleaks.tgz "https://github.com/gitleaks/gitleaks/releases/download/v${GITLEAKS_VERSION}/gitleaks_${GITLEAKS_VERSION}_linux_x64.tar.gz"
          echo "${GITLEAKS_SHA256}  gitleaks.tgz" | sha256sum -c -
          mkdir -p "$RUNNER_TEMP/gl" && tar -xzf gitleaks.tgz -C "$RUNNER_TEMP/gl" gitleaks
          echo "$RUNNER_TEMP/gl" >> "$GITHUB_PATH"
      - name: Scan commit range
        env:
          EVENT_NAME: ${{ github.event_name }}
          BASE_SHA: ${{ github.event.pull_request.base.sha }}
          HEAD_SHA: ${{ github.event.pull_request.head.sha }}
          BEFORE_SHA: ${{ github.event.before }}
          AFTER_SHA: ${{ github.sha }}
        run: gitleaks git --log-opts="$(scripts/secret-scan-range.sh)" --redact --no-banner --exit-code 1 .
```

**Range script contract.** The script prints the range to use:

| Event | Output |
|---|---|
| PR | `BASE..HEAD` |
| push | `BEFORE..AFTER` |
| push, `BEFORE` all zeros | `-1 AFTER` |
| push, `BEFORE` not a commit in the clone | `-1 AFTER`, plus a `::warning::` on stderr |

Any SHA that does not match `^[0-9a-f]{40}$` → exit 2 (fail closed).

**Other ci.yml changes.**
- `ci-gate`: set `needs: [unit-tests, secret-scan]` and rename the step to "Require the gating jobs to succeed".
- `report`: in "Build report site", add `ACTOR: ${{ github.actor }}` and `[ "$ACTOR" = "dependabot[bot]" ] && publish=false`.

**codeql.yml.**

```yaml
name: CodeQL
on:
  pull_request: { branches: [dev] }
  push: { branches: [dev] }
  schedule: [{ cron: "23 4 * * 1" }]
permissions: { contents: read }
concurrency:
  group: codeql-${{ github.event.pull_request.number || github.ref }}
  cancel-in-progress: ${{ github.event_name == 'pull_request' }}
jobs:
  analyze:
    name: analyze (java-kotlin)
    runs-on: ubuntu-24.04
    timeout-minutes: 45
    permissions: { contents: read, security-events: write }
    steps:
      - uses: actions/checkout@v7
      - uses: actions/setup-java@v6
        with: { distribution: temurin, java-version: "17" }
      - uses: gradle/actions/setup-gradle@v6
        with: { cache-read-only: true }
      - uses: github/codeql-action/init@v4
        with: { languages: java-kotlin, build-mode: manual }
      - run: ./gradlew --no-daemon --no-build-cache -Pkotlin.compiler.execution.strategy=in-process :app:compileDebugKotlin
      - uses: github/codeql-action/analyze@v4
        with: { category: "/language:java-kotlin" }
```

**dependabot.yml.** Group patterns are indicative; S5 confirms them. Dependabot uses the first matching group, so `test-tooling` comes first.

```yaml
version: 2
updates:
  - package-ecosystem: gradle
    directory: /
    target-branch: dev
    schedule: { interval: weekly, day: monday }
    open-pull-requests-limit: 5
    commit-message: { prefix: build, include: scope }
    groups:
      test-tooling: { patterns: ["junit:*", "org.robolectric*", "app.cash.turbine*", "androidx.test*"] }
      kotlin-ksp: { patterns: ["org.jetbrains.kotlin:*", "org.jetbrains.kotlin.*", "com.google.devtools.ksp*"] }
      kotlinx: { patterns: ["org.jetbrains.kotlinx*"] }
      androidx-compose: { patterns: ["androidx.*"] }
  - package-ecosystem: github-actions
    directory: /
    target-branch: dev
    schedule: { interval: weekly, day: monday }
    open-pull-requests-limit: 5
    commit-message: { prefix: ci, include: scope }
    groups:
      actions: { patterns: ["*"] }
```

**lefthook detekt job.** lefthook runs `sh`. `"$*"` joins the arguments with the first character of `IFS`, so S1 fixes the separator in one place:

```yaml
    - name: detekt
      glob: "*.kt"
      run: command -v detekt >/dev/null || { echo "brew install detekt"; exit 1; }; set -- {staged_files}; IFS=','; detekt --input "$*" --config config/detekt/detekt.yml --build-upon-default-config --baseline config/detekt/baseline.xml || { echo "Fix the detekt issues; accepted legacy issues: regenerate config/detekt/baseline.xml (README)"; exit 1; }
```

**detekt.yml key overrides.** Confirm every rule id against `detekt --generate-config` in S1.

| Purpose | Overrides |
|---|---|
| ktlint duplicates, set `active: false` | `style>MaxLineLength`, `NewLineAtEndOfFile`, `WildcardImport`, `UnusedImports`, `ModifierOrder`, `TrailingWhitespace`, `NoTabs`, `SpacingBetweenPackageAndImports` (if present in 1.23.8) |
| Compose tuning | `naming>FunctionNaming.ignoreAnnotated: [Composable]` |
| | `naming>TopLevelPropertyNaming.constantPattern: '[A-Z][A-Za-z0-9]*'` |
| | `complexity>LongParameterList` with `functionThreshold: 8` and `ignoreDefaultParameters: true` |
| | `complexity>LongMethod.ignoreAnnotated: [Composable]` |
| | `style>MagicNumber` with `ignorePropertyDeclaration: true` and `ignoreCompanionObjectPropertyDeclaration: true` |
| | `style>UnusedPrivateMember.ignoreAnnotated: [Preview]` |
| | `complexity>TooManyFunctions.ignoreAnnotatedFunctions: [Preview]` |

## Phase 0 Spikes

**S1: detekt.** Read-only, local.
- **Pass:**
  1. `detekt --version` = 1.23.8.
  2. A run over `app/src` reports no parse/PSI errors on any file.
  3. `detekt --help` plus a two-file probe confirms the `--input` separator. 1.x docs suggest a comma, against the `:` assumed earlier; set `IFS` to whatever passes.
  4. A new issue in a staged file aborts the commit; a baselined one passes.
  5. The hook takes ≤10 s on 3 files.
  6. The baseline entry count is recorded.
- **Fallback:**
  - If ≤3 non-core files fail to parse, add them to `--excludes` and record it.
  - If more fail, or any domain/usecase file fails, escalate to the user (option: a 2.0 alpha CLI jar).
  - If the baseline has more than 300 entries, disable or tune noisy rules before baselining.

**S2: CodeQL.** Runs on this change's own PR to `dev`.
- **Pass:**
  - Tool status shows Kotlin files scanned ≥90% of `app/src/main/**/*.kt`.
  - The job takes ≤20 min.
  - It uploads a SARIF.
- **Fallback, in order:**
  1. Drop `-Pkotlin.compiler.execution.strategy=in-process`. With AGP 9 built-in Kotlin the property may be ignored.
  2. Add `--rerun-tasks`.
  3. `build-mode: autobuild`.
  4. If extraction is still empty: keep CodeQL non-gating, skip admin step A2, and escalate.

**S3: ruleset gate.** Run after the first green `dev` push analysis. Rulesets have no evaluate mode on this plan, so the probes are live.
- **Probes:**
  - (a) a docs-only probe PR must become mergeable after CodeQL completes;
  - (b) a throwaway probe PR with a known High alert (intent extra → `ProcessBuilder`) must be blocked by the rule, then closed unmerged;
  - (c) the first Dependabot PR to `dev` gets an analysis and is not blocked by pre-existing `dev` alerts.
- **Fallback:**
  - If (a) stays "waiting for results", or rule evaluation does not work for PRs to non-default `dev`: remove the rule, keep CodeQL informational, record it and ask the user.
  - If (c) fails: document a manual Dependabot path.

**S4: gitleaks history.** Run a local full-history scan: `gitleaks git --redact --no-banner .` (v8.30.1).
- **Pass:** 0 findings → no allowlist file.
- **Fallback:**
  - A false positive gets a `.gitleaksignore` fingerprint (D13).
  - A real secret stops apply and is escalated: rotation and history rewrite are out of scope.

**S5: Dependabot.** Runs after promotion, because the config is read from `main`.
- **Pass:**
  - Version PRs open against `dev` with `build(deps):`/`ci(deps):` titles.
  - `[plugins]` versions (agp, ksp, spotless, kover) are bumped.
  - Groups match as intended.
  - `@dependabot rebase` on a retargeted security PR rebases onto `dev`.
- **Fallback:**
  - Plugins not bumped: document a manual bump.
  - Rebase does not target `dev`: use close and reopen (D11).
  - Groups wrong: adjust the patterns in a follow-up.

## Admin Steps (each needs explicit user confirmation)

1. **A1.** Merge the PR to `dev`. Then:
   - enable vulnerability alerts (`PUT /repos/{o}/{r}/vulnerability-alerts`);
   - enable security updates (`PUT .../automated-security-fixes`), in that order.
2. **A2.** After the first green `dev` push analysis and S3 (a): GET ruleset 27200640, append the `code_scanning` rule (CodeQL, `errors`, `high_or_higher`), then PUT.
3. **A3.** No admin action: promotion to `main` activates `dependabot.yml` and the cron. S5 follows.

**Rollback order.**
1. Remove the code-scanning rule from the ruleset first. Without `codeql.yml`, it would block every PR.
2. Revert the PR (`ci-gate` → `unit-tests` only; this removes CodeQL, Dependabot and detekt).
3. Optionally disable security updates, then alerts.

## File Changes

| File | Action | Description |
|---|---|---|
| `.github/workflows/ci.yml` | Modify | `secret-scan`, `ci-gate` needs, Dependabot guard, header comment |
| `.github/workflows/codeql.yml` | Create | CodeQL analysis (D4-D5) |
| `.github/dependabot.yml` | Create | Version updates (D10) |
| `scripts/secret-scan-range.sh`, `scripts/test-secret-scan-range.sh` | Create | Range script and harness (D8) |
| `lefthook.yml` | Modify | detekt job |
| `config/detekt/detekt.yml` | Create | Overrides (D3) |
| `config/detekt/baseline.xml` | Create (generated) | Legacy issues |
| `README.md` | Modify | D14 |
| `openspec/config.yaml` | Modify | `quality.static_analysis`, verify rule |
| `.gitleaksignore` | Conditional | Only if S4 finds false positives |

`promote.yml`: no functional change. Its comment "ci-gate only aggregates the unit tests" becomes stale (see Open Questions).

**Size estimate (authored lines, `baseline.xml` excluded).**

| File | Lines |
|---|---|
| `ci.yml` | ~45 |
| range script + harness | ~95 |
| `codeql.yml` | ~40 |
| `dependabot.yml` | ~40 |
| `lefthook.yml` | ~4 |
| `detekt.yml` | ~55 |
| README (+/-) | ~80 |
| `config.yaml` | ~4 |
| **Total** | **≈365** |

That is well within 800. The OpenSpec change artifacts are not counted, following the archived precedent.

## Testing Strategy

| Layer | What | Approach |
|---|---|---|
| Unit (shell, Strict TDD) | `secret-scan-range.sh` | Write the harness first (RED). Cases: PR, push, zero `before`, unreachable `before` (temp git repo), malformed SHA → exit 2 |
| Static | Workflows, scripts | actionlint/shellcheck in the hook; check `dependabot.yml` against its schema |
| Live probes | AC | A fake secret committed with `--no-verify` makes `secret-scan` and `ci-gate` red, and the log is redacted. Plus S1 hook probes, S3 PRs, a Dependabot PR with no publish/comment, and `:app:testDebugUnitTest` green |

## Threat Matrix

| Boundary | Applicability | Design response | RED tests |
|---|---|---|---|
| Documentation-like paths | N/A: globs only select analysis inputs; nothing is executed by name | — | — |
| Git repository selection | Applicable | Range only from event SHAs passed via `env:`; `fetch-depth: 0`; scan path `.`; no `git -C` | Harness PR/push cases; `--no-verify` secret probe |
| Commit state | Applicable | detekt sees only `{staged_files}`. No `.kt` staged → job skipped. Partially staged files are linted from the worktree (same as spotless; accepted) | S1: staged new issue aborts; docs-only commit skips detekt |
| Push state | Applicable | No new job pushes. Zero or unreachable `before` → head-only, with a warning. Never an empty range | Harness zero/unreachable cases |
| PR commands | Applicable | SHAs and actor only through `env:`. SHAs validated with a regex before `--log-opts` (no option injection) | Harness malformed SHA → exit 2 |

| Risk | Mitigation |
|---|---|
| Public logs leak a secret | `--redact`, no `-v`, no report file uploaded |
| Read-only tokens (Dependabot, forks) | CodeQL uploads are allowed on `pull_request`. `report` publish is guarded (D12); fork PRs are already guarded |
| A gitleaks false positive blocks merge and promotion | S4 plus a `.gitleaksignore` fingerprint in a fix PR; rollback step 2 as a last resort |
| A missing CodeQL analysis blocks PRs | No path filters; push to `dev`; rule only after green runs (A2); retarget plus reopen for security PRs (D11); rollback step 1 |
| Local brew gitleaks/detekt drift from CI | CI is pinned; README documents the versions |

## Living Specs Interaction

The archived D11 is not edited (archive rule). This design's D1 refines it. The following living requirements contradict this change and must be MODIFIED by the spec deltas, which already exist in `specs/`. Archiving merges them.

**`ci-pipeline`:**
- Purpose
- "No Static Analysis in CI", which bans gitleaks in `ci.yml`
- "Least privilege", which allows write scopes only on report/cleanup
- "Single Required Check", which depends only on `unit-tests`
- "Documentation of CI and Admin Steps"
- "Out of Scope Boundaries", which bans CodeQL and Dependabot

**`code-quality-gates`:**
- "Pre-commit Hook", which says hooks are the only enforcement point and describes the bypass scenario
- "Documented Setup", which lists four binaries

## Open Questions

- [ ] Should the stale `promote.yml` comment get a one-line update (comment only)? The confirmed decision says "no change".
- [ ] Spec alignment: the delta covers zero `before` only. Confirm that an unreachable `before` → head-only with a warning (D8) is acceptable, rather than failing closed.
- [ ] The `--input` separator: 1.x likely uses a comma, not `:`. S1 decides; this does not block the design.
