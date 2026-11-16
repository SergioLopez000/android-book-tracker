# Apply Progress: static-analysis-and-hooks

## Batch 1 (PR 1, branch chore/2-static-analysis-format): tasks 1.1-1.8 DONE

Mode: Strict TDD (config/format batch: red check, then green, plus probe and regression).

Commits (local, not pushed):
- 9c9137c `build: add spotless and editorconfig`
- f0df21c `style: apply spotless formatting` (formatter output only)

Versions: Spotless 8.11.0 and ktlint 1.8.0 both resolved; no fallback needed.

TDD evidence:
- RED: `./gradlew spotlessCheck` failed after unit 1 (format violations in 46+ files).
- GREEN: `./gradlew spotlessCheck` exit 0 after unit 2 (also with --rerun-tasks).
- Regression: `./gradlew :app:testDebugUnitTest` exit 0; 102 tests, 0 failures, 0 errors, 0 skipped.
- Probe (1.8): appended `import java.util.*` plus misspaced val to Book.kt; spotlessCheck failed naming `app/src/main/java/com/librosleidos/domain/model/Book.kt`; reverted.

Format commit size: `git diff --shortstat HEAD~1 HEAD` = 49 files changed, 293 insertions(+), 201 deletions(-) (494 lines, under the 800 budget; no size:exception needed). Only app/src/**/*.kt changed; *.gradle.kts needed no changes.

.editorconfig path-scoped disables (ktlint could not auto-fix; no code edited):
- `[RemoteUtils.kt]` `ktlint_standard_kdoc = disabled` (dangling top-level KDoc at L7).
- `[{BookDaoTest,StarRatingTest,BookEditViewModelTest}.kt]` `ktlint_standard_max-line-length = disabled` (unbreakable test-name string lines >100).
- `property-naming` for ui/theme was NOT flagged; no disable added.

Learned:
- Directory-qualified or `**/`-prefixed section globs were not honored by Spotless's ktlint in apply mode; bare file-name globs work.
- Gradle daemon caches the editorconfig; run `./gradlew --stop` after editing `.editorconfig` before re-testing.
- android_studio style removes trailing commas in call sites (most of the reformat).

## Batch 2 (PR 2, branch chore/2-static-analysis-tooling): tasks 2.1-2.17 DONE

Mode: Strict TDD. The commit-msg script was the real TDD unit (RED harness, GREEN script, shellcheck refactor). Tool config used deliberate-violation probes plus a clean-tree pass as evidence.

Commits (local, not pushed), all passed through the installed lefthook hooks (from unit 6 on):
- 0ed61b2 `build: add android lint with baseline and compose-lints`
- 4d28896 `build: add kover coverage report`
- 4f4dcf9 `build: add conventional commit message validator`
- 8c5c1f0 `build: add lefthook pre-commit and commit-msg hooks`
- 3f88aa3 `docs: document quality tooling and ignore format commit in blame`

### compose-lints spike: KEEP 1.5.4
Done directly on this branch. `./gradlew :app:lintDebug --stacktrace` with 1.5.4 completed lint analysis (exit 1 only because existing issues were not baselined yet). No `ObsoleteLintCustomCheck`/`LintError` in `lint-results-debug.xml` (0 matches). `ComposeModifierWithoutDefault` was reported for the probe: `Probe.kt:7: Error: This @Composable function has a modifier parameter but it doesn't have a default value.` 1.4.3 fallback not needed; AGP untouched.

### Lint
- Baseline: `./gradlew :app:updateLintBaseline` exists in AGP 9.1.1 and works. `app/lint-baseline.xml` has 10 issues (110 lines): ComposeMultipleContentEmitters 1, ComposeParameterOrder 2, ComposeViewModelInjection 2, ModifierParameter 2, MonochromeLauncherIcon 2, ObsoleteSdkInt 1. D7 time-based checks are ignored through `app/lint.xml` (not baselined).
- Clean tree: `:app:lintDebug` exit 0.
- Probe compose (`Probe(modifier: Modifier)`): exit 1, `ComposeModifierWithoutDefault`.
- Probe `Log.d("probe" + x, "v=" + x)`: exit 0. No default-enabled lint check flags it (LogConditional/LogTagMismatch are off or irrelevant at minSdk 26). Substituted probe: unused string resource `probe_unused` in `strings.xml` -> `UnusedResources` error, exit 1 (this proves warningsAsErrors). Probes reverted.

