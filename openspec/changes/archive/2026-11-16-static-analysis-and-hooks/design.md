# Design: Static Analysis and Local Hooks

## Technical Approach

Two chained PRs. **PR 1** lands Spotless and the one-time reformat, so `spotlessCheck` is strict from the first commit that applies it. **PR 2** adds Android Lint with a baseline, compose-lints (if the spike passes), Kover, lefthook, the commit-msg script and the docs, all on an already formatted tree. Before PR 1, a throwaway-branch spike decides on compose-lints. No production code, layering or runtime behavior changes.

## Architecture Decisions

| # | Decision | Chosen | Rejected | Rationale |
|---|---|---|---|---|
| D1 | PR order | Format first (PR 1), tooling second (PR 2) | Tooling first with a red or ratcheted `spotlessCheck` | Tooling first would leave `spotlessCheck` red, or need `ratchetFrom` (explicitly not used). The lint baseline is also generated on the final formatted sources, so it does not drift. |
| D2 | Spotless placement | Applied in the **root** project, covering `**/*.kt` and all `*.gradle.kts` | Apply in `:app` with `src/**/*.kt` | Root placement also covers `build.gradle.kts` and `settings.gradle.kts`. A single `./gradlew spotlessCheck` task fits a future multi-module setup. |
| D3 | ktlint config source | `.editorconfig` only (no `editorConfigOverride`) | DSL overrides | One source of truth for the IDE and ktlint. |
| D4 | Code style | `ktlint_code_style = android_studio` | `ktlint_official`, `intellij_idea` | Matches Android Studio's formatter (less IDE churn, smaller reformat) and stays compatible with `kotlin.code.style=official`. |
| D5 | Violations ktlint cannot auto-fix | Path-scoped `.editorconfig` rule disable, listed in PR 1 | Code edits inside the format PR | Keeps PR 1 purely mechanical. Known case: PascalCase `val`s in `ui/theme/Color.kt`/`Type.kt` (`property-naming`). |
| D6 | Pre-commit formatter | `spotlessCheck` (fail with a fix hint) | `spotlessApply` + `stage_fixed` | Never rewrites the index. `stage_fixed` with partially staged files can stage hunks the user never selected. Limitation: Gradle checks the work tree, not the index. |
| D7 | Lint severity | `warningsAsErrors = true` and `lint.xml` ignores time-based checks | `warningsAsErrors = false` | Issue #2 requires new warnings to fail. Version-staleness checks (`GradleDependency`, `NewerVersionAvailable`, `AndroidGradlePluginVersion`, `OldTargetApi`) would turn red without any code change. |
| D8 | `checkDependencies` | Omitted (default) | `true` | Single module, so there are no library modules to analyze. `lintChecks` is not affected. |
| D9 | Missing hook binary | Fail with an install hint (`command -v` guard) | Skip silently | A silent skip hides missing secret scanning. |
| D10 | Blame-ignore SHA | Added in PR 2 using the SHA **as landed** on the target branch | Commit SHA from PR 1's own branch | Squash or rebase rewrites SHAs. Merge PR 1 with **rebase-merge** so the format commit stays isolated. |
| D11 | Kover | Report-only, generated-class excludes only | `koverVerify` thresholds; exclude every `@Composable` | Proposal scope. Excluding UI code would hide real gaps. |

## File Changes

| File | PR | Action | Description |
|---|---|---|---|
| `gradle/libs.versions.toml` | 1, 2 | Modify | Versions `spotless`, `ktlint` (PR 1), `kover`, `composeLints` (PR 2). Plugin aliases and the `compose-lint-checks` library. |
| `build.gradle.kts` | 1, 2 | Modify | Apply `spotless` with the block below (PR 1). `kover apply false` (PR 2). |
| `.editorconfig` | 1 | Create | Shown below. |
| `app/src/**/*.kt`, `*.gradle.kts` | 1 | Modify | Output of `spotlessApply` only, in its own commit. |
| `app/build.gradle.kts` | 2 | Modify | Kover plugin + `kover {}`, `android { lint {} }`, `lintChecks(...)`. |
| `app/lint.xml`, `app/lint-baseline.xml` | 2 | Create | Lint config and the generated baseline. |
| `lefthook.yml` | 2 | Create | Hooks. |
| `scripts/check-commit-msg.sh`, `scripts/test-check-commit-msg.sh` | 2 | Create | Validator and its test harness. |
| `.git-blame-ignore-revs` | 2 | Create | Landed SHA of the format commit. |
| `README.md`, `openspec/config.yaml` | 2 | Modify | Setup and commands. `quality`/`coverage` commands. |

