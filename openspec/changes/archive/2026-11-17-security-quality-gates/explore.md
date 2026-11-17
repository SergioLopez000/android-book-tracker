# Exploration: security-quality-gates

Issue #4, branch `ci/4-security-quality-gates`. Mirrored from Engram `sdd/security-quality-gates/explore` (obs #102).

## Current State

- **Build**: single Gradle module `:app`. Versions (`gradle/libs.versions.toml`):
  - AGP 9.1.1, Kotlin 2.3.20, KSP 2.3.11
  - spotless 8.11.0, ktlint 1.8.0 (android_studio style, `.editorconfig`)
  - compose-lint-checks 1.5.4, kover 0.9.11
  - Gradle wrapper 9.6.0, JDK 17
  - `gradle.properties`: `org.gradle.caching=true`, `parallel=true`
- **`lefthook.yml`**:
  - `pre-commit` (`parallel: true`, jobs with name/glob/run): spotless (`*.{kt,kts}`), android-lint, gitleaks (`gitleaks git --pre-commit --staged --redact --no-banner`, with a `command -v` guard and brew hint), actionlint, shellcheck.
  - `commit-msg`: `scripts/check-commit-msg.sh` (regex `^(types)(\(scope\))?!?: [^ ].{0,99}$`, scope `[a-z0-9._/-]+`).
- **`ci.yml`**:
  - Jobs:
    - `unit-tests`
    - `report`: non-gating, with contents and pull-requests write.
    - `ci-gate`: `needs: [unit-tests]`, `if: always()`. It is the only required check in dev ruleset 27200640.
  - Allure is pinned with `ALLURE_VERSION` + `ALLURE_SHA256` env, `curl -fsSL -o`, then `sha256sum -c -`.
  - Actions are pinned to majors.
  - Triggers: `pull_request` (no branch filter) and push to `dev`.
- **Branches**: the default branch is `main`, fast-forwarded by `promote.yml`; `dev` is the integration branch.
- **Missing**: `.github/dependabot.yml`, a CodeQL workflow, detekt, `.gitleaks.toml`.
- **README** (Spanish) states that static analysis is local-only and records the accepted `--no-verify` risk.
- **Prior decisions**:
  - Engram #93: static analysis is local-only.
  - Engram #101 refines it for this issue:
    - quality stays local-only and detekt joins the pre-commit hook
    - CI runs only slow, GitHub-service or non-bypassable security checks: CodeQL on PRs + weekly, gitleaks over the commit range via the binary
    - Dependabot weekly
    - SonarCloud dropped; SonarQube for IDE recommended in the README
    - ONLY free tooling

## Findings per item

### 1. detekt

**Versions**
- Stable is 1.23.8 (Feb 2025): Kotlin 2.0.21, Gradle 8.12.1, AGP 8.8.1.
- 2.0 is alpha only, under plugin id `dev.detekt` (1.x used `io.gitlab.arturbosch.detekt`):
  - alpha.3: Kotlin 2.3.21, Gradle 9.3.1, AGP 9.1.1
  - alpha.4/5: Kotlin 2.4.0
  - alpha.6 (2026-08-04): Kotlin 2.4.10, Gradle 9.6.1, AGP 9.3.1
- The project (AGP 9.1.1 / Kotlin 2.3.20 / Gradle 9.6.0) matches no alpha exactly.

