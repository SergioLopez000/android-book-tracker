# Exploration: freeze-backend (#91)

## Context

On 2026-11-25 the scope changed: the app is local-only, with no users (#26, #90). The Spring Boot backend (#6) has no current purpose:

- The app never calls it. `app/` contains no backend URL, no `10.0.2.2`, no `/api/books` and no `BASE_URL`.
- It is not deployed (#8).
- It answers 401 to everything except `/actuator/health`, because no production authenticator exists.

The goal is to keep `backend/` untouched while disabling everything that builds, tests, scans, publishes, promotes or updates it, so that the change can be undone by reverting it.

## Touchpoints

### Workflows

- `.github/workflows/ci.yml`:
  - Header (lines 1–7).
  - `backend-tests` (lines 58–96) runs `test integrationTest verifyPackagedArtifact` and Kover with Testcontainers. It uploads `allure-results-backend` and `coverage-backend`.
  - `contract-tests` (lines 98–142) runs `contractTest pitest` and Kover. It uploads `allure-results-contract`, `coverage-contract` and `pit-contract`.
  - `report`:
    - Its `needs` (line 260) lists `unit-tests`, `backend-tests`, `contract-tests` and `e2e-tests`.
    - It aggregates backend and contract results, Kover and PIT (lines 328–365), and its PR comment links to backend Kover (line 406).
    - Its artifact downloads use `continue-on-error: true`, so missing artifacts only show as unavailable.
  - `ci-gate`:
    - Its `needs` (line 422) lists `unit-tests`, `backend-tests`, `contract-tests` and `secret-scan`.
    - It treats any result other than success, including skipped, as a failure (lines 430–433). So `if: false` is not enough: the jobs and their `needs` entries must be removed.
  - `publish-image` (lines 436–474) runs on `dev` pushes only, holds `packages: write`, and pushes `ghcr.io/<repo>/backend:sha-<sha>` and `:dev`.
- `.github/workflows/codeql.yml`:
  - Installs JDK 21 for the backend (lines 33–36).
  - Has a "Build backend (JDK 21)" step (lines 49–53).
- `.github/workflows/promote.yml`:
  - `check-image` (lines 26–59) holds `packages: read` and runs `exit 1` when `backend:sha-<commit>` is missing, which would block every promotion once no image is published.
  - `promote` needs `check-image` and reads its `sha` output (lines 63 and 81). The `INPUT_SHA` fallback (line 83) handles an empty value.
  - `retag-image` (lines 120–155) holds `packages: write` and uses crane to tag `:staging`.
- `token-expiry.yml`, `board-sync.yml` and `pr-cleanup.yml` have no backend references.

### Dependabot

`.github/dependabot.yml`:

- Line 1 is a comment about root and `/backend`.
- Lines 25–47 are the Gradle `/backend` entry.
- Lines 48–61 are the Docker `/backend` entry.

### Hooks and scripts

- `lefthook.yml:20`: the `secrets-contract` glob includes `backend/Dockerfile`. Spotless and detekt also lint staged backend files. Nothing builds the backend locally.
- `scripts/check-secrets-contract.sh`:
  - Lines 7, 19 and 121–144 require `backend/Dockerfile` to exist with a single digest-pinned `FROM` and no secret-like `ENV` or `ARG`.
  - It strips comments before checking jobs, and requires `permissions:` on every job.
- `scripts/test-secrets-contract.sh` uses its own fixtures, so it is unaffected.
- `scripts/test-image-smoke.sh` builds the image locally. It is manual only.
- `scripts/test-image-config.sh` is not in lefthook; README:151 and `openspec/config.yaml:56` reference it. It asserts:
  - that `publish-image` exists, runs only on `dev` pushes and needs `ci-gate` (lines 58–61);
  - that `check-image` exists, reads packages and is needed by `promote` (lines 66–68);
  - the `retag-image` checks (lines 69–74);
  - that `packages: write` is allowed only in `publish-image` and `retag-image` (lines 77–78);
  - the Dockerfile checks (lines 47–54).
- `scripts/test-e2e-ci-config.sh` makes no backend assertions. It only needs `ci-gate` to have `needs:` without `e2e-tests`, and `report` to need `e2e-tests`.

### Root Gradle

`build.gradle.kts:15,20`: Spotless formats `**/*.kt` and `backend/*.gradle.kts`. `settings.gradle.kts` includes only `:app`.

### Docs and specs

- `README.md`:
  - "Backend (Spring Boot)" and "Imagen del backend (GHCR)" (lines 122–151).
  - The hooks paragraph (line 161).
  - "What runs where" (lines 203–207).
  - The pipeline diagram and job list (lines 216–234).
  - The artifact row (line 300).
- `docs/secrets.md` has backend rows for `SPRING_DATASOURCE_*` and `GOOGLE_BOOKS_SERVER_API_KEY`, all planned. The `GOOGLE_BOOKS_API_KEY` row wording is pinned by the secrets contract and must not change.
- `openspec/specs/ci-pipeline/spec.md` describes backend CI as active in these requirements:
  - Unit Test Job
  - Artifact Contract
  - Report Generation and Publishing
  - Single Required Check
  - Out of Scope Boundaries
  - CodeQL Analysis Workflow
  - Dependabot Configuration
  - Backend Test Job
  - Contract Test Job With PIT
  - GHCR Image Publication
  - Promote Retags the Same Image Digest
  - E2E Test Job (one scenario)
  - Static Contract Checks Self-Tested and Run Locally
- `openspec/specs/backend-api/spec.md` describes the backend's own behaviour, which stays true for the frozen code.
- `openspec/specs/code-quality-gates/spec.md` covers formatting, coverage and the pre-commit hook, and mentions the backend.
- `openspec/config.yaml:14-15,56` describes `publish-image` and the backend test commands.

### Docker

Every Docker use belongs to the backend: the image, Testcontainers, the smoke and image-config scripts, the Dockerfile checks, and Dependabot's Docker entry. The app does not use Docker: unit tests run on the JVM with Robolectric, and e2e runs on an Android emulator.

### Related issues

#84 (run the backend image smoke test in CI) no longer applies once the backend is frozen.

## Approach

- Remove `backend-tests`, `contract-tests` and `publish-image` from `ci.yml`, and remove them from the `needs` of `ci-gate` and `report`. Remove the backend and contract aggregation from `report`.
- Remove the backend build and JDK 21 from `codeql.yml`.
- Remove `check-image` and `retag-image` from `promote.yml`, and have `promote` resolve the SHA itself.
- Remove both `/backend` entries from Dependabot.
- Reduce or retire `scripts/test-image-config.sh`. Decide what happens to the Dockerfile checks in `check-secrets-contract.sh`.
- Mark the backend as frozen in the README, `docs/secrets.md`, `openspec/config.yaml` and the living specs, through delta specs.
- Keep `backend/` byte-identical.

## Risks

- `ci-gate` and the promotion would block if any backend `needs` entry or `check-image` survived.
- `test-image-config.sh` asserts the removed jobs.
- `packages:` permission assertions need updating once no job writes packages.
- The `GOOGLE_BOOKS_API_KEY` row wording is pinned by the secrets contract.
- Reverting must restore everything, so the change should stay in one revertible PR.