## Interfaces / Contracts

```toml
spotless = "8.11.0"; ktlint = "1.8.0"; kover = "0.9.11"; composeLints = "1.5.4"   # fallback 1.4.3
compose-lint-checks = { module = "com.slack.lint.compose:compose-lint-checks", version.ref = "composeLints" }
spotless = { id = "com.diffplug.spotless", version.ref = "spotless" }
kover = { id = "org.jetbrains.kotlinx.kover", version.ref = "kover" }
```

```kotlin
// root build.gradle.kts
spotless {
    kotlin {
        target("**/*.kt"); targetExclude("**/build/**")
        ktlint(libs.versions.ktlint.get())
    }
    kotlinGradle {
        target("*.gradle.kts", "app/*.gradle.kts")
        ktlint(libs.versions.ktlint.get())
    }
}
// app/build.gradle.kts
android { lint {
    baseline = file("lint-baseline.xml"); lintConfig = file("lint.xml")
    abortOnError = true; warningsAsErrors = true
} }
kover { reports { filters { excludes {   // nesting: verify on first run
    classes("*_Impl", "*_Impl\$*", "*ComposableSingletons*", "*.BuildConfig", "*.R", "*.R\$*")
} } } }
dependencies { lintChecks(libs.compose.lint.checks) }
```

```ini
root = true
[*]
charset = utf-8
end_of_line = lf
insert_final_newline = true
trim_trailing_whitespace = true
indent_style = space
indent_size = 4
[*.{kt,kts}]
ktlint_code_style = android_studio
ktlint_function_naming_ignore_when_annotated_with = Composable
[app/src/main/java/com/librosleidos/ui/theme/*.kt]
ktlint_standard_property-naming = disabled   # only if the PR 1 run flags it
```

`app/lint.xml`: `<lint>` with `severity="ignore"` for the four D7 ids. compose-lints ids stay at their defaults.

```yaml
# yaml-language-server: $schema=https://lefthook.dev/schema.json
pre-commit:
  parallel: true
  jobs:
    - name: spotless
      glob: "*.{kt,kts}"
      run: ./gradlew --quiet spotlessCheck || { echo "Run ./gradlew spotlessApply"; exit 1; }
    - name: gitleaks
      run: command -v gitleaks >/dev/null || { echo "brew install gitleaks"; exit 1; }; gitleaks git --pre-commit --staged --redact --no-banner
    - name: actionlint
      glob: ".github/workflows/*.{yml,yaml}"
      run: command -v actionlint >/dev/null || { echo "brew install actionlint shellcheck"; exit 1; }; actionlint
    - name: shellcheck
      glob: "*.sh"
      run: shellcheck {staged_files} && scripts/test-check-commit-msg.sh
commit-msg:
  jobs:
    - name: conventional-commit
      run: scripts/check-commit-msg.sh {1}
```

**commit-msg contract.** Bash, `set -euo pipefail`. It reads the first line of `$1` that is not blank and not a `#` comment. It accepts that line when it matches any of:
- `^Merge `
- `^Revert "`
- `^(fixup|squash|amend)! `
- `^(feat|fix|docs|style|refactor|perf|test|build|ci|chore|revert)(\([a-z0-9._/-]+\))?!?: [^ ].{0,99}$`

Otherwise it prints the expected format and exits 1. An empty message also exits 1. The harness is a table of `message|expected-exit` rows written to a `mktemp` file. It prints per-case FAIL lines and exits non-zero on any failure.

