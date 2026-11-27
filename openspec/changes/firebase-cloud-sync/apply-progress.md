# Apply progress: firebase-cloud-sync

## Slice B (backend deletion): B.1.1-B.3.1 complete; B.3.2 (commit/PR) left to the orchestrator
- RED: `test -e backend` succeeded; workflow grep empty; `backend/` grep listed 8 hits (README, build.gradle.kts, .gitignore, 3 scripts, kover fixture, image smoke). Secrets contract scripts green at baseline.
- GREEN: removed `backend/`, 3 backend-only scripts; edited lefthook.yml, build.gradle.kts, .gitignore, issue templates, docs/secrets.md (GOOGLE_BOOKS_API_KEY row untouched), README.md, openspec/config.yaml.
- Checks: absence checks pass; actionlint, shellcheck, secrets/e2e/kover scripts, `:app:testDebugUnitTest`, `spotlessCheck`, detekt CLI all exit 0 (no `detekt` Gradle task exists; it is the brew CLI).
