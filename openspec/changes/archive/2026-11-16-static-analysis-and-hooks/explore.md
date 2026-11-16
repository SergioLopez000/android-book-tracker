# Exploration: static-analysis-and-hooks

Source: GitHub issue #2 "chore: static analysis and local hooks". Engram mirror: `sdd/static-analysis-and-hooks/explore` (project `libros-leidos`).

## Scope (confirmed by the user)

- In: Spotless + ktlint, detekt + Compose rules (baseline), Android Lint (baseline), Kover (report only), gitleaks, actionlint, conventional-commit check, lefthook hooks.
- Out: hadolint and Spectral (no Dockerfile/OpenAPI yet, moved to #6); any CI workflow or job (moved to #3).

## Current state

- Single module `:app`: 64 Kotlin files, about 3.4k lines (main + test).
- No `.editorconfig`, lint, format or coverage configuration. `openspec/config.yaml` marks linter, formatter and coverage as unavailable.
- Version catalog `gradle/libs.versions.toml`; root `build.gradle.kts` declares plugins with `apply false`; `settings.gradle.kts` includes `gradlePluginPortal()`.
- `gradle.properties` sets `android.disallowKotlinSourceSets=false` (KSP workaround).
- Only workflow: `.github/workflows/promote.yml` (the only actionlint target).
- `local.properties` is git-ignored and holds the optional `GOOGLE_BOOKS_API_KEY`.

## Compatibility (checked 2026-11-16)

| Tool | Candidate | Notes | Source |
|---|---|---|---|
| Spotless Gradle plugin | 8.11.0 | Gradle 8.1+, Java 17 | https://raw.githubusercontent.com/diffplug/spotless/main/plugin-gradle/CHANGES.md |
| ktlint | 1.8.0 (pin) | 2.0.0-ALPHA targets Kotlin 2.4: avoid | https://github.com/pinterest/ktlint/releases |
| detekt | 2.0.0-alpha.3 | Built on Kotlin 2.3.21 / AGP 9.1.1 / Gradle 9.3.1; supports AGP 9 built-in Kotlin. Stable 1.23.8 is built on Kotlin 2.0.21 / AGP 8.8.1. alpha.6 moved to Kotlin 2.4.10. Plugin id `dev.detekt`. | https://github.com/detekt/detekt/releases, https://detekt.dev/docs/gettingstarted/gradle |
| compose-rules (detekt) | 0.6.0 | Pairs with detekt alpha.3 (0.6.4+ pairs with alpha.6). Enable under `Compose:` in `detekt.yml`. | https://github.com/mrmans0n/compose-rules/releases |
| Kover | 0.9.11 | AGP 9 support since 0.9.4; Gradle 9.6 fix in 0.9.9. Report only. | https://github.com/Kotlin/kotlinx-kover/releases |
| Android Lint | bundled with AGP | `lint { baseline = file("lint-baseline.xml"); abortOnError = true }` | https://developer.android.com/studio/write/lint |
| lefthook | 2.2.2 | Standalone Go binary | https://github.com/evilmartians/lefthook/releases |
| gitleaks | 8.30.1 | `detect`/`protect` deprecated | https://github.com/gitleaks/gitleaks |
| actionlint | 1.7.12 | | https://github.com/rhysd/actionlint/releases |
| commitlint | Node-based | Needs package.json; repo has none | https://lefthook.dev/examples/commitlint/ |

## Approaches

| # | Approach | Pros | Cons |
|---|---|---|---|
| 1 | Gradle-first, detekt 2.0.0-alpha.3 + compose-rules 0.6.0, brew-installed hook tools, bash regex for commit messages | Matches the toolchain exactly; no Node | detekt 2.0 is alpha; contributors must install tools |
| 2 | Same with detekt 1.23.8 + compose-rules 0.4.x | Stable line | Kotlin 2.0.21 vs 2.3 mismatch; fallback only |
| 3 | Add package.json + commitlint | Full commitlint | JS toolchain in an Android repo |
| 4 | Spotless `ratchetFrom` instead of a one-time reformat | No large format commit | Unformatted files linger; depends on a git ref |

## Recommendation

Approach 1. Start with a compatibility spike (detekt alpha.3 + compose-rules 0.6.0; fallback alpha.6 + 0.6.8, last resort 1.23.8). Baselines for detekt and Android Lint; ktlint debt fixed in one pure-format commit (SHA in `.git-blame-ignore-revs`); Kover report only. Hooks: pre-commit runs Spotless, detekt, gitleaks (staged) and actionlint; commit-msg runs a bash regex; Android Lint stays out of pre-commit (slow).

## Size estimate

About 250-290 hand-written lines plus an unmeasured 150-700-line ktlint reformat. A split into a tooling PR and a format-only PR is probable against the 800-line budget.

## Risks

- detekt 2.0 alpha: config and rule ids may change; alpha and compose-rules versions are coupled.
- KSP-generated sources must be excluded from detekt and Kover.
- Kover with Robolectric may need exclusions (unverified).
- Hooks are local-only and bypassable; remote enforcement arrives with #3.
- Lint baseline is AGP-version sensitive.

## Unverified

Spotless 8.11.0 default ktlint version; lefthook install docs (404); Kover docs page lags (0.9.8); ktlint Composable naming `.editorconfig` key; gitleaks staged-scan flags (`gitleaks git --pre-commit --staged`).