**Gradle plugin**
- It couples detekt's embedded compiler to the build.
- Its task has no `--input`, so a staged-files run needs a hack.
- There is a known crash with type resolution and `.kts` files (#5501).

**CLI (`detekt-cli`)**
- Options: `--input` for multiple paths, `--config`, `--build-upon-default-config`, `--baseline`, `--create-baseline`.
- The `--input` separator is `:` in the 2.0 docs, while 1.x used comma or semicolon. **Verify empirically.**
- The official docs recommend the CLI on staged files for pre-commit hooks.
- Caveat: there is no type resolution, so rules that need it are skipped.
- The brew package `detekt` tracks the stable release.

**Run time (estimates)**
- CLI on a few files: about 2-5 s.
- Gradle on the whole module: about 10-30 s with a warm daemon.
- The hook runs jobs in parallel, so the CLI job hides behind `lintDebug`.

**Baseline**
- XML `SmellBaseline`. IDs are `RuleId:FileName:Signature`, using the file name only, so partial `--input` runs work.
- The 2.x ID format changed, so a baseline must be regenerated when upgrading from 1.x.
- Generate it over the whole `app/src`.

**ktlint overlap**
- Do NOT add the formatting/ktlint-wrapper rule set.
- Disable style rules that duplicate ktlint/Spotless: MaxLineLength, NewLineAtEndOfFile, WildcardImport, UnusedImports, ModifierOrder, …
- Compose tuning:
  - FunctionNaming with `ignoreAnnotated: [Composable]`
  - probably LongParameterList, LongMethod, MagicNumber and TopLevelPropertyNaming

**Wiring**
- A `detekt` job in `lefthook.yml` with glob `*.kt`, a `command -v` guard and a brew hint.
- Config in `config/detekt/detekt.yml` and `config/detekt/baseline.xml`.

### 2. CodeQL

**Support**
- Kotlin 1.8.0-2.4.20 is supported.
- `github/codeql-action@v4`.

**Build mode**
- `none` does NOT analyze Kotlin, so it is unusable here.
- Recommended: an advanced workflow with `build-mode: manual`:
  - Build with `./gradlew :app:compileDebugKotlin --no-daemon --no-build-cache`.
  - With the build cache on, a FROM-CACHE compile never runs kotlinc and leaves the Kotlin database empty.
  - If the tracer misses the daemon, possibly add `-Pkotlin.compiler.execution.strategy=in-process`.
  - **UNVERIFIED: needs a spike.**

**Triggers**
- `pull_request` to `dev` plus a weekly cron. Optionally `push` to `dev`, to give PRs an alert baseline.
- A schedule only fires from the default branch (`main`), so the cron starts after the first promotion and scans `main`.

**Other**
- **Permissions**: `contents: read` by default, and `security-events: write` on the job. Fork and Dependabot PRs get a read-only token, so the SARIF upload may fail.
- **Run time**: about 8-15 min (estimate). Free for public repos.
- **Gating**: recommended non-gating, in a separate workflow. If gating is wanted later, use the free "Require code scanning results" ruleset rule.

### 3. gitleaks in CI

**Version and pin**
- v8.30.1. The `linux_x64` sha256 is `551f6fc83ea457d62a0d98237cbad105af8d557003051f41f3e7ca7b3f2470eb`. **Re-verify at apply.**
- Maintenance risk: the README says gitleaks is feature-complete with security patches only, and the author is moving to "Betterleaks".
- Pin it the same way as Allure: `GITLEAKS_VERSION` + `GITLEAKS_SHA256` env, then curl, `sha256sum -c -` and tar.

**Job**
- A `secret-scan` job in `ci.yml`: `fetch-depth: 0`, `contents: read`.
- Command: `gitleaks git --log-opts="<range>" --redact --no-banner --exit-code 1 .`
- `--redact` is mandatory because logs are public. Avoid `-v`.

**Ranges**
- PR: `base.sha..head.sha`.
- Push: `before..sha`. When `before` is all zeros, scan only the head commit.
- Pass these through env vars.

**Other**
- Run one full-history scan at apply, to decide whether an allowlist is needed.
- **Gating: YES**, via `ci-gate` `needs`. The name is unchanged, so no ruleset change, and `promote.yml` also blocks on leaks.

### 4. Dependabot

**Config**
- `.github/dependabot.yml` version 2, ecosystems `gradle` and `github-actions` (`directory: /`), weekly, `target-branch: dev`.
- The version catalog is supported. Whether `[plugins]` entries are updated is unconfirmed: check the first PRs.
- The bare `ktlint` version is never bumped.

**Timing**: the config is read from `main`, so it is inert until the first promotion. Security-update PRs behave differently with `target-branch`.

**Commit prefix**
- `build(deps): bump …` for Gradle and `ci(deps): bump …` for Actions.
- Both match `check-commit-msg.sh`.
- PR titles are not validated, so the prefix only keeps squash history consistent.

**Grouping**
- androidx/compose, kotlin+ksp, kotlinx, test tooling, and all actions in one group.
- `open-pull-requests-limit` about 5.

**Side effect**
- Dependabot PRs get a read-only token, so the `report` publish fails. It is red but non-gating.
- Guard it with `github.actor != 'dependabot[bot]'`.

### 5. README

Write it in Spanish:
- Add a "qué corre dónde" table:
  - pre-commit: spotless, lint, detekt, staged gitleaks, actionlint, shellcheck, commit-msg
  - CI: tests, gitleaks over the range, CodeQL
  - repo config: Dependabot
- Update the "solo local" statement and the accepted-risk note: quality can still be bypassed, but secrets are now caught in CI.
- Recommend SonarQube for IDE in standalone mode.
- Add detekt to the brew line.

## Affected Areas

- `lefthook.yml`
- `config/detekt/detekt.yml` and `config/detekt/baseline.xml` (new)
- `.github/workflows/ci.yml`
- `.github/workflows/codeql.yml` (new)
- `.github/dependabot.yml` (new)
- `README.md`
- `openspec/config.yaml`
- delta specs for `ci-pipeline` and `code-quality-gates`
- maybe `.gitleaks.toml` / `.gitleaksignore`

## Spec contradictions (MODIFY in delta specs)

**ci-pipeline**
- MODIFY:
  - the Purpose paragraph
  - "No Static Analysis in CI": quality stays local, security runs in CI
  - "Workflow Triggers and Runner" and its least-privilege scenario: CodeQL needs `security-events: write`
  - "Single Required Check (ci-gate)": unit-tests + secret-scan
  - "Documentation of CI and Admin Steps"
  - "Out of Scope Boundaries"
- ADD:
  - CodeQL workflow
  - `secret-scan` job
  - Dependabot config

**code-quality-gates**
- MODIFY:
  - "Pre-commit Hook": add detekt and a secrets exception
  - "Documented Setup": five binaries plus the detekt command
- ADD "Detekt Gate With Baseline".

## Approaches

**detekt**
- (A) CLI on staged files via brew. **Recommended.**
- (B) Gradle plugin, 2.0 alpha.
- (C) CLI jar pinned and checksummed through a script.

**CodeQL**
- Advanced workflow with a manual build. **Recommended.**
- Autobuild.
- Default setup.
- `none`: useless for Kotlin.

**gitleaks**
- A job in `ci.yml` that feeds `ci-gate`. **Recommended.**
- A separate workflow, which cannot feed `ci-gate`.

## Risks

- **detekt**:
  - Hook latency is low with the CLI.
  - Compose rules may cause false positives.
  - 1.23.8 parses with Kotlin 2.0.21 against Kotlin 2.3 sources.
  - The 2.0 alpha changes the baseline format.
- **CodeQL**:
  - Kotlin extraction under the tracer, Gradle cache or daemon.
  - 8-15 min runs.
  - Read-only token on fork and Dependabot PRs.
  - The cron starts only after the first promotion to `main`.
- **Dependabot**:
  - PR noise and a red `report` job on its PRs.
  - Inert until its config is on `main`.
  - Coupled Kotlin/KSP/AGP/Compose BOM bumps.
- **gitleaks**:
  - Gating, so a false positive blocks merge and promotion (needs an allowlist path).
  - Maintenance status.
  - Version drift between local and CI.
- **Free tier**: CodeQL and the free Actions minutes require the repo to stay PUBLIC.
- **Size**: the review budget is 800 lines and `baseline.xml` size is unknown.

## Open questions

1. detekt version: stable 1.23.8 via brew, or a 2.0.0-alpha CLI?
2. Should CodeQL also run on push to `dev`?
3. Is CodeQL non-gating and gitleaks gating through `ci-gate`?
4. Dependabot: hold major bumps? Is the group policy OK?
5. Guard the `report` publish steps for `dependabot[bot]`?

## Research phase

A research phase is not warranted. The remaining unknowns can only be settled by running things: the CodeQL manual build, the detekt `--input` separator and version, and Dependabot `[plugins]` entries. Handle them as spike tasks.

## Ready for Proposal

Yes, after questions 1-3.
