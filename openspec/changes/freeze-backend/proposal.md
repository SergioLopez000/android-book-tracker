# Proposal: Freeze the backend and disable its CI (#91)

## Intent

Since 2026-11-25 the app is local-only, with no users (#90). The backend is never called or deployed, yet CI still builds, tests, scans, publishes and promotes it. Freeze it: keep the code, stop the automation, and make reactivation one revert.

## Scope

### In Scope
- Remove backend CI, CodeQL build, image promotion and Dependabot entries.
- Remove the Dockerfile checks and fixtures from the secrets contract (D2); retire `scripts/test-image-config.sh` (D3).
- Mark the backend frozen in README, `docs/secrets.md`, `openspec/config.yaml` and living specs.

### Out of Scope
- Any byte under `backend/` or `app/`.
- Deleting GHCR images (free, restorable history).
- #84 (stays open, deferred, D4).
- Changing the local Spotless and detekt hooks on `backend/`. They only run on staged edits.

## Capabilities

### New Capabilities
None

### Modified Capabilities
- `ci-pipeline`: REMOVED Backend Test Job, Contract Test Job With PIT, GHCR Image Publication, Promote Retags the Same Image Digest, No Secret-Like Dockerfile Variables. MODIFIED Unit Test Job, Artifact Contract, Report Generation and Publishing, Single Required Check, Out of Scope Boundaries, CodeQL Analysis Workflow, Dependabot Configuration, E2E Test Job, Static Contract Checks Self-Tested and Run Locally.
- `backend-api`: Purpose-only frozen note. Its requirements describe the code, which stays true.
- `code-quality-gates`: reviewed, no change. Its backend mentions describe the untouched build and the local hooks.

## Approach

| Touchpoint | Change |
|------------|--------|
| `ci.yml` | Delete `backend-tests`, `contract-tests`, `publish-image`; `ci-gate` keeps `unit-tests` and `secret-scan`, `report` keeps `unit-tests` and `e2e-tests`; drop backend/contract/PIT aggregation and the Kover link; update header |
| `codeql.yml` | Delete the JDK 21 and backend build steps |
| `promote.yml` | Delete `check-image`, `retag-image`; `promote` resolves the SHA itself |
| `dependabot.yml` | Delete both `/backend` entries and the comment |
| `check-secrets-contract.sh`, `test-secrets-contract.sh` | Delete Dockerfile checks and their fixtures |
| `test-image-config.sh` | Delete it and its references |
| `lefthook.yml` | Drop `backend/Dockerfile` from the `secrets-contract` glob |
| Docs | Frozen notes; `GOOGLE_BOOKS_API_KEY` row untouched |

Reactivation: the PR is squash-merged into one commit, so reverting that commit restores everything.

## Affected Areas

| Area | Impact |
|------|--------|
| `.github/workflows/{ci,codeql,promote}.yml`, `.github/dependabot.yml` | Modified |
| `scripts/check-secrets-contract.sh`, `scripts/test-secrets-contract.sh`, `lefthook.yml` | Modified |
| `scripts/test-image-config.sh` | Removed |
| `README.md`, `docs/secrets.md`, `openspec/config.yaml` | Modified |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| A surviving backend `needs` or `check-image` blocks `ci-gate` or promotion | Med | actionlint; grep for removed job names |
| `test-e2e-ci-config.sh` expectations break | Low | Keep its `ci-gate`/`report` wiring; run it |
| `test-secrets-contract.sh` fails after fixture removal | Med | Run it |
| Pinned `GOOGLE_BOOKS_API_KEY` wording drifts | Low | Edit only backend rows; drift guard |
| Diff exceeds the 800-line budget | Low | Mostly deletions; single PR |

## Rollback Plan

`git revert` the squash commit. No data changes. The existing GHCR images remain, and the next `dev` push publishes a new one.

## Dependencies

- Post-merge: close Dependabot PRs #66, #67, #69 with a comment linking #91 (D5).

## Success Criteria

- [ ] `git diff --stat origin/dev...HEAD -- backend/ app/` is empty (compared against `dev`, since `main` lags behind it).
- [ ] No workflow builds, tests, scans, publishes or promotes the backend; `ci-gate` passes on the PR.
- [ ] Dependabot has no `/backend` entry; #66, #67, #69 closed.
- [ ] actionlint, `test-secrets-contract.sh` and `test-e2e-ci-config.sh` pass.
- [ ] Docs and specs mark the backend frozen and revertible.
