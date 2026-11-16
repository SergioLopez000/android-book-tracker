# Proposal: Static Analysis and Local Hooks

## Intent

Issue #2: the repo has no formatter, linter, coverage or commit hooks (`openspec/config.yaml` marks them unavailable). Add local quality gates where existing debt is baselined and not failing, and new violations fail the check.

## Scope

### In Scope
- Spotless + ktlint 1.8.0; `.editorconfig` with Composable naming exception; one-time pure-format commit (SHA in `.git-blame-ignore-revs`); strict `spotlessCheck`.
- Android Lint: committed `lint-baseline.xml` + `lint.xml`, `abortOnError`.
- slackhq compose-lints via `lintChecks`, 1.5.4 (fallback 1.4.3), gated by a first spike running `:app:lintDebug`.
- Kover 0.9.11, report only, excluding generated code only.
- lefthook: pre-commit (Spotless, `gitleaks git --pre-commit --staged --redact`, actionlint + shellcheck); commit-msg (bash Conventional Commits regex; allows merge/revert/fixup).
- README setup/commands section; `openspec/config.yaml` quality/coverage update.

### Out of Scope
- detekt (until 2.0 stable; smells via SonarCloud, #4); compose-rules ktlint ruleset.
- hadolint, Spectral (#6); any CI workflow/job (#3).
- Node/`package.json`; AGP upgrade; Android Lint in pre-commit.

## Capabilities

### New Capabilities
- `code-quality-gates`: format, lint, coverage-report, secret, workflow and commit-message checks; baselined debt passes, new violations fail.

### Modified Capabilities
- None

## Approach

Spike first: compose-lints 1.5.4, then 1.4.3, against AGP 9.1.1; if neither loads, record it and drop compose-lints. Then Gradle plugins via version catalog, baselines generated and committed, hooks via brew-installed binaries. Reformat ships as an isolated commit/PR. No production code or hexagonal layering changes.

## Affected Areas

| Area | Impact | Description |
|---|---|---|
| `gradle/libs.versions.toml`, `build.gradle.kts`, `app/build.gradle.kts` | Modified | Spotless, Kover, lint config, compose-lints |
| `.editorconfig`, `app/lint.xml`, `app/lint-baseline.xml`, `.git-blame-ignore-revs` | New | Tool config/baselines |
| `lefthook.yml`, `scripts/check-commit-msg.sh` | New | Hooks |
| `app/src/**/*.kt`, `*.gradle.kts` | Modified | Format-only |
| `README.md`, `openspec/config.yaml` | Modified | Docs, tooling |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| compose-lints incompatible with AGP 9.1.1 lint | Med | Spike first; fallback; drop |
| Reformat exceeds 800-line budget | High | Separate pure-format PR |
| Lint baseline AGP-sensitive | Med | Documented regeneration |
| Kover/Robolectric or filter syntax issues | Low | Verified on first run |
| Hooks bypassable (`--no-verify`) | High | Accepted; CI in #3 |

## Rollback Plan

Revert the tooling PR (catalog, plugins, configs, hooks); run `lefthook uninstall`. The format commit reverts independently; no runtime behavior or data is touched.

## Dependencies

- Contributor binaries: `brew install lefthook gitleaks actionlint shellcheck`.

## Success Criteria

- [ ] `spotlessCheck`, `:app:lintDebug` pass on the baselined tree (issue #2: debt not failing).
- [ ] A deliberate violation per tool (format, lint, compose-lints if kept, fake secret, broken workflow) fails its check (issue #2: new violations fail).
- [ ] commit-msg script accepts valid/merge/revert/fixup messages, rejects invalid ones.
- [ ] `koverHtmlReportDebug` produces a report.
- [ ] `./gradlew :app:testDebugUnitTest` stays green.
