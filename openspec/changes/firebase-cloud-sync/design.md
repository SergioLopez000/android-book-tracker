# Design: Firebase Cloud Sync

## Technical Approach

Firestore (Spark, offline persistence) replaces Room as the single source of truth. Firebase types stay in `data/firebase` behind domain ports and thin `*Remote` interfaces, so domain, use cases, ViewModels and adapters stay pure-JVM testable. One `BookRepository` implementation serves personal and shared-list collections (path-parameterized). Auth gates the NavHost. The `e2e` build type swaps the cloud graph for in-memory adapters. Scope change (supersedes the proposal's "backend stays frozen"): the Spring Boot `backend/` is deleted in this change.

## Architecture Decisions

| # | Topic | Choice | Rejected | Rationale |
|---|---|---|---|---|
| D1 | Port boundary | Ports `BookRepository` (String ids), `AuthGateway`, `SharedListRepository`, `AccountDeletion`; adapters wrap `BooksRemote`/`ListsRemote` (map DTOs) | Firebase types in use cases | Strict TDD on JVM |
| D2 | Id generation | `SaveBookUseCase(repo, newId = { UUID.randomUUID().toString() })` | Firestore auto-id | Deterministic tests; valid offline |
| D3 | Query | One listener per collection; pure `filterAndSort` in `domain/model/BookQuery.kt`. DATE desc → title asc → id asc; RATING desc → title asc → id asc (single tie-break rule, per spec cloud-book-sync) | Server queries + indexes | No indexes; identical offline |
| D4 | Offline writes | `set`/`delete` fire-and-forget; failure listener logs | Await ack | Ack never resolves offline |
| D5 | uid scoping | `authState.flatMapLatest { SignedIn -> observe(path(uid)); else -> emptyList }` | Graph per user | Reactive user switch |
| D6 | Sign-out | `awaitPendingWrites(3s)`; timeout → `PendingWritesWouldBeLost` (warning + confirm, P4), then `signOut()` → `terminate()` → `clearPersistence()` | Silent clear | No cross-user leak, no silent loss |
| D7 | Membership index | Collection-group query `members where uid == me` + index override + collection-group read rule | `users/{uid}/memberships` mirror | Remover cannot write another user's mirror |
| D8 | Empty lists (P2) | Any operation leaving zero members (leave, self-removal, account deletion) runs the list cascade instead | `memberCount` in rules | Simple rules; concurrent-leave orphan accepted (inaccessible) |
| D9 | Test wiring | `open fun cloudServices()` in `LibrosLeidosApp`; `e2e` build type (`initWith(debug)`, task `assembleE2e`) with `src/e2e/E2eApp` selected by `android:name="${appClass}"`; Robolectric `@Config(application = SmokeTestApp::class)` | Emulator e2e; runtime flag | Offline e2e; no test code in release |
| D10 | Config | Placeholder `app/google-services.json` (`demo-libros`, non-`AIza` key); real files gitignored in `app/src/{debug,release}/` | CI secret | Forks/Dependabot build |
| D11 | Navigation | `edit?bookId={bookId}`, nullable `StringType`; remove `NO_BOOK_ID`. Unknown id → VM `NotFound` state (message + back), no crash | Sentinel | Native nullable arg |
| D12 | Shared books | Book schema, no user-identifying field; `BookEditViewModel` reused via `bookRepositoryFor(listId)` | `addedBy` | No PII left after deletion (P3) |
| D13 | Join | One online transaction: read invite, validate, `uses+1`, create member. Offline → "Joining needs a connection"; last-use race loser (denied/aborted) → "This invite is no longer valid" | Two writes | Atomic; rules check `getAfter` |
| D14 | Invite ownership | No `createdBy`; creator batch also writes private `users/{uid}/invites/{code}` `{listId}` | `createdBy` on invite | No PII on readable invites; deletion can find own invites |
| D15 | App Check | Enforced in the console (manual checklist in README); Play Integrity in release, debug provider in debug/e2e | Rules-level | Rules cannot verify App Check |

## Data Flow

    FirebaseAuth ─callbackFlow─> AuthGateway.authState ─> NavHost gate
                                        │ flatMapLatest
    Firestore listener ─> BooksRemote ─> FirestoreBookRepository ─> filterAndSort ─> UseCases ─> VMs

## Firestore Layout and Rules (`rules_version = '2'`)

- `users/{uid}/books/{bookId}` `{title, author, coverUrl|null, rating, review, dateFinishedEpochDay, updatedAt}`; `users/{uid}/invites/{code}` `{listId}`
- `lists/{listId}` `{name, createdAt}`; `.../members/{uid}` `{uid, displayName, joinedAt, inviteCode|null}`; `.../books/{bookId}`
- `invites/{code}` `{listId, expiresAt, maxUses, uses}`; code = 12 chars Crockford base32, `SecureRandom`. Default 7 days / 10 uses (P1).

Rules (`firebase/firestore.rules`):
- `users/{uid}/**`: owner only; books pass `validBook()`: `keys().hasOnly`, title/author ≤ 300, review ≤ 5000, coverUrl ≤ 2048 or null, rating 0.5–5 in 0.5 steps, int epoch day, `updatedAt == request.time`.
- `match /{path=**}/members/{m}`: `allow read: if signedIn() && resource.data.uid == request.auth.uid`.
- `lists/{listId}` create: `keys().hasOnly(['name','createdAt'])`, name string 1–100, `createdAt == request.time`, `existsAfter(member(listId, uid))`. Read/update (same name check)/delete: `isMember`.
- Member create: `m == request.auth.uid`, `keys().hasOnly(['uid','displayName','joinedAt','inviteCode'])`, `uid == m`, displayName string 1–100, `joinedAt == request.time`; plus (a) creator `!exists(list) && existsAfter(list)`, `inviteCode == null`; or (b) join: invite matches `listId`, unexpired, `uses < maxUses`, `getAfter(invite).uses == uses + 1`. Read/delete: `isMember`; update denied.
- List books: `isMember` + `validBook`. Invites: `get` signed-in; `list` only with `listId ==` for members; create by member, `uses == 0`, `1 ≤ maxUses ≤ 50`, `expiresAt ≤ request.time + duration.value(7,'d')`; update only `uses+1` with `!exists(member) && existsAfter(member)`; delete by member.

## List Cascade

Order (each step a ≤ 500-op batch, online, idempotent): list books → invites (`listId ==`) → other members' docs → list doc → own member doc. Own member doc is last so `isMember` holds throughout. Used by delete-list and by D8.

## Account Deletion

1. Reauthenticate (or accept `auth_time` ≤ 5 min). Declined/failed → abort; nothing deleted.
2. Delete own invites via `users/{uid}/invites` (while still a member).
3. Per membership: sole member → cascade (P3); else delete own member doc.
4. Batch-delete `users/{uid}/**` and `users/{uid}`.
5. `currentUser.delete()` last; then terminate + clearPersistence. Interrupted runs resume after sign-in.

## File Changes

| Path | Action |
|---|---|
| `domain/{model,port,usecase}/*` | Modify/Create |
| `data/firebase/*` | Create |
| `data/local/*`, Room tests, KSP, `app/schemas/` | Delete (3c) |
| `di/AppContainer.kt`, `LibrosLeidosApp.kt`, `MainActivity.kt`, `ui/{auth,lists,account,list,edit}/*` | Modify/Create |
| `app/src/main/AndroidManifest.xml` | Modify: `android:name="${appClass}"` |
| `app/src/e2e/**`, `app/src/test/.../SmokeTestApp.kt` | Create |
| `firebase.json`, `firebase/{firestore.rules,firestore.indexes.json,package.json,tests/*}` | Create |
| `gradle/libs.versions.toml`, `app/build.gradle.kts`, `.gitignore`, `.github/workflows/ci.yml` | Modify |
| `e2e/src/pages/tags.ts` | Modify: `LIST_ITEM_PATTERN` `^book_list_item_([A-Za-z0-9_-]+)$`, `LIST_ITEM_SELECTOR_REGEX`, `item(id: string)`, `removeButton(id: string)` |
| `README.md`, `openspec/config.yaml` | Modify: stack, Firebase setup, App Check checklist; remove frozen-backend sections and the `test-backend-frozen.sh` rule |
| `backend/**` (63 files) | Delete (slice B) |
| `scripts/check-backend-frozen.sh`, `scripts/test-backend-frozen.sh`, `scripts/test-image-smoke.sh` | Delete (backend-only) |
| `lefthook.yml` | Modify: drop `backend-frozen` command |
| `build.gradle.kts` | Modify: drop `backend/*.gradle.kts` Spotless target |
| `.gitignore` | Modify: drop `/backend/build/` |
| `.github/ISSUE_TEMPLATE/{chore,feature}.yml` | Modify: drop `backend` option |
| `docs/secrets.md` | Modify: drop `SPRING_DATASOURCE_*`, `NEON_HOST_CREDENTIALS`, `JWT_SIGNING_SECRET` rows; keep `GOOGLE_BOOKS_SERVER_API_KEY` row marked retired (checker requires the `GOOGLE_BOOKS_API_KEY` row's superseded wording) |
| `scripts/test-kover-summary.sh`, `scripts/test-secrets-contract.sh` | Unchanged: `backend` appears only in self-contained fixtures |

Verified by grep: no workflow or `dependabot.yml` entry references `backend/`.

Build: Firebase BoM (auth, firestore, appcheck-playintegrity, appcheck-debug), google-services 4.6.0, credentials + googleid, coroutines-play-services. CI: job `rules-tests` (Node + JDK, pinned firebase-tools, `firebase emulators:exec --only firestore --project demo-libros "npm test"`) in `ci-gate.needs`; e2e builds `assembleE2e`; Kover drops `*_Impl` exclusions.

## Testing Strategy

| Layer | What | Approach |
|---|---|---|
| Unit | filterAndSort, mappers, adapters, invite/join, sign-out, cascade order, deletion (reauth-first, abort leaves data), not-found VM | Fakes of remotes/`AuthGateway`; Turbine |
| Robolectric | Auth gate, smoke | `SmokeTestApp` |
| Rules | Cross-user, list/member create shape, collection-group read, join/replay/expiry/last use, limits, cascade permissions | Emulator, `rules-tests` |
| E2E | Lifecycle with string ids | `assembleE2e`, in-memory |

## Threat Matrix

N/A — no git/PR automation, executable-file classification or dynamic shell composition; `rules-tests` runs fixed commands.

## Migration / Rollout and Slice Plan

No data migration (data loss accepted). Room v2 with String PK and destructive fallback from 1a until 3c. Interim state: after 2a/2b, books stay in device-local Room, not uid-scoped (any signed-in user on the device sees them); uid scoping starts in 3b. Accepted (no users).

| Slice | Content | Reviewed lines |
|---|---|---|
| B | Delete `backend/` + backend-only scripts; edit references above. Independent, can land first | ~120 (pure deletions ~3300+ reported separately) |
| 0 | Spike: BoM, plugin on AGP 9.5.3, placeholder; failure stops and re-plans | ~100 |
| 1a / 1b | String ids domain+data / UI, nav, not-found, e2e tags | ~450 / ~380 |
| 2a / 2b | AuthGateway, gate, `e2e` build type, manifest / sign-in UI, password reset (P6) | ~480 / ~450 |
| 3a / 3b / 3c | Users rules + `rules-tests` / Firestore adapter, DI, sign-out / Room removal | ~400 / ~600 / ~500 (deletions separate) |
| 4a / 4b / 4c | List/member rules + tests + domain / list adapter, cascade, UI / invites, join | ~700 / ~650 / ~550 |
| 5 | Account deletion + docs/config | ~550 |

Order: B independent; 0 → 1a → 1b → 2a → 2b → 3a → 3b → 3c → 4a → 4b → 4c → 5. Chained PRs.

## Open Questions

- [x] Spike: google-services 4.6.0 on AGP 9.5.3; offline `set()` returns without ack. Resolved in slice 0: the plugin applies on AGP 9.5.3 (debug and release, `assembleDebug`/`assembleRelease` green); against the emulator an offline `set()` returns an incomplete Task, is readable from cache at once and is acknowledged after `enableNetwork()` (`FirestoreOfflineSpikeTest`). Firebase BoM 35.2.0. Emulator needs JDK 21 (firebase-tools 15.x); Gradle stays on JDK 17.
- [x] Emulator: `list` rule on `invites` with `resource.data.listId`; fallback `lists/{listId}/invites/{code}` + lookup doc. Resolved in slice 0: no fallback needed. With `allow list: if isMember(resource.data.listId)` (membership via `exists(.../lists/$(listId)/members/$(uid))`), a member query `where listId == X` is allowed and returns only that list's invites; a non-member, a member querying another list, and an unconstrained query are denied; `get` by code works for any signed-in user and is denied unauthenticated. 6/6 cases passed with `@firebase/rules-unit-testing` on the Firestore emulator (throwaway test, not committed).
- [x] `displayName` limit 1–100 adopted; aligned with spec firestore-security-rules.
