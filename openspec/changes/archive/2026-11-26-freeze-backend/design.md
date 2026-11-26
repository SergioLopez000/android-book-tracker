# Design: Freeze the backend and disable its CI (#91)

## Technical Approach

Pure removal plus one new local guard, in one squash commit to `dev`. `backend/` and `app/` stay byte-identical. A new self-tested static check (`scripts/check-backend-frozen.sh`) proves the freeze and fails while any backend automation survives.

## Architecture Decisions

| Decision | Options | Tradeoff | Choice |
|---|---|---|---|
| Freeze guard | Extend `check-secrets-contract.sh` / new script pair | Extending reuses helpers but puts CI-scope and Dependabot rules in a secrets script whose glob omits `dependabot.yml` | New `check-backend-frozen.sh` + `test-backend-frozen.sh`, own lefthook job, never in CI ("No Static Analysis in CI") |
| `test-image-smoke.sh` | Delete / keep | Deleting breaks the `backend-api` spec reference and makes reactivation harder | Keep as a manual backend-only tool; no hook, workflow or verify rule references it |
| `scripts/pit-summary.sh` (+ harness) | Delete / keep | Unused after the freeze, but restored usage on revert | Keep, dormant |
| `report` PIT download | Keep / remove | `pit-*` had only one producer (`contract-tests`) | Remove the step |
| Byte-identity proof | In guard / in verify | The guard is git-free; a base comparison is meaningless after merge | Verify command only (below) |
| Local hooks (D1) | Narrow Spotless/detekt globs / keep | They lint staged files and never build or test the backend | Confirmed: unchanged |

## File Changes

| File | Action | Exact edits |
|---|---|---|
| All workflows | Rule | No text, comments included, may contain `backend`, `contract-tests`, `publish-image`, `check-image`, `retag-image`, `ghcr.io`, `docker/login-action` or `packages: write`. Comments say "server module frozen (#91), see README" |
| `.github/workflows/ci.yml` | Modify | Header (1–7) rewritten: "Runs the app unit tests, the non-gating Appium e2e suite on an emulator and the secret scan on every pull request and dev push, then publishes Allure and Kover reports to gh-pages. The server module is frozen (#91), see README." followed by the existing quality-checks and `ci-gate` sentences. Delete `backend-tests` and `contract-tests` (58–143) and `publish-image` with its comment (436–474). `report.needs: [unit-tests, e2e-tests]`; delete "Download PIT report" (293–298). In "Build report site": comment 328 becomes "Coverage figure for the Android app"; delete 341, 343, 345–357, 363–364; line 358 becomes `coverage="app ${app_coverage}"`. In "Compose PR comment" delete 406–407. `ci-gate.needs: [unit-tests, secret-scan]` |
| `.github/workflows/codeql.yml` | Modify | `java-version: "17"` (drop the 33 comment and `21`); delete "Build backend (JDK 21)" (49–53); header becomes "CodeQL analysis for the Android app (Kotlin). The server module is frozen (#91) and not compiled, see README." plus the existing ruleset sentence. Remaining steps: checkout, setup-java 17, setup-gradle, init, Build app, analyze |
| `.github/workflows/promote.yml` | Modify | Delete `check-image` (26–60) and `retag-image` (119–155). `promote`: drop `needs` and `outputs`; Resolve commit env `INPUT_SHA: ${{ inputs.sha }}` (existing `rev-parse --verify` and ancestor checks unchanged). Permissions stay `contents: write`, `checks: read`; no `packages:` anywhere. Header (1–7) rewritten as whole sentences, not truncated: "Promotes the commit verified in the dev ring to staging by fast-forwarding `main` to it. The `staging` environment requires QA approval, so the job waits until a reviewer approves the deployment. Fast-forward keeps the exact SHA tested in dev, so nothing is rebuilt." |
| `.github/dependabot.yml` | Modify | Line 1: "Version updates target dev."; delete both `/backend` entries (25–61) |
| `scripts/check-secrets-contract.sh` | Modify | Delete header item 3, `dockerfile=` (19), section 3 (121–143); renumber items 4→3, 5→4 |
| `scripts/test-secrets-contract.sh` | Modify | Delete `DIGEST`, `backend` in `mkdir`, the Dockerfile `printf` (65) and "--- Dockerfile ---" (136–159). Clean fixture has no `backend/` |
| `scripts/test-image-config.sh` | Delete | D3 |
| `scripts/check-backend-frozen.sh`, `scripts/test-backend-frozen.sh` | Create | Guard + harness (contract below) |
| `lefthook.yml` | Modify | `secrets-contract` glob drops `backend/Dockerfile`; new job `backend-frozen`, glob `{.github/workflows/*.{yml,yaml},.github/dependabot.yml,scripts/*backend-frozen*.sh}`, run `bash scripts/test-backend-frozen.sh` |
| `README.md` (Spanish) | Modify | Replace 122–151 with "## 🧩 Backend (congelado)": code kept, nothing in CI/CodeQL/Dependabot/GHCR, manual commands, `test-image-smoke.sh` manual only, reactivation and its gaps. Line 161: drop `backend/Dockerfile`, add `backend-frozen`. Delete rows 203, 204, 207; diagram 214–222 without backend jobs; delete 226, 228; update 229, 231, 232, 234; artifact row 300 `(unit)`; delete 302. Add the harness to the Comandos table |
| `docs/secrets.md` | Modify | Status of the `SPRING_DATASOURCE_*`, `NEON_HOST_CREDENTIALS`, `GOOGLE_BOOKS_SERVER_API_KEY`, `JWT_SIGNING_SECRET` rows gets the prefix `frozen with the backend (#91); `. The `GOOGLE_BOOKS_API_KEY` row is byte-identical |
| `openspec/config.yaml` | Modify | Context 10–15: backend frozen since #91, revert to reactivate, no image publication. Remove the `backend` testing project; `contract`/`mutation` `available: false` (frozen). Verify: delete line 56, drop "secret-like Dockerfile ENV/ARG" from 57, add `bash scripts/test-backend-frozen.sh` |

