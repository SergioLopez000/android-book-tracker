# Proposal: Firebase Cloud Sync

## Intent

Books live only in an on-device Room database. Users need multi-device sync, cloud backup and shared book lists. Firebase Auth + Cloud Firestore (Spark plan, no Cloud Functions) becomes the single source of truth, using its offline persistence. The old Spring Boot `backend/` is replaced by Firebase and is deleted in this change.

## Scope

### In Scope
- Build spike: Firebase BoM, google-services plugin on AGP 9.5.3, committed placeholder `google-services.json`.
- `Book.id` from `Long` to String (UUID) across domain, navigation, UI tags and e2e.
- Required sign-in with Google (Credential Manager) and email/password, including "forgot password" (password reset email); sign-out clears the Firestore cache and warns before discarding unsynced writes.
- Firestore adapter for `BookRepository` (client-side filter/sort, last-write-wins, non-blocking offline writes); Room, KSP and Room tests removed.
- Shared lists: book copies, equal members (any member edits books, invites, removes members, deletes the list), `review` visible, invite codes (default 7 days / 10 uses, maximum 7 days / 50 uses). A list with no members left is deleted automatically.
- Security rules with size limits (title/author 300, review 5000, list name 100), App Check (Play Integrity), emulator rules tests gating `ci-gate` (job `rules-tests`).
- In-app account and data deletion (sole-member lists deleted; copies the user added to other lists stay, without identifying data).
- e2e test-only in-memory auth and repository wiring (offline) through the `e2e` build type.
- Delete `backend/` and its tooling: freeze guard scripts, lefthook jobs, image smoke and secrets-contract tests that only serve the backend, docs, issue templates and root build references.

### Out of Scope
- Room data import (no users, #90).
- Anonymous auth, App Links, Cloud Functions, roles or ownership, member cap.
- Invite-code revoke UI (expiry suffices).
- Email verification.
- Reviving or reusing the old backend.

## Capabilities

### New Capabilities
- `user-auth`: required sign-in, Google and email/password, password reset, sign-out with cache clear and unsynced-write warning.
- `cloud-book-sync`: per-user Firestore storage, offline persistence, last-write-wins.
- `shared-book-lists`: lists, copies, equal members, invite codes, cascade deletion.
- `account-deletion`: in-app deletion of account and data.
- `firestore-security-rules`: rules, size limits, App Check, emulator tests.

### Modified Capabilities
- `book-library`: String ids; persistence no longer Room.
- `book-entry`: String ids; persistence semantics.
- `e2e-testing`: id tag pattern; test-only auth/repository wiring (`e2e` build type).
- `ci-pipeline`: `rules-tests` job gating `ci-gate`; placeholder config; backend-specific requirements removed.
- `code-quality-gates`: Room-specific coverage exclusions and backend coverage/format/detekt coverage removed.

### Removed Capabilities
- `backend-api`: the entire capability is removed (replaced by Firebase, firebase-cloud-sync).

## Approach

Keep Firebase behind ports in `domain/port` with adapters in `data/*` (domain stays Android-free). Thin remote interfaces allow JVM tests under Strict TDD. Slices: 0 build spike (stop and re-plan on failure); 1 string-id refactor; 2 auth; 3 Firestore adapter, rules, emulator tests, DI switch, Room removal; 4 shared lists (likely split into model/rules and UI/join); 5 account deletion, backend deletion, docs, CI/e2e rework. Slices 1 and 4 may exceed 800 lines, so chained PRs are likely (decided at tasks). Issue-first workflow and conventional commits apply.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `app/src/main/.../domain` | Modified | String id, auth/list ports |
| `app/src/main/.../data/local` | Removed | Room |
| `app/src/main/.../data` | New | Firebase adapters |
| `app/src/main/.../di`, `MainActivity` | Modified | Wiring, navigation |
| `app/src/main/.../ui` | Modified/New | Ids, sign-in, password reset, lists, deletion |
| `firestore.rules`, rules tests | New | Rules + emulator tests |
| `.github/workflows/ci.yml` | Modified | Gating `rules-tests` job |
| `backend/` | Removed | Old Spring Boot backend and its Gradle build |
| `scripts/`, `lefthook.yml` | Removed/Modified | Backend freeze guard scripts, backend-only image smoke and secrets-contract tests, lefthook jobs |
| `.github/dependabot.yml`, issue templates, root `settings.gradle.kts`/Spotless/detekt references | Modified/Removed | Backend references |
| `e2e/` | Modified | Tags, test wiring |
| `openspec/config.yaml`, `openspec/specs/backend-api`, docs | Modified/Removed | Stack, setup, backend docs |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| google-services plugin incompatible with AGP 9.5.3 | Med | Slice 0 spike; stop and re-plan |
| Any member can remove others or delete a list (abuse, data loss) | Med | Document; copies protect personal books |
| Spark quota exhaustion by a hostile member | Med | App Check, size limits |
| Offline `save()` hangs on server ack | Med | Do not await ack; verify in spike |
| Cross-user cache leak | Low | Clear persistence on sign-out |
| Test-device data loss | High | Accepted (no users) |
| Slices over budget | High | Chained PRs |
| Backend deletion leaves dangling references in tooling | Med | Explicit removal requirements and grep-based scenarios |

## Rollback Plan

Revert slice PRs in reverse order. Until slice 3 merges, Room remains active; after it, reverting slice 3 restores Room and KSP. The backend deletion is a single revertable commit range. Firebase project config is external and can be disabled.

## Dependencies

- Firebase project with Auth and Firestore, SHA-1 registered, API key restricted.
- Firebase Emulator Suite in CI.

## Success Criteria

- [ ] Signed-in user sees the same books on two devices and offline.
- [ ] Members share list books per the equal-member model.
- [ ] Account deletion removes auth user and data.
- [ ] Rules tests gate `ci-gate`; unit and e2e suites stay green.
- [ ] `backend/` and its tooling are gone and no workflow, hook, script, doc or build file references it.