### Kover
- Plugin 0.9.11; `kover { reports { filters { excludes { classes(...) } } } }` nesting is valid on first run. No `koverVerify`.
- `koverHtmlReportDebug koverXmlReportDebug` exit 0. Report: `app/build/reports/kover/htmlDebug/index.html` (XML at `app/build/reports/kover/reportDebug.xml`). 60 classes in 9 packages (`com.librosleidos`, data.local, data.remote, di, domain.model, domain.usecase, ui.components, ui.edit, ui.list); no `*_Impl`, `ComposableSingletons`, `BuildConfig` or `R` classes (generated classes excluded).

### commit-msg TDD
- RED: harness with 31 `message|expected-exit` cases; with the script missing the harness exits 1 ("validator not found"). It also compares the exact exit code, so a missing validator (127) cannot pass the reject cases.
- GREEN: `scripts/check-commit-msg.sh`; harness "all 31 cases passed", exit 0.
- REFACTOR: `shellcheck scripts/*.sh` exit 0, harness still green.
- Boundary found: a subject with exactly 100 chars after `: ` is valid, 101 is rejected (design regex `.{0,99}` after one char).

### lefthook
- `lefthook install` run locally; `lefthook validate` "All good". `{1}` template works (bad message blocked). The `*.kt` glob matched the nested path in the spotless probe. Added a `command -v shellcheck` guard to the shellcheck job (D9 consistency).
- Probes (none committed, all reverted):
  - Misformatted staged `.kt` (wildcard import mid-file): blocked by spotless, exit 1, hint "Run ./gradlew spotlessApply".
  - Staged AWS-format fake key fixture: blocked by gitleaks, exit 1 ("leaks found: 2"). Redaction verified with `gitleaks ... -v`: `Secret: REDACTED`. Note: the first attempt with the canonical `AKIAIOSFODNN7EXAMPLE` was NOT flagged (gitleaks allowlists it) and created a probe commit, which I undid with `git reset --mixed HEAD~1` and deleted the fixture; the second attempt used a different fake key.
  - Unstaged fixture with a harmless staged file: commit passed, exit 0 (not scanned); that probe commit was reset away.
  - `git commit -a` with the fixture appended to tracked README.md: blocked by gitleaks, exit 1.
  - `runs-on:` removed from `promote.yml`: blocked by actionlint, exit 1 (`"runs-on" section is missing in job "promote"`); file restored.
  - Clean commits (unit 6 and 7 commits): pass, hooks ran.

### Blame-ignore and docs
- `.git-blame-ignore-revs` holds `eff404ed4e3e79bf35b0845706eaf5fb77e273cf` (verified present on the tracker with `git log`). `git blame` on MainActivity.kt:46 shows `eff404ed` without the file and `805984a7` with `--ignore-revs-file`.
- README: new section "Calidad de Código y Hooks Locales" (in Spanish, matching the existing README). `openspec/config.yaml`: formatter, linter, coverage marked available with commands.

### Final verification (2.17)
- `./gradlew spotlessCheck :app:lintDebug koverHtmlReportDebug :app:testDebugUnitTest`: BUILD SUCCESSFUL, exit 0 (mostly up-to-date from earlier runs). Tests: 102, 0 failures, 0 errors, 0 skipped.
- `scripts/test-check-commit-msg.sh` exit 0; `shellcheck scripts/*.sh` exit 0.

### Size
`git diff --shortstat chore/2-static-analysis..HEAD` (before this docs(sdd) commit): 11 files, 310 insertions, 4 deletions. Generated `app/lint-baseline.xml`: 110 insertions. Authored: ~200 insertions, 4 deletions (including README and config), well under 800.

### Deviations from design
- Lint probe: `Log.d` concatenation is not flagged by default; replaced with an unused resource probe.
- Added a `command -v shellcheck` guard to the shellcheck hook job.
- Kover excludes list has no trailing comma (ktlint android_studio removes it).
