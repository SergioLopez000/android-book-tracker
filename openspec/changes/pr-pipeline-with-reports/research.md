# Research: pr-pipeline-with-reports

Status: partial (most lanes verified; gaps listed). Date: 2026-11-16. Engram mirror: `sdd/pr-pipeline-with-reports/research` (project `libros-leidos`).

## Findings

### 1. Allure with JUnit XML

- Allure 2: latest 2.46.2 (2026-11-10). A Maven artifact `io.qameta.allure.plugins:junit-xml-plugin` exists. That it is enabled by default in the CLI is partially verified, and the `TEST-*.xml` naming rule (which Gradle output already matches) is unverified.
- Allure 2 install: release tgz `allure-2.46.2.tgz` or Maven `allure-commandline` (asset names unverified). It needs only Java, which is already present.
- Allure 2 generate: `allure generate <dir> --single-file --clean -o allure-report`.
- Allure 3: npm `allure` 3.33.0, needs Node >= 22.18.
  - The core reader has a `junitxml` directory in its source, but the docs never name JUnit XML as an input.
  - It auto-detects directories named `allure-results`.
  - `singleFile` is an Awesome-plugin option.
- **Recommendation:** Allure 2.46.2 (no Node). Spike on a real run first, and fall back to Allure 3 with `actions/setup-node` if the output is poor.

### 2. Actions (as of 2026-11, from the READMEs)

| Action | Notes |
|---|---|
| `actions/checkout@v7` | `fetch-depth: 0` for gitleaks and the commit-message job |
| `actions/setup-java@v6` | temurin 17. Skip `cache:` when setup-gradle caches |
| `gradle/actions/setup-gradle@v6` | Only default-branch runs write the cache; PR runs read only. Enhanced cache is free for public repos |
| `actions/upload-artifact@v7` | `overwrite` defaults to false, so artifact names must be unique |
| `actions/download-artifact@v8` | `pattern` + `merge-multiple: true` |
| `gitleaks/gitleaks-action@v3` | No license needed for personal accounts. Set `GITLEAKS_ENABLE_COMMENTS: false`, pass `GITHUB_TOKEN` |
| actionlint | Official download script (pin a version) or `docker://rhysd/actionlint:<tag>` (bundles shellcheck) |
| `marocchino/sticky-pull-request-comment@v3` | `header` updates the same comment. Needs `pull-requests: write` |
| `peaceiris/actions-gh-pages@v4` | `destination_dir` (beta) + `keep_files: true` give per-PR folders. `actions/deploy-pages` replaces the whole site, so it cannot keep per-PR folders |

### 3. GitHub Pages from a gh-pages branch

- `POST /repos/{owner}/{repo}/pages` with `{"source":{"branch":"gh-pages","path":"/"}}`. The branch must exist first.
- Limits:
  - 1 GB published site, so a per-PR cleanup job is needed.
  - 100 GB a month of bandwidth (soft limit).
  - A 10-minute deploy timeout.
  - **Branch-based builds have a soft limit of 10 builds an hour.**
- URL format `https://OWNER.github.io/REPO/<folder>/` and the propagation delay are unverified.

### 4. Closing keywords

- A keyword in a PR body only works when the PR targets the default branch. Here PRs target `dev`, so the keywords are ignored and no link is created.
- A keyword in a **commit message** closes the issue once that commit is merged into the default branch.
- Whether a direct fast-forward push to `main` triggers the close is undocumented. Third-party guides say it does. **Test it with a throwaway issue.**
- Squash merge can include the PR body when the format "Pull request title and description" is selected. The API field names are unverified.
- Design implication: the keyword must end up in a commit message on `dev`, through the merge message setting or in the commits themselves.

### 5. Android SDK and Robolectric

- The Ubuntu 24.04 runner image has platforms android-37.0/37.1/37.2 and 36/35/34, and build-tools 37.0.0. Pin `ubuntu-24.04`.
- Robolectric 4.17 added SDK 37 support (4.18 is the latest).
- **Orchestrator check:** this project has `targetSdk = 36` (37 is only `compileSdk`), and 9 test classes pin `@Config(sdk = [34])`. Robolectric never runs SDK 37, so **no Robolectric bump is needed**. The 102 tests pass locally on JDK 17.

### 6. Rulesets and check-runs

- `PUT /repos/{owner}/{repo}/rulesets/{id}` needs the full `rules` array. GET it, append, PUT, then verify.
- Rule: `{"type":"required_status_checks","parameters":{"required_status_checks":[{"context":"ci-gate","integration_id":<actions app id>}],"strict_required_status_checks_policy":<bool>}}`. Read the app id from a real check run's `app.id` (15368 is expected but unverified).
- `context` must equal the job `name`.
- `GET .../commits/{ref}/check-runs` supports `check_name`, `filter=latest`, and `per_page` up to 100.

## Changes to the exploration

1. The Robolectric/SDK 37 risk is ruled out (targetSdk 36 and an explicit sdk 34). SDK 37 is preinstalled on the runner anyway.
2. Pages from a branch has a soft limit of 10 builds an hour. Accept it for a solo repo and keep cleanup commits batched.
3. Allure 2.46.2 is the default after a spike. Allure 3 needs Node 22.18+.
4. `promote.yml` can query check-runs with `check_name=ci-gate`.

## Gaps (resolved by spikes in the first apply batch)

- Allure 2 JUnit XML defaults and asset names.
- Allure 3 JUnit XML input.
- Fast-forward closing (throwaway issue).
- The Actions `integration_id`.
- Pages URL and propagation delay.
- The merge-message API field names.