## Interfaces / Contracts

`scripts/check-backend-frozen.sh [root]`: awk/grep only, no git or network. It scans the full workflow text, comments included, to match the spec scenario "No workflow touches the backend". Matching is case-insensitive. It prints one `FAIL: frozen: <file> line <n> (<rule>)` per hit and exits non-zero on any failure.

| Rule | Scope | Fails on |
|---|---|---|
| F0 non-vacuity | root | no workflow files; `ci.yml` without a `ci-gate` job; `dependabot.yml` missing or without `package-ecosystem:` |
| F1 | workflows | `backend` |
| F2 | workflows | `backend-tests`, `contract-tests`, `publish-image`, `check-image`, `retag-image` (as job or `needs`) |
| F3 | workflows | `packages:`, `ghcr.io`, `docker/login-action`, `test-image-` |
| F4 | `dependabot.yml` | `/backend` as a `directory`/`directories` value; any `package-ecosystem: docker` entry, quoted or not |

Harness (`mktemp` fixtures, style of `test-secrets-contract.sh`). Fixtures are temporary roots, not real workflows.

| Case | Expected |
|---|---|
| Clean root | Passes |
| Clean root with a `# server module frozen (#91)` comment | Passes (positive control) |
| Fixture with a `# backend` comment only | Fails F1, which proves comments are scanned |
| One fixture per rule | Fails: F0 three times (empty workflows, no dependabot, no `ci-gate`), `promote` needing `check-image`, a `docker/login-action` step, `directory: /backend`, `package-ecosystem: "docker"` |
| Real repository | Passes |

## Testing Strategy (Strict TDD)

| Step | RED | GREEN |
|---|---|---|
| Guard | Harness before the checker exists fails; with the checker, the real-repo case fails listing every backend reference | Workflow and Dependabot edits |
| Secrets contract | Fixture root without `backend/Dockerfile` fails the current checker | Delete section 3 |
| Regression | — | `actionlint`, `test-secrets-contract.sh`, `test-e2e-ci-config.sh`, `test-pit-summary.sh`, `test-kover-summary.sh`, `shellcheck` |
| Byte identity | — | `git diff --stat origin/dev...HEAD -- backend/ app/` prints nothing (`main` lags `dev`) |
| Live | — | `ci-gate` green on the PR; after merge, one `promote.yml` dispatch on `dev` (QA) |

## Threat Matrix

| Boundary | Applicability | Design response | Planned RED tests |
|---|---|---|---|
| Documentation-like paths | N/A: no file classification | — | — |
| Git repository selection | N/A: no new git invocation | — | — |
| Commit state | N/A | — | — |
| Push state | Applicable: `promote` pushes `${SHA}:refs/heads/main` with the SHA now from `inputs.sha` | Same `rev-parse --verify`, dev-ancestor, fast-forward and `ci-gate` checks; empty input falls back to `origin/dev` | Guard fixture: `promote` needing `check-image` fails (F2); actionlint rejects dangling `needs` |
| PR commands | N/A | — | — |

## Migration / Rollout

Reactivation is `git revert <squash-sha>` in a PR to `dev`. That also removes the guard, which is why the guard and the removals ship together. A revert does NOT restore:

- Dependabot PRs #66, #67 and #69. They stay closed, and new ones arrive on the next weekly run.
- Images for freeze-period commits. Promotion needs a post-revert `dev` push.
- `:staging` while frozen. It keeps the last pre-freeze digest.
- Backend CodeQL alerts closed by the narrower analysis. They come back on the next analysis.
- Expired Gradle caches.

Manual steps after the merge:

- Issue #84 stays open (D4).
- Close #66, #67 and #69 with a comment linking #91 (D5).
- D6 (user, 2026-11-26): security updates stay enabled. Any security-update PR for backend manifests is closed by hand with a comment linking #91. No Dependabot `ignore` entry is added.

## Risks

- About 900–1000 changed lines, specs included. That is over 800, but about 60% is deletions. D1 requires one revertible PR, so `sdd-tasks` should ask for a single PR with a size exception.
- Security-update PRs for backend manifests may keep arriving after the `/backend` entries are removed (D6). They are handled by hand.

## Open Questions

None blocking.