## Testing Strategy

| Layer | What | Approach |
|---|---|---|
| Unit (shell, Strict TDD) | Commit-msg script | Write the harness first (RED: script missing). Cases: valid with and without scope, `!`, merge, revert, fixup/squash, a leading comment line, empty, a bad type, a missing space, an uppercase scope, a subject over 100 characters. |
| Regression | App behavior | `./gradlew :app:testDebugUnitTest` green after PR 1 and after PR 2. |
| Tool | One deliberate violation per tool (below) | Manual and recorded in apply-progress. None of these probes is committed. |

| Tool | Deliberate violation | Expected |
|---|---|---|
| Spotless | Extra spaces or a wildcard import in a `.kt` file | `spotlessCheck` exit ≠ 0 |
| Lint | `Log.d` with a string concatenation, or a hardcoded string in the manifest | `:app:lintDebug` exit ≠ 0 |
| compose-lints | `@Composable fun Probe(modifier: Modifier)` (no default) | `ComposeModifierWithoutDefault` reported, exit ≠ 0 |
| Kover | n/a | `:app:koverHtmlReportDebug` writes `app/build/reports/kover/htmlDebug/index.html`, with no `*_Impl` classes listed |
| gitleaks | Staged `aws_secret_access_key = AKIA...` fixture | Commit blocked, exit 1 |
| actionlint | `runs-on:` removed from `promote.yml` | Exit 1 |

**compose-lints spike (step 0).** Add 1.5.4 and run `./gradlew :app:lintDebug --stacktrace` together with the probe. **Keep** 1.5.4 only if all three hold: lint completes, there is no `ObsoleteLintCustomCheck`/`LintError` for the registry, and the probe is reported. Otherwise retry with 1.4.3. If 1.4.3 also fails, drop compose-lints and record the output. Then generate the baseline with `./gradlew :app:updateLintBaseline`, or delete `lint-baseline.xml` and run `./gradlew :app:lintDebug -Dlint.baselines.continue=true`. Regenerate it the same way after an AGP or compose-lints bump, and review the diff.

## Threat Matrix

| Boundary | Applicability | Design response | RED tests |
|---|---|---|---|
| Documentation-like paths | N/A: no file classification or execution by name | — | — |
| Git repository selection | N/A: lefthook runs at the repo root and no `git -C` is used | — | — |
| Commit state | Applicable | gitleaks scans the **index** (`--staged`). With `commit -a`, files are staged before the hooks run. An empty index or `--amend` passes clean. Spotless checks the work tree (accepted, D6). | Harness cases for the message file. gitleaks staged fixture (blocked), unstaged fixture (not scanned), `commit -a` fixture (blocked). |
| Push state | N/A: no pre-push hook | — | — |
| PR commands | N/A: no PR automation | — | — |

## Migration / Rollout

1. Run the spike on a throwaway branch.
2. PR 1: catalog + Spotless + `.editorconfig` (commit 1), then `spotlessApply` (commit 2). Rebase-merge.
3. PR 2 (based on the merged PR 1): the tooling commits, then `.git-blame-ignore-revs` with the landed SHA, then the README (`lefthook install`, `git config blame.ignoreRevsFile .git-blame-ignore-revs`).

Estimated authored lines: PR 1 is about 45 plus the reformat (unmeasured, 150-700; measure it with `git diff --shortstat`). PR 2 is about 260, excluding `lint-baseline.xml`. To roll back, revert PR 2 and run `lefthook uninstall`. PR 1 reverts independently.

## Open Questions

- [ ] Reformat size against the 800-line budget (ask-on-risk): if it is over, PR 1 needs a `size:exception` (mechanical diff).
- [ ] Verify at apply: Spotless reads `.editorconfig` without `setEditorConfigPath`; the Kover `filters` nesting; whether lefthook `glob` `*.kt` matches nested paths (otherwise use `**/*.kt` or `glob_matcher: doublestar`); the `{1}` template; whether `updateLintBaseline` exists in AGP 9.1.1.
