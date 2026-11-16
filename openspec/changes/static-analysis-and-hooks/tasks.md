# Tasks: Static Analysis and Local Hooks

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | PR 1: ~45 authored + reformat (generated, unmeasured, est. 150-700). PR 2: ~260 authored + `lint-baseline.xml` (generated, size unknown) |
| 800-line budget risk | PR 1: Medium (reformat unmeasured). PR 2: Low authored, baseline is generated |
| Chained PRs recommended | Yes |
| Suggested split | PR 1 (format-first) -> PR 2 (tooling, on merged PR 1) |
| Delivery strategy | ask-on-risk |
| Chain strategy | pending |

Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: pending
800-line budget risk: Medium

Notes: PR 1 reformat size is UNMEASURED until `spotlessApply` runs. Measure with `git diff --shortstat` (format commit only) before opening PR 1. If over 800, request `size:exception` (mechanical diff). PR 1 MUST be rebase-merged (D10) so the format SHA stays isolated and landed SHA is stable. PR 2 is based on merged PR 1.

### Suggested Work Units

| Unit | Goal | Likely PR | Focused test command | Runtime harness | Rollback boundary |
|------|------|-----------|----------------------|-----------------|-------------------|
| 1 | Spotless + `.editorconfig` config | PR 1 | `./gradlew spotlessCheck` (expected red until U2) | probe misformat | revert commit |
| 2 | One-time `spotlessApply` (format only) | PR 1 | `./gradlew spotlessCheck` exit 0 | `:app:testDebugUnitTest` | revert commit |
| 3 | compose-lints spike + Android Lint + baseline | PR 2 | `./gradlew :app:lintDebug` exit 0 | probe violations | revert commit |
| 4 | Kover report | PR 2 | `./gradlew :app:koverHtmlReportDebug` exit 0 | open report, check no `*_Impl` | revert commit |
| 5 | commit-msg script (RED then GREEN) | PR 2 | `scripts/test-check-commit-msg.sh` | harness table | revert commit |
| 6 | lefthook.yml | PR 2 | `lefthook run pre-commit` | gitleaks/actionlint probes | revert + `lefthook uninstall` |
| 7 | blame-ignore, docs | PR 2 | `git blame --ignore-revs-file .git-blame-ignore-revs <file>` | read README | revert commit |

## PR 1: Format first

### Unit 1 (commit 1: `build: add spotless and editorconfig`)
- [x] 1.1 `gradle/libs.versions.toml`: add `spotless = "8.11.0"`, `ktlint = "1.8.0"` and plugin alias `spotless`. Check: `./gradlew help` exit 0. (Formatting Gate)
- [x] 1.2 `build.gradle.kts`: apply spotless (`kotlin` target `**/*.kt` excluding `**/build/**`; `kotlinGradle` target `*.gradle.kts`, `app/*.gradle.kts`; ktlint from catalog). Check: `./gradlew spotlessCheck` runs (red expected).
- [x] 1.3 `.editorconfig`: create per design (`android_studio` style, Composable naming exception, no `editorConfigOverride`). Verify Spotless reads it without `setEditorConfigPath`. Scenario: Composable PascalCase name allowed.

### Unit 2 (commit 2: `style: apply spotless formatting`, format-only)
- [x] 1.4 Run `./gradlew spotlessApply`. If ktlint cannot auto-fix `property-naming` in `app/src/main/java/com/librosleidos/ui/theme/*.kt`, add the path-scoped disable to `.editorconfig` in commit 1 (amend before 1.4), not code edits (D5).
- [x] 1.5 Commit `app/src/**/*.kt` and `*.gradle.kts` output ONLY. Check: `./gradlew spotlessCheck` exit 0. Scenario: Formatted tree passes.
- [x] 1.6 Measure `git diff --shortstat HEAD~1` and record it. If over 800 lines, flag for `size:exception` decision.
- [x] 1.7 Regression: `./gradlew :app:testDebugUnitTest` exit 0. Scenario: Unit tests pass.
- [x] 1.8 Probe (not committed): add extra spaces or wildcard import to a `.kt`; `./gradlew spotlessCheck` exit != 0 naming the file; revert probe. Scenario: Misformatted Kotlin file fails.

## PR 2: Tooling (based on merged PR 1)

