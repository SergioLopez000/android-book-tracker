# Apply progress: freeze-backend (#91)

Mode: Strict TDD. Store: hybrid. Delivery: exception-ok (one PR, `size:exception`, D7). Nothing committed or pushed (orchestrator's job).

## Status
Tasks done: 33 of 37 non-post-merge tasks (all except 3.9, 4.7, 5.5 commits and V.8 PR, which belong to the orchestrator). P.1-P.5 not in scope.

## Unit 1 (guard, workflows, Dependabot, lefthook)
- 1.1-1.5 RED: `bash scripts/test-backend-frozen.sh` with no checker: exit 1, "22 check(s) failed" (every case reported `check-backend-frozen.sh: No such file or directory`).
- 2.1 GREEN: checker created; all fixtures pass, only the real-repo case failed.
- 2.2 RED real repo: `check-backend-frozen.sh` listed 65 hits (ci.yml 42, codeql.yml 3, promote.yml 17, dependabot.yml 3).
- 2.3: shellcheck clean.
- 3.1-3.5: ci.yml, codeql.yml, promote.yml, dependabot.yml edited per design; `scripts/test-image-config.sh` deleted (the deletion is unstaged in the worktree); the harness fixture uses `test-image-foo.sh` so the V.7 grep stays empty.
- 3.6 RED: with `# backend` planted in ci.yml and no job, `lefthook run pre-commit --file .github/workflows/ci.yml` passed (actionlint, gitleaks, secrets-contract). GREEN: after adding `backend-frozen`, exit 1 with `FAIL: frozen: ci.yml line 324 (F1 backend)`.
- 3.7 mutations (all reverted): `# backend` in ci.yml (F1), `needs: [check-image]` in promote.yml (F2), `packages: write` in codeql.yml (F3), `/backend` directory (F4), quoted `"docker"` ecosystem (F4), ci-gate-less ci.yml (F0). Each failed naming its rule.
- 3.8: all runners exit 0 (see below).

## Unit 2 (secrets contract)
- 4.1 RED: fixture without `backend/`: `FAIL: Dockerfile: expected a single digest-pinned FROM in backend/Dockerfile` on every pass case, then the script aborted on the missing Dockerfile.
- 4.2-4.4 GREEN: Dockerfile section, header item and variable removed from the checker; Dockerfile fixture block removed from the harness; lefthook glob narrowed; items renumbered (header 1-4 and comments, drift guard now section 3).
- 4.5: no `dockerfile` text in either script or lefthook.yml; `--file backend/Dockerfile` skips `secrets-contract`, a workflow file runs it; mutation `write-all` -> `write-allx` made "write-all fails" fail, reverted; no workflow runs either script.
- 4.6: shellcheck and both runners exit 0.

## Unit 3 (docs)
- README (Spanish): section "Backend (congelado)" with manual commands, smoke script manual only, reactivation and gaps; hook list, tables, diagram, bullets, artifact rows updated; harness added to Comandos; Dependabot retarget section keeps the guidance and adds the by-hand closure for backend security PRs (D6).
- `docs/secrets.md`: 6 rows prefixed `frozen with the backend (#91); `; the GOOGLE_BOOKS_API_KEY row untouched.
- `openspec/config.yaml`: context, backend project removed, contract/mutation `available: false`, verify rules updated.
- 5.4: frozen/congelad found in all three files; `test-image-config` grep empty; both guards green; lefthook pre-commit on the docs passes.

## Final verification
V.1-V.7 done and green (details in the apply report). V.8 is the orchestrator's.

- V.6, re-run by the orchestrator after the phase-contract check: `./gradlew :app:testDebugUnitTest` exit 0; 23 result files, 126 tests, 0 failures.
- `openspec/config.yaml` first context line now says the backend build is frozen (#91), to close the validator note.

## Deviations and notes
- Harness fixture variables are `CI_YML`/`OTHER_YML`/`DEP_YML` (not `CI`) to avoid the `CI` environment variable of real runners.
- `openspec/config.yaml` context first line ("Two independent Gradle builds") left as is.
- Size: tracked diff vs origin/dev 58 insertions, 491 deletions; new untracked: check-backend-frozen.sh 78, test-backend-frozen.sh 201 (code total about 828; specs and SDD artifacts excluded).
