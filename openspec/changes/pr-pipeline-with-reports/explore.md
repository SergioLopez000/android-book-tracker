# Exploration: pr-pipeline-with-reports

Source: GitHub issue #3 "ci: PR pipeline with test reports". Engram mirror: `sdd/pr-pipeline-with-reports/explore` (project `libros-leidos`, obs #85).

## Scope (confirmed by the user)

Implement for what exists today and prepare for later work. Lint gate jobs run in parallel and gate a unit-test job. An Allure report for unit tests and a Kover coverage report are published and linked in a PR comment. #6 (contract tests and PIT) and #7 (Appium E2E) add jobs without a redesign. No contract, E2E or PIT jobs now.

## Repo facts (verified)

- `.github/workflows/` has only `promote.yml`.
  - Its check step calls `gh api .../check-runs` without `--paginate` (max 30 runs).
  - It warns instead of failing when no checks exist.
- Default branch is `main`. Rulesets `dev` (27200640) and `main` (27200649) are active. Pages is not enabled. The repo is public.
- `app/build.gradle.kts`:
  - Kover is configured.
  - Lint uses a baseline, with `abortOnError` and `warningsAsErrors`.
  - `compileSdk = 37`.
  - `isIncludeAndroidResources = true`.
- `scripts/check-commit-msg.sh` takes a message file.

## 1. GitHub Actions design

- **Triggers:** `pull_request` and `push` on `dev`.
  - The push run is needed because `promote.yml` checks the check-runs of the dev SHA.
  - No `paths:` filters: a skipped required check stays pending.
- **Concurrency:** `ci-${{ github.event.pull_request.number || github.ref }}`. Cancel in-progress runs for PRs only.
- **Permissions:** `contents: read` at workflow level. Write scopes only on the report job.
- **Job graph:**
  - Parallel lint jobs: `lint-spotless`, `lint-android`, `actionlint`, `gitleaks`, commit messages + shellcheck.
  - These gate `unit-tests`: `testDebugUnitTest` plus the Kover reports, uploaded as artifacts.
  - Then `report`, with `if: always()`.
  - Then the `ci-gate` aggregator. It is the only required check.
- **Action versions** (latest-release pages, to be checked on the first run): checkout v7, setup-java v6 (temurin 17), `gradle/actions/setup-gradle@v6`, upload-artifact v7, download-artifact v8, sticky-pull-request-comment v3, gitleaks-action v3 (Node 24). Pin by tag or SHA.
- **gitleaks-action:** no license is needed for a personal account. Set `GITLEAKS_ENABLE_COMMENTS: false` and `fetch-depth: 0`.
- **Commit-message job:** checks every non-merge commit in `base..head` and the PR title with `scripts/check-commit-msg.sh`.
- **Runner:** `ubuntu-latest` may need SDK platform 37 downloaded (a spike checks this). Robolectric downloads its android-all jars, which the Gradle cache helps with.
- **Fork safety:** use `pull_request`, never `pull_request_target`. Publish and comment only for same-repo PRs. Fork PRs get artifacts only, and a `workflow_run` commenter is deferred.

## 2. Allure for Android unit tests

| Option | Assessment |
|---|---|
| allure-kotlin (`allure-kotlin-junit4`/`-android`) 2.4.0 | Stale (about 4 years old), runner conflicts with Robolectric/Compose rules. Not recommended. |
| allure-java `allure-junit4` 3.x plus the AspectJ agent | Maintained. Android/Robolectric wiring unverified. |
| `io.qameta.allure` Gradle plugin 3.x | Needs Gradle 8.11+, tested with 9.6.1. AGP 9 compatibility unverified. |
| **JUnit XML from Gradle, then Allure CLI (recommended)** | No test or build changes. Allure 2 has a JUnit XML plugin; Allure 3 JUnit XML reading is unverified. Spike first; fall back to `allure-junit4`. |

Results contract: each test job uploads `allure-results-<suite>`, and the report job merges them all.

## 3. Publishing and linking

- Allure HTML does not work from `file://`, so a hosted report is needed for a link.
- **P1 (recommended):**
  - A `gh-pages` branch with per-PR folders (`pr-<N>/allure`, `pr-<N>/kover`) and `dev/` for pushes to dev.
  - A `concurrency: gh-pages` group, because concurrent pushes to gh-pages race.
  - A `pr-cleanup.yml` workflow on PR close.
  - Pages has to be enabled once.
- **P2 (fallback):** artifacts plus the job summary. This does not meet "published and linked".
- **PR comment:** a sticky comment, using marocchino v3 with a fixed header, or about 15 lines of `gh` CLI.
- **Kover in the comment:** an `awk`/`python3` step that parses the root LINE/BRANCH counters from the Kover XML. Kover HTML goes to Pages.

## 4. Extensibility for #6/#7

- Each test job uploads `allure-results-<suite>`, and optionally `coverage-<suite>` or `pit-<suite>`.
- The report job downloads `pattern: allure-results-*` with `merge-multiple: true`.
- #6 and #7 only add their job to the `needs:` of `report` and `ci-gate`.
- The publishing logic lives in a composite action or a reusable workflow. A reusable workflow prefixes check names, so the required check stays the top-level `ci-gate`.

## 5. Acceptance criterion 3 (Closes #N)

- Closing keywords only work for PRs that target the default branch (`main`). Here PRs target `dev`, so the keywords are ignored when those PRs merge.
- Commits that carry a closing keyword close the issue when they reach the default branch.
- It is unverified whether the fast-forward push in `promote.yml` triggers that closing.
- Options:
  1. **Keep default=main (recommended).** The issue closes on promotion, which matches the DoD. The keyword must be in the commit message on dev, set through the merge/squash commit message setting. Verify with a throwaway issue.
  2. Switch default to `dev`. The issue would close before QA, which breaks the DoD. Rejected.
  3. Fallback: a promotion step that parses closing keywords in the promoted range and closes the issues (about 40 lines, `issues: write`).

## 6. Branch protection and promote.yml

- Add `required_status_checks` with context `ci-gate` to ruleset 27200640. This happens after the first successful run, because the context must exist.
- Tighten `promote.yml`:
  - Fail when there are no checks.
  - Require a successful `ci-gate` run.
  - Use `--paginate`.
  - Take the latest run per name.
  - Remove the "#3" warning text.

## Approaches

| Approach | Pros | Cons | Effort |
|---|---|---|---|
| A (recommended): one `ci.yml`, JUnit XML to Allure, per-PR gh-pages, sticky comment, `ci-gate` | No test/build changes, extensible, meets AC2 | Allure JUnit XML spike, Pages to enable, gh-pages grows | ~380-440 lines |
| B: artifacts + summary only | Simplest | No link, misses AC2 | ~200 |
| C: `allure-junit4` + plugin + agent | Rich Allure metadata | Android/AGP 9 unverified, build churn | ~480 |
| D: fork commenting via `workflow_run` now | Fork PR comments | More surface, unneeded for a solo repo | +60 |

Recommendation: A, with C as fallback and D deferred. One PR fits the 800-line budget.

## Risks

- Allure JUnit XML reading is unverified, so a spike decides between Allure 2 and 3.
- SDK 37 may need a download on the runner. Robolectric needs network access.
- Closing issues through a fast-forward push is unverified, so test it empirically.
- Pushes to gh-pages can race, and the branch grows over time.
- The required check must follow the first successful run. A renamed job would block merges.
- Action majors need confirmation on the first run.
- The merge commit message settings need checking manually.
