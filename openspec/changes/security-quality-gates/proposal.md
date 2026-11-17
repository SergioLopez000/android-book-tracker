# Proposal: Security and Quality Gates

## Intent

Issue #4: local hooks can be bypassed (`--no-verify`, web UI), so leaked secrets and vulnerable code reach `dev` unnoticed, dependencies drift, and Kotlin has no static analysis beyond lint. This change refines D11 of the archived `pr-pipeline-with-reports`: **quality stays local-only; security (CodeQL, gitleaks) runs in CI because it must not be bypassable.**

## Scope

### In Scope
- **detekt 1.23.8** CLI (brew) as a lefthook pre-commit job on staged `*.kt`. Config and baseline live in `config/detekt/{detekt.yml,baseline.xml}`. No ktlint wrapper; style rules that duplicate ktlint are disabled; tuned for Compose.
- **CodeQL** `java-kotlin` in `.github/workflows/codeql.yml` with `build-mode: manual`. Triggers: `pull_request` to `dev` (no path filters), `push` to `dev`, weekly cron. It blocks merges via the dev ruleset 27200640 rule "Require code scanning results". Proposed default thresholds: Alerts = **Errors**, Security alerts = **High or higher**.
- **gitleaks v8.30.1** `secret-scan` job in `ci.yml`. Binary pinned by sha256 `551f6fc83ea457d62a0d98237cbad105af8d557003051f41f3e7ca7b3f2470eb` (re-verify at apply). It scans the PR/push range with `--redact` and is added to `ci-gate` `needs` (name unchanged).
- **Dependabot version updates** (`.github/dependabot.yml`): `gradle` + `github-actions`, weekly, `target-branch: dev`, prefixes `build(deps)` / `ci(deps)`, grouped.
- **Dependabot security updates + vulnerability alerts** enabled. This is an admin step that needs user confirmation at apply. Security PRs target `main`; the documented process retargets them to `dev` by hand.
- Skip `ci.yml` `report` publish steps for `dependabot[bot]` (read-only token).
- README (Spanish): "qué corre dónde" table, updated accepted-risk note, SonarQube for IDE recommendation, detekt setup.

### Out of Scope
- SonarCloud and any paid tooling.
- Quality gates (spotless, lint, detekt, commit-msg) in CI.
- Application code and hexagonal layering (unchanged).

## Capabilities

### New Capabilities
None.

### Modified Capabilities
- `ci-pipeline`:
  - MODIFY: Purpose, No Static Analysis in CI, Workflow Triggers and Runner (least privilege: `security-events: write` on CodeQL), Single Required Check (`unit-tests` + `secret-scan`), Documentation of CI and Admin Steps, Out of Scope Boundaries.
  - ADD: CodeQL Workflow, Secret Scan Job, Dependabot Configuration.
- `code-quality-gates`:
  - MODIFY: Pre-commit Hook (adds detekt; a secrets bypass is now caught in CI), Documented Setup (five binaries plus the detekt command).
  - ADD: Detekt Gate With Baseline.

## Approach

Spikes come first and become phase 0 tasks. After that, each tool is one work unit, and admin steps run last after green runs.

| # | Spike | Fallback |
|---|---|---|
| S1 | detekt 1.23.8 parses the Kotlin 2.3 sources; `--input` separator; baseline size | Exclude unparseable files and record it; otherwise escalate |
| S2 | CodeQL Kotlin extraction with `--no-daemon --no-build-cache -Pkotlin.compiler.execution.strategy=in-process :app:compileDebugKotlin`; measure run time | Adjust flags |
| S3 | Ruleset behavior on `dev`: missing or in-progress analysis, existing alerts, Dependabot PR runs | Gate only after the first `dev` analysis |
| S4 | Full-history gitleaks scan | Add `.gitleaks.toml` allowlist |
| S5 | Dependabot `[plugins]` bumps; config read from `main` only | Document |

## Affected Areas

| Area | Impact | Description |
|---|---|---|
| `lefthook.yml` | Modified | detekt job |
| `config/detekt/detekt.yml`, `baseline.xml` | New | Config and baseline |
| `.github/workflows/codeql.yml` | New | CodeQL analysis |
| `.github/workflows/ci.yml` | Modified | `secret-scan`, `ci-gate` needs, Dependabot guard on `report` |
| `.github/dependabot.yml` | New | Version updates |
| `README.md`, `openspec/config.yaml` | Modified | Docs and tooling |
| `.gitleaks.toml` | Maybe | Allowlist (S4) |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| detekt (Kotlin 2.0.21) fails on 2.3 syntax | Med | S1 |
| CodeQL gate blocks every PR (missing analysis or a workflow that did not run) | Med | No path filters; push to `dev`; enable the rule after the first green run (S3) |
| Kotlin extraction empty or slow (8-15 min) | Med | S2; no build cache |
| gitleaks false positive blocks merge and promotion | Low | Allowlist path; S4 |
| Security PRs target `main` | High | Retarget to `dev` by hand; documented |
| Dependabot inert until promoted to `main`; PR noise | High | Grouping, PR limit; documented |
| Free tier needs the repo to stay public | Low | Documented |
| gitleaks maintenance mode | Low | Pinned version; revisit later |

## Rollback Plan

1. Remove "Require code scanning results" from ruleset 27200640.
2. Revert the PR. This restores `ci-gate` to `unit-tests` only and removes CodeQL, Dependabot and detekt.
3. Optionally disable security updates and alerts.

## Dependencies

Admin steps that need user confirmation at apply:
- Enable vulnerability alerts and security updates.
- Add the code-scanning rule to the dev ruleset after the first green `dev` analysis.

## Success Criteria

- [ ] A staged `.kt` file with a new detekt issue aborts the commit; baselined issues pass.
- [ ] CodeQL analyzes Kotlin on PRs to `dev`, on push and weekly, and a qualifying alert blocks the merge.
- [ ] A fake secret pushed with `--no-verify` fails `secret-scan` and `ci-gate`, and the log is redacted.
- [ ] `dependabot.yml` is valid; security updates and alerts are enabled.
- [ ] Dependabot PRs do not run the `report` publish steps.
- [ ] The README has the table, the risk note and the SonarQube for IDE recommendation.
- [ ] `:app:testDebugUnitTest` stays green.