### Unit 3 (compose-lints spike FIRST, then lint)
- [ ] 2.1 Spike on a throwaway branch: add `composeLints = "1.5.4"` and `compose-lint-checks` to `gradle/libs.versions.toml`; add `lintChecks(libs.compose.lint.checks)` in `app/build.gradle.kts`; add probe `@Composable fun Probe(modifier: Modifier)`.
- [ ] 2.2 Run `./gradlew :app:lintDebug --stacktrace`. KEEP only if lint completes, no `ObsoleteLintCustomCheck`/`LintError` for the registry, and `ComposeModifierWithoutDefault` is reported. Else retry `1.4.3`; if that fails too, DROP and record the output in `apply-progress` (and verify no build/lint file references compose-lints). Scenarios: Kept/Dropped.
- [ ] 2.3 `app/build.gradle.kts`: `android { lint { baseline = file("lint-baseline.xml"); lintConfig = file("lint.xml"); abortOnError = true; warningsAsErrors = true } }`. Scenario: Abort on error configured.
- [ ] 2.4 `app/lint.xml`: `severity="ignore"` for `GradleDependency`, `NewerVersionAvailable`, `AndroidGradlePluginVersion`, `OldTargetApi` (D7).
- [ ] 2.5 Generate `app/lint-baseline.xml` via `./gradlew :app:updateLintBaseline` (or delete + `./gradlew :app:lintDebug -Dlint.baselines.continue=true`; verify at apply which exists). Remove the probe. Check: `./gradlew :app:lintDebug` exit 0. Scenarios: Baselined tree passes; Kept - baselined tree passes.
- [ ] 2.6 Probes (not committed): `Log.d` with string concatenation (or hardcoded manifest string) -> `:app:lintDebug` exit != 0; if kept, `Probe(modifier: Modifier)` -> compose rule reported, exit != 0. Scenarios: New issue fails; Kept - Compose violation fails.

### Unit 4 (Kover)
- [ ] 2.7 `gradle/libs.versions.toml` (`kover = "0.9.11"`), `build.gradle.kts` (`kover apply false`), `app/build.gradle.kts` (plugin + `kover { reports { filters { excludes { classes(...) } } } }`; verify nesting on first run). No `koverVerify`. Scenario: No threshold.
- [ ] 2.8 Check: `./gradlew koverHtmlReportDebug koverXmlReportDebug` exit 0; `app/build/reports/kover/htmlDebug/index.html` exists and lists no `*_Impl`/`ComposableSingletons`/`BuildConfig`/`R`. Scenarios: HTML/XML report produced; Generated code excluded.

### Unit 5 (commit-msg script, strict TDD)
- [ ] 2.9 RED: `scripts/test-check-commit-msg.sh` with `message|expected-exit` table via `mktemp` (valid with/without scope, `!`, all 11 types, merge, revert, fixup/squash/amend, leading comment line, empty, `updated stuff`, `feature: add x`, `feat: `, uppercase scope, missing space, subject over 100 chars). Run: fails (script missing), exit != 0.
- [ ] 2.10 GREEN: `scripts/check-commit-msg.sh` (bash, `set -euo pipefail`, first non-blank non-`#` line, regex per design, usage message on reject). Check: harness exit 0. Scenarios: all Commit-message Hook scenarios.
- [ ] 2.11 REFACTOR: `shellcheck scripts/*.sh` exit 0; harness stays green.

### Unit 6 (lefthook)
- [ ] 2.12 `lefthook.yml` per design (spotless, gitleaks, actionlint, shellcheck, commit-msg `{1}`). Verify: `*.kt` glob matches nested paths (else `**/*.kt`/`glob_matcher: doublestar`), `{1}` template, `command -v` guards. No Android Lint.
- [ ] 2.13 Probes (not committed): misformatted staged `.kt` blocked; staged fixture `aws_secret_access_key = AKIA...` blocked, redacted, exit 1; unstaged fixture not scanned; `commit -a` fixture blocked; `runs-on:` removed from `promote.yml` blocked by actionlint; clean commit passes. Scenarios: Pre-commit Hook (all four).

### Unit 7 (blame-ignore and docs)
- [ ] 2.14 `.git-blame-ignore-revs`: add the LANDED format-commit SHA from the target branch (not the PR 1 branch SHA). Check: file contains it; `git blame --ignore-revs-file` skips it. Scenario: Format commit is blame-ignored.
- [ ] 2.15 `README.md`: install `lefthook gitleaks actionlint shellcheck`, `lefthook install`, `git config blame.ignoreRevsFile .git-blame-ignore-revs`, commands `spotlessCheck`/`spotlessApply`, `:app:lintDebug`, baseline regeneration, `koverHtmlReportDebug`, `:app:testDebugUnitTest`. Scenario: README lists setup and commands.
- [ ] 2.16 `openspec/config.yaml`: mark formatter, linter, coverage available with commands. Scenario: Config reflects tooling.

### Final verification
- [ ] 2.17 Run `./gradlew spotlessCheck :app:lintDebug koverHtmlReportDebug :app:testDebugUnitTest` (all exit 0) and `scripts/test-check-commit-msg.sh` (exit 0). Record every probe result in apply-progress. Scenario: Unit tests pass.
