# Research: static-analysis-and-hooks

Status: partial (all lanes answered; gaps listed below). Date: 2026-11-16. Engram mirror: `sdd/static-analysis-and-hooks/research` (project `libros-leidos`).

## Sources

- S1 https://slackhq.github.io/compose-lints/
- S2 https://raw.githubusercontent.com/slackhq/compose-lints/main/CHANGELOG.md
- S3 https://github.com/diffplug/spotless/blob/main/plugin-gradle/README.md
- S4 https://mrmans0n.github.io/compose-rules/ktlint/
- S5 https://developer.android.com/studio/write/lint
- S6 https://github.com/Kotlin/kotlinx-kover/releases
- S7 https://kotlin.github.io/kotlinx-kover/gradle-plugin/
- S8 https://github.com/gitleaks/gitleaks (incl. `cmd/git.go`)
- S9 https://lefthook.dev/installation/homebrew, https://lefthook.dev/configuration/stage_fixed
- S10 https://github.com/rhysd/actionlint/blob/main/docs/usage.md
- S11 https://github.com/evilmartians/lefthook/blob/master/README.md

## Findings

### 1. compose-lints (partially verified)

- Coordinates: `lintChecks("com.slack.lint.compose:compose-lint-checks:<v>")` (S1).
- 1.6.0+ "requires AGP/lint 9.3+" (S2): not usable with AGP 9.1.1.
- Candidates: 1.5.4 (preferred; 1.5.2 fixed Java 17 compatibility) or 1.4.3 (built on lint 32.2.0 / AGP lint 9.2.0 / Kotlin 2.3.21). Both are built on a newer lint than AGP 9.1.1 ships; minimum lint API is not documented. **Must be proven with `./gradlew :app:lintDebug`** (spike task).
- Configuration through `lint.xml` (`<issue id="..." severity="ignore"/>`, nested `<ignore path="..."/>`).
- `ComposeRememberMissing` removed in 1.5.0 (replaced by lint's `UnrememberedMutableState`); `ComposeUnstableReceiver`/`ComposeUnstableCollections` disabled by default since 1.5.2.

### 2. Spotless + ktlint (partially verified)

- `spotless { kotlin { target("src/**/*.kt"); ktlint("1.8.0").editorConfigOverride(...) }; kotlinGradle { target("*.gradle.kts"); ktlint("1.8.0") } }` (S3). Default code style `intellij_idea`. Pin ktlint explicitly (default version not confirmed).
- `.editorconfig`: `[*.{kt,kts}]` + `ktlint_function_naming_ignore_when_annotated_with = Composable` (unverified by fetch; matches ktlint 1.x behaviour).
- compose-rules ktlint ruleset 0.4.28+ is compatible with ktlint 1.8.0 (S4) but duplicates compose-lints: not used.

### 3. Android Lint baseline (verified, S5)

- `android { lint { baseline = file("lint-baseline.xml"); abortOnError = true; checkDependencies = true; lintConfig = file("lint.xml"); warningsAsErrors = true } }` (`warningsAsErrors`/`lintConfig` partially verified).
- First run creates the baseline; create with `./gradlew :app:lintDebug -Dlint.baselines.continue=true` (still documented). Regenerate by deleting and rerunning. Commit the baseline.

### 4. Kover (verified, S6/S7)

- 0.9.11 latest (2026-09-29); AGP 9 support since 0.9.4/0.9.5. Plugin id `org.jetbrains.kotlinx.kover`.
- Tasks: `:app:koverHtmlReportDebug`, `:app:koverXmlReportDebug` (plus `koverVerifyDebug`, unused).
- Local JVM unit tests (Robolectric) are instrumented; no Robolectric-specific note (unverified).
- Exclude generated classes only (`*_Impl`, `*_Impl$*`, `*ComposableSingletons*`, `*.BuildConfig`); do not exclude all `@Composable`. Exact `reports { filters { excludes { ... } } }` nesting to confirm on a real run.

### 5. lefthook (partially verified)

- `brew install lefthook`, then `lefthook install`. Config `lefthook.yml`, schema `https://lefthook.dev/schema.json`; current docs use `jobs:` lists.
- `stage_fixed: true` only in pre-commit; `{staged_files}` template; commit-msg receives the message file as `{1}` (unverified by fetch).
- Gradle is slow per file: run Spotless at module level.

### 6. gitleaks (partially verified)

- `gitleaks git --pre-commit --staged --redact` (flags verified in `cmd/git.go`). `protect`/`detect` deprecated since 8.19.0.
- Exit 0 = clean, 1 = leaks or error. Config `.gitleaks.toml` with `[[allowlists]]`.
- Project is in maintenance mode (security patches only).

### 7. actionlint (verified, S10)

- `actionlint` with no arguments checks `.github/workflows`. Exit 0 clean, 1 problems, 2 bad flag, 3 fatal.
- shellcheck is not bundled: `brew install actionlint shellcheck`.

## Changes to the exploration

1. compose-lints must be pinned to 1.5.4 (fallback 1.4.3), not the latest 1.6.x, and proven with `lintDebug`.
2. `ComposeRememberMissing` no longer exists; stability checks off by default.
3. compose-rules ktlint ruleset skipped (duplicates compose-lints).
4. `-Dlint.baselines.continue=true` still needed to create the baseline.
5. gitleaks: use `gitleaks git --staged --pre-commit`, not `protect`.
6. actionlint needs shellcheck installed separately.
7. lefthook config uses `jobs:`.

## Gaps

Spotless default ktlint version; ktlint Composable naming key page (404); lefthook `parallel` and `{1}` pages (404); Kover with Robolectric and built-in Kotlin; `warningsAsErrors`/`lintConfig` in the lint docs; latest gitleaks 8.30.x; exact lint API minimum for compose-lints 1.4.3/1.5.x. All are checked during the spike or first apply batch.
