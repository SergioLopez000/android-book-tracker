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
