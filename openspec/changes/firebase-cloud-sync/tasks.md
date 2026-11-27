# Tasks: firebase-cloud-sync

Strict TDD is active: in every behavior unit the failing test (RED) comes first, then the minimal implementation (GREEN), then triangulation and refactor. Pure-JVM tests use fakes of the thin `*Remote` interfaces and `AuthGateway`, with Turbine for flows. Tasks flagged **[USER]** are manual steps (Firebase console, real config files, device checks) that the apply phase does not perform. Package root: `app/src/main/java/com/librosleidos/` (main) and `app/src/test/java/com/librosleidos/` (tests), abbreviated `main/` and `test/`.

Commit rule for every unit: `lefthook` pre-commit and commit-msg hooks MUST run, `--no-verify` is forbidden, conventional commits only, no AI attribution or Co-Authored-By. Issue-first: every slice PR links an issue (openspec/specs/issue-workflow). Work-unit commits: each unit below is one reviewable commit inside its slice PR.

Spec refs (capability abbreviations): `UA` user-auth, `CBS` cloud-book-sync, `SBL` shared-book-lists, `AD` account-deletion, `FSR` firestore-security-rules, `BL` book-library, `BE` book-entry, `E2E` e2e-testing, `CI` ci-pipeline, `CQG` code-quality-gates, `BA` backend-api (REMOVED). Design refs: `D1`..`D15` are the architecture decisions in `design.md`; `P1`..`P6` are the product decisions it cites.

## Review Workload Forecast

| Slice | Content | Est. reviewed lines | Pure deletions (reported separately) |
|---|---|---|---|
| B | Delete `backend/` and backend-only tooling, fix references | ~120 | ~3300+ (63 backend files, 3 scripts) |
| 0 | Build spike: BoM, google-services plugin, placeholder, STOP gate | ~100 | 0 |
| 1a | String ids: domain + data | ~450 | ~0 |
| 1b | String ids: UI, nav, not-found, e2e tags | ~380 | ~0 |
| 2a | AuthGateway, auth gate, `e2e` build type, manifest | ~480 | 0 |
| 2b | Sign-in UI, password reset | ~450 | 0 |
| 3a | Users rules + `rules-tests` | ~400 | 0 |
| 3b | Firestore adapter, DI, sign-out | ~600 | ~0 |
| 3c | Room, KSP and Room tests removal | ~500 | Room tree, schemas, Room tests (counted separately, size set at apply) |
| 4a | List/member rules + tests + domain | ~700 | 0 |
| 4b | List adapter, cascade, list UI | ~650 | 0 |
| 4c | Invites, join | ~550 | 0 |
| 5 | Account deletion + docs/config | ~550 | small |
| **Total** | | **~5930 reviewed** | **~3300+ backend, plus 3c deletions** |

| Field | Value |
|---|---|
| Chained PRs recommended | Yes: 13 slice PRs (B is independent; 0 through 5 form one chain) |
| 400-line budget risk | High: every slice except B and 0 is at or over 400 |
| 800-line budget risk | Medium: no slice is estimated over 800; 4a (~700) and 4b (~650) are the closest and may cross it, in which case split by the unit boundaries below (rules vs domain, adapter vs UI) instead of requesting `size:exception` |
| Decision needed before apply | Yes: the `chain_strategy` is NOT chosen. The orchestrator must ask the user (see PR base options below). Slice 0 is also a STOP/re-plan gate that may change later slices |
| Delivery strategy | ask-on-risk; one PR per slice; pure deletions (B, 3c) are not counted against `review_budget_lines=800` |

### PR base options per slice (chain strategy, to be chosen by the user)

| Option | Base of each PR | Tradeoff |
|---|---|---|
| A. Stacked on `dev` (feature branch chain) | B: `dev`. 0: `dev`. 1a: slice 0 branch. 1b: 1a. ... 5: 4c. Each PR targets the previous slice's branch; retarget to `dev` after the parent merges | Small diffs, reviewable in order, but needs retargeting and rebases after each squash merge |
| B. Sequential to `dev` | Every PR targets `dev` and opens only after the previous slice merged. B may merge at any time | No retargeting and always-green `dev`, but slower: the next slice cannot be reviewed in parallel |
| C. Integration branch `feat/firebase-cloud-sync` | B: `dev`. 0 through 5: the integration branch; one final PR to `dev` | Intermediate states (Room kept until 3c, uid scoping only from 3b) never reach `dev`; the final PR is huge and cannot be reviewed as a whole |

Recommendation: A or B. Interim state (design Migration): after 2a/2b books stay in device-local Room and are not uid-scoped until 3b; this is accepted (no users) but argues against option C only if `dev` must stay releasable.

### Slice order and dependencies

- B is independent and can land first or last. B touches `docs/secrets.md`, `README.md`, `openspec/config.yaml`, `lefthook.yml`, `build.gradle.kts`; slice 5 touches `README.md` and `openspec/config.yaml` again, so land B before 5 to avoid conflicts.
- Strict chain: 0 -> 1a -> 1b -> 2a -> 2b -> 3a -> 3b -> 3c -> 4a -> 4b -> 4c -> 5. Slice 3a (rules, JS) has no Kotlin dependency on 2b and could run in parallel with 2a/2b under option B only if its branch is independent; the design keeps it sequential, so treat it as sequential.
- Single writer per slice. Inside every slice: RED, GREEN, TRIANGULATE/REFACTOR, then the commit task.
- Bottlenecks: slice 0 (STOP gate for the whole chain); 3a to 3b (the Firestore adapter needs the users rules); 4a (rules and domain) gates 4b and 4c.

---

## Slice B: Delete the backend and its tooling (~120 reviewed lines, ~3300+ deletions) [independent]

Start: `backend/` and its guard, hooks, templates and docs exist. End: `test -e backend` fails; `grep` scenarios from CI "Backend Removed From the Repository" return nothing; Android app build, e2e suite and `secret-scan` unaffected. Files: `backend/**`, `scripts/{check,test}-backend-frozen.sh`, `scripts/test-image-smoke.sh`, `lefthook.yml`, `build.gradle.kts`, `.gitignore`, `.github/ISSUE_TEMPLATE/{chore,feature}.yml`, `docs/secrets.md`, `README.md`, `openspec/config.yaml`, `settings.gradle.kts`, detekt config.

### B.1 RED: executable absence checks

- [x] B.1.1 RED: record the failing baseline: `test -e backend` succeeds, and `grep -rEn 'backend|contract-tests|publish-image|check-image|retag-image|ghcr\.io|docker/login-action|packages: *write' .github/workflows/ .github/dependabot.yml` and `grep -rIn 'backend/' . --exclude-dir=.git --exclude-dir=openspec --exclude-dir=node_modules` list the hits to remove. Save the list as RED evidence. Spec: CI Backend Removed (Backend tree gone, No workflow or dependabot reference, No dangling references).
- [x] B.1.2 RED: confirm the pre-change lefthook run: `bash scripts/test-secrets-contract.sh` and `bash scripts/check-secrets-contract.sh` pass now (baseline), because the secrets contract must stay green after the `docs/secrets.md` edits. Spec: CI Static Contract Checks (Self-test covers failures); D-note in design File Changes (`docs/secrets.md`).

### B.2 GREEN: deletions and reference edits

- [x] B.2.1 GREEN (deletion unit): `git rm -r backend/` and delete `scripts/check-backend-frozen.sh`, `scripts/test-backend-frozen.sh`, `scripts/test-image-smoke.sh`. Report as pure deletions. Spec: CI Backend Removed (Backend tree gone, No backend-only scripts or hooks); BA all REMOVED requirements (Separate Backend Build, Hexagonal Domain and Use Cases, Owner-Scoped Persistence, Integration Tests Against PostgreSQL, Deny-by-Default Security, Actuator Exposes Only Health, OpenAPI Contract as Source of Truth, Request Validation and Safe Errors, Contract Tests, Mutation Testing Gate, Hardened Container Image, Database Credentials From Environment Only, Packaged Artifact Free of Credentials).
- [x] B.2.2 GREEN: `lefthook.yml` drop the `backend-frozen` command (and any `backend/*` glob); `build.gradle.kts` drop the `backend/*.gradle.kts` Spotless target; detekt/Spotless config and `settings.gradle.kts` drop any backend reference; `.gitignore` drop the backend build-output ignore entry. Spec: CI Backend Removed (No backend-only scripts or hooks, No dangling references); CQG Formatting Gate, Pre-commit Hook, Detekt Gate With Baseline (no backend coverage).
- [x] B.2.3 GREEN: `.github/ISSUE_TEMPLATE/{chore,feature}.yml` drop the `backend` option. Spec: CI Backend Removed (No dangling references).
- [x] B.2.4 GREEN: `docs/secrets.md` remove the `SPRING_DATASOURCE_*`, `NEON_HOST_CREDENTIALS`, `JWT_SIGNING_SECRET` rows; keep the `GOOGLE_BOOKS_SERVER_API_KEY` row marked retired and keep the `GOOGLE_BOOKS_API_KEY` row byte-identical so the checker's superseded wording still passes. Run `bash scripts/test-secrets-contract.sh` and `bash scripts/check-secrets-contract.sh`. Spec: CI Open Question resolution (backend-era secret rows); CI Static Contract Checks.
- [x] B.2.5 GREEN: `README.md` remove the frozen-backend section and any backend setup text (Spanish); `openspec/config.yaml` remove the backend testing entry and the `bash scripts/test-backend-frozen.sh` verify rule. Spec: CI Backend Removed (No dangling references); CQG Documented Setup.
- [x] B.2.6 GREEN: fix any remaining hit from B.1.1; `scripts/test-kover-summary.sh` and `scripts/test-secrets-contract.sh` stay unchanged (backend only in self-contained fixtures). Spec: CI Backend Removed (No dangling references).

### B.3 TRIANGULATE and commit

- [x] B.3.1 TRIANGULATE: run `test -e backend` (fails), the two `grep` commands from B.1.1 (match nothing), `actionlint` (exit 0), `shellcheck scripts/*.sh`, `bash scripts/test-secrets-contract.sh`, `bash scripts/check-secrets-contract.sh`, `bash scripts/test-e2e-ci-config.sh`, `bash scripts/test-kover-summary.sh`, `./gradlew :app:testDebugUnitTest`, `./gradlew spotlessCheck detekt`. Spec: CI Backend Removed (Workflows stay valid); CQG.
- [ ] B.3.2 Commit as two work units: (1) `chore: remove the backend tree` (pure deletion), (2) `chore: drop backend references from tooling, docs and templates`. Report the lefthook summary. Open the PR to `dev`; label `size:exception` is NOT needed because deletions are reported separately (confirm with the owner if the labeller counts them).

---

## Slice 0: Build spike and STOP gate (~100 lines)

Start: no Firebase dependency. End: `./gradlew :app:assembleDebug :app:testDebugUnitTest` green with the Firebase BoM, the google-services plugin on AGP 9.5.3 and a placeholder config; spike findings recorded. **STOP/re-plan gate**: if any spike below fails, do not start slice 1a; report to the orchestrator and re-plan. Files: `gradle/libs.versions.toml`, `build.gradle.kts`, `app/build.gradle.kts`, `app/google-services.json`, `.gitignore`.

### 0.1 RED: spikes as failing proofs

- [x] 0.1.1 RED: add `app/src/test/java/com/librosleidos/data/firebase/FirebaseSpikeTest.kt` asserting `FirebaseApp.initializeApp` reads the placeholder project id `demo-libros` under Robolectric. Run: fails (no dependency, no config). Spec: CI Placeholder Firebase Configuration in CI (CI builds without secrets); D10.
- [x] 0.1.2 RED: add a spike test, against the Firestore emulator via `firebase emulators:exec --only firestore --project demo-libros`, asserting that an offline (network disabled) `set()` returns its Task without waiting for server ack and that a read-after-write from cache returns the value. Fails until wired. Spec: CBS Offline Read and Non-Blocking Write; D4.

### 0.2 GREEN

- [x] 0.2.1 GREEN: add Firebase BoM, `firebase-auth`, `firebase-firestore`, `firebase-appcheck-playintegrity`, `firebase-appcheck-debug`, `androidx.credentials`, `googleid`, `kotlinx-coroutines-play-services` and the `com.google.gms.google-services` 4.6.0 plugin to `gradle/libs.versions.toml`, `build.gradle.kts`, `app/build.gradle.kts`. Spec: CI Placeholder Firebase Configuration in CI; D10.
- [x] 0.2.2 GREEN: commit placeholder `app/google-services.json` (project `demo-libros`, non-`AIza` key so `secret-scan` passes); `.gitignore` the real files in `app/src/{debug,release}/google-services.json`. Spec: CI Placeholder Firebase Configuration in CI (CI builds without secrets, Placeholder is inert); D10.
- [x] 0.2.3 GREEN: make 0.1.1 pass; run `./gradlew :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest` on AGP 9.5.3. Record the output.

### 0.3 STOP gate (spike verification)

- [x] 0.3.1 Verify and record: (a) google-services 4.6.0 applies on AGP 9.5.3 for debug and release; (b) offline `set()` does not block (0.1.2 passes); (c) real-config variant lookup: place a dummy `app/src/debug/google-services.json` and confirm it wins over the root file, then remove it; (d) `secret-scan` (gitleaks) passes on the placeholder. Spec: CI Placeholder Firebase Configuration in CI; CBS Offline Read and Non-Blocking Write.
- [x] 0.3.2 Emulator spike: write a throwaway rules test (not committed to `main`; kept only as evidence) proving a `list` query on `invites` constrained by `where listId == X` is allowed for a member under a rule using `resource.data.listId` (design Open Question 2). If it fails, record the fallback `lists/{listId}/invites/{code}` plus lookup doc and flag slices 3a/4a/4c for re-plan. Spec: FSR Invite Code Enforcement; D14.
- [x] 0.3.3 **STOP/re-plan**: if any of 0.3.1 (a)(b)(c) fails, stop and report `blocked`. If 0.3.2 fails, update the design and tasks for 4a/4c before continuing. Update design Open Questions to resolved with evidence.
- [ ] 0.3.4 Commit: `build: add the Firebase BoM and google-services plugin with a placeholder config`. Open the PR.

---

## Slice 1a: String ids in domain and data (~450 lines)

Start: `Book.id: Long`, Room v1. End: `Book.id: String`, `BookRepository` on String ids, Room v2 with String PK and destructive fallback (no schema import), `SaveBookUseCase` takes `newId`. Files (main): `domain/model/Book.kt`, `domain/model/BookQuery.kt` (new), `domain/port/BookRepository.kt`, `domain/usecase/{SaveBook,GetBookById,DeleteBook,GetBooks}UseCase.kt`, `data/local/{BookEntity,BookDao,BookMapper,BookRepositoryImpl,BooksDatabase}.kt`, `app/schemas/`.

### 1a.1 RED

- [ ] 1a.1.1 RED: `test/domain/model/BookQueryTest.kt`: `filterAndSort` by DATE desc then title asc then id asc; RATING desc then title asc then id asc; ties fully ordered; date-range and rating filters. Fails: `BookQuery.kt` missing. Spec: CBS Client-Side Filter and Sort; BL List Read Books; D3.
- [ ] 1a.1.2 RED: `test/domain/usecase/SaveBookUseCaseTest.kt`: new book gets `newId()`; edit keeps the id; injected `newId` makes ids deterministic; two saves with the same title produce two ids. Update `FakeBookRepository.kt` to String ids. Spec: CBS Client-Generated String Identifiers; BE Add Book Manually, Allow Duplicate Titles; D2.
- [ ] 1a.1.3 RED: update `GetBookByIdUseCaseTest.kt`, `DeleteBookUseCaseTest.kt`, `GetBooksUseCaseTest.kt` to String ids and the null/absent result for an unknown id. Spec: BL String Book Identifier, Delete Book Entry; BE Edit Existing Book.
- [ ] 1a.1.4 RED: update `test/data/local/{BookDaoTest,BookMapperTest,BookRepositoryImplTest}.kt` to String ids (Room v2); add a mapper case for an id with `-` and `_`. Spec: BL String Book Identifier; CBS Storage Behind a Port.

### 1a.2 GREEN

- [ ] 1a.2.1 GREEN: `domain/model/Book.kt` and `BookRepository.kt` use `String` ids; create `domain/model/BookQuery.kt` with the pure `filterAndSort` (D3); make 1a.1.1 pass. Spec: BL String Book Identifier; CBS Client-Side Filter and Sort, Storage Behind a Port; D1, D3.
- [ ] 1a.2.2 GREEN: `SaveBookUseCase(repo, newId = { UUID.randomUUID().toString() })`, `GetBookByIdUseCase`, `DeleteBookUseCase`, `GetBooksUseCase` (delegating sort/filter to `filterAndSort`). Spec: CBS Client-Generated String Identifiers; BL List Read Books; D2.
- [ ] 1a.2.3 GREEN: Room entity, DAO, mapper, `BookRepositoryImpl` and `BooksDatabase` (version 2, `fallbackToDestructiveMigration`, remove committed v1 schema export if present). Spec: BL String Book Identifier; design Migration.

### 1a.3 TRIANGULATE and commit

- [ ] 1a.3.1 TRIANGULATE: add a `BookQueryTest` case with identical date and title (id tie-break) and an empty list; run `./gradlew :app:testDebugUnitTest`.
- [ ] 1a.3.2 REFACTOR: `./gradlew spotlessCheck detekt`; remove dead `Long` helpers.
- [ ] 1a.3.3 Commit (two work units): `refactor(domain): use string book ids and a pure filterAndSort` and `refactor(data): move Room to string primary keys`.

---

## Slice 1b: String ids in UI, navigation and e2e tags (~380 lines)

Start: UI and nav still pass `Long`/`NO_BOOK_ID`. End: `edit?bookId={bookId}` nullable String arg, unknown id shows a not-found state, e2e tags accept string ids. Files: `MainActivity.kt`, `ui/list/{BookListViewModel,BookListScreen,BookListTags,BookListRoute,BookListUiState}.kt`, `ui/edit/{BookEditViewModel,BookEditScreen,BookEditTags,BookEditRoute,BookEditUiState}.kt`, `e2e/src/pages/tags.ts` and its e2e tests.

### 1b.1 RED

- [ ] 1b.1.1 RED: `test/ui/edit/BookEditViewModelTest.kt`: null id = add mode; existing id loads the book; unknown id emits a `NotFound` state (no crash, no save); save preserves the id. Spec: BE Edit Existing Book; BL String Book Identifier; D11.
- [ ] 1b.1.2 RED: `test/ui/edit/BookEditScreenTest.kt`: `NotFound` renders a message and a back action. Spec: BE Edit Existing Book; D11.
- [ ] 1b.1.3 RED: `test/ui/list/{BookListViewModelTest,BookListScreenTest}.kt`: item tags are `book_list_item_<stringId>` and `remove` tags use string ids; delete passes the string id. Spec: BL Delete Book Entry; E2E Test Tag Naming.
- [ ] 1b.1.4 RED: e2e tag test (existing harness in `e2e/`): `LIST_ITEM_PATTERN` `^book_list_item_([A-Za-z0-9_-]+)$` fails on the current numeric pattern for a UUID id. Spec: E2E Test Tag Naming.

### 1b.2 GREEN

- [ ] 1b.2.1 GREEN: `MainActivity.kt` route `edit?bookId={bookId}` with a nullable `StringType` argument; delete `NO_BOOK_ID`. Spec: BE Add Book Manually, Edit Existing Book; D11.
- [ ] 1b.2.2 GREEN: `BookEditViewModel`, `BookEditUiState`, `BookEditScreen` (`NotFound` state with message and back). Spec: BE Edit Existing Book; D11.
- [ ] 1b.2.3 GREEN: list ViewModel, screen, tags with string ids. Spec: BL Delete Book Entry; E2E Test Tag Naming.
- [ ] 1b.2.4 GREEN: `e2e/src/pages/tags.ts`: `LIST_ITEM_PATTERN`, `LIST_ITEM_SELECTOR_REGEX`, `item(id: string)`, `removeButton(id: string)`. Run the e2e unit/lint checks and `bash scripts/test-e2e-ci-config.sh`. Spec: E2E Test Tag Naming, Happy-Path Flow.

### 1b.3 TRIANGULATE and commit

- [ ] 1b.3.1 TRIANGULATE: UUID id and an id containing a hyphen or an underscore in the tag regex; `./gradlew :app:testDebugUnitTest`; e2e tests on the existing debug build still pass (offline, string ids).
- [ ] 1b.3.2 Commit: `refactor(ui): navigate and tag books by string id` and `test(e2e): accept string ids in page-object tags`.

---

## Slice 2a: AuthGateway, auth gate, `e2e` build type (~480 lines)

Start: no auth. End: the NavHost is gated by `authState`; a sign-in screen shell is shown signed-out; `e2e` build type swaps in in-memory adapters; Robolectric smoke uses `SmokeTestApp`. Files: `domain/port/AuthGateway.kt`, `data/firebase/FirebaseAuthGateway.kt`, `data/firebase/AuthRemote.kt`, `LibrosLeidosApp.kt` (`open fun cloudServices()`), `di/AppContainer.kt`, `MainActivity.kt`, `app/src/main/AndroidManifest.xml`, `app/build.gradle.kts` (build type), `app/src/e2e/**` (`E2eApp`, in-memory auth/repo), `app/src/test/.../SmokeTestApp.kt`, `.github/workflows/ci.yml` (e2e job builds `assembleE2e`), e2e `appId`/config.

### 2a.1 RED

- [ ] 2a.1.1 RED: `test/data/firebase/FirebaseAuthGatewayTest.kt` with a fake `AuthRemote`: `authState` emits `SignedOut` then `SignedIn(uid)`, replays the current state to a new collector, reacts to sign-out. Spec: UA Observable Auth State, Session Persistence; D1, D5.
- [ ] 2a.1.2 RED: `test/ui/auth/AuthGateTest.kt` (Robolectric, `@Config(application = SmokeTestApp::class)`): signed-out shows the sign-in screen and not the book list; signed-in shows the list; auth flip signed-in to signed-out returns to sign-in. Spec: UA Sign-In Required; D9.
- [ ] 2a.1.3 RED: update `MainActivitySmokeTest.kt` to run under `SmokeTestApp` with a fake auth state. Spec: E2E Isolation and Offline Execution; D9.
- [ ] 2a.1.4 RED: `test/e2e/InMemoryServicesTest.kt` (or under `app/src/test`): the in-memory auth signs in with a test user and the in-memory repository round-trips a book without network. Spec: E2E Test-Only In-Memory Wiring.
- [ ] 2a.1.5 RED: build check script/step: `./gradlew :app:assembleE2e` fails (no build type); and the release variant contains no `E2eApp` class (assert via `./gradlew :app:assembleRelease` plus a grep of the merged manifest). Spec: E2E Test-Only In-Memory Wiring (no test code in release); D9.

### 2a.2 GREEN

- [ ] 2a.2.1 GREEN: `domain/port/AuthGateway.kt` (`authState: Flow<AuthState>`, sign-in/out contracts) and `FirebaseAuthGateway` over `AuthRemote` (`callbackFlow`). Spec: UA Observable Auth State; D1.
- [ ] 2a.2.2 GREEN: `LibrosLeidosApp` `open fun cloudServices()`, `AppContainer` consuming it; manifest `android:name="${appClass}"` with `manifestPlaceholders`. Spec: E2E Test-Only In-Memory Wiring; D9.
- [ ] 2a.2.3 GREEN: `e2e` build type (`initWith(debug)`, task `assembleE2e`), `app/src/e2e/**` `E2eApp` with in-memory `AuthGateway` and `BookRepository`; `SmokeTestApp` under `app/src/test`. Spec: E2E Test-Only In-Memory Wiring, Isolation and Offline Execution; D9.
- [ ] 2a.2.4 GREEN: `MainActivity` gates the NavHost on `authState` (signed-out routes to a sign-in destination shell). Spec: UA Sign-In Required.
- [ ] 2a.2.5 GREEN: `ci.yml` `e2e-tests` builds `assembleE2e`; update the e2e app config to the `e2e` variant output; run `bash scripts/test-e2e-ci-config.sh` and `actionlint`. Spec: CI E2E Test Job; E2E Isolation and Offline Execution, Happy-Path Flow.

### 2a.3 TRIANGULATE and commit

- [ ] 2a.3.1 TRIANGULATE: auth state during cold start (initial emission), user switch without app restart; the e2e happy path passes signed in through the in-memory auth on `assembleE2e`. Spec: E2E Happy-Path Flow.
- [ ] 2a.3.2 REFACTOR and verify: `./gradlew spotlessCheck detekt :app:testDebugUnitTest`, `actionlint`.
- [ ] 2a.3.3 Commit (two units): `feat(auth): add AuthGateway and gate navigation on auth state`, `build(e2e): add the e2e build type with in-memory services`.

---

## Slice 2b: Sign-in UI and password reset (~450 lines)

Start: sign-in shell. End: Google and email/password sign-in, registration, password reset (P6). Files: `ui/auth/{SignInViewModel,SignInScreen,SignInUiState,SignInTags,PasswordResetRoute...}.kt`, `domain/usecase/{SignIn,SignInWithGoogle,SendPasswordReset}UseCase.kt` (or direct gateway calls), `data/firebase/{FirebaseAuthGateway,GoogleCredentialSource}.kt`, strings.

### 2b.1 RED

- [ ] 2b.1.1 RED: `test/ui/auth/SignInViewModelTest.kt`: email/password success; invalid credentials message without revealing which field is wrong; network error; empty fields blocked; registration; a second submit while loading is ignored. Spec: UA Email and Password Sign-In.
- [ ] 2b.1.2 RED: `test/ui/auth/PasswordResetViewModelTest.kt`: valid email sends reset and shows a neutral confirmation (same message for unknown email); invalid email format rejected; failure keeps the screen. Spec: UA Password Reset; P6.
- [ ] 2b.1.3 RED: `test/data/firebase/GoogleCredentialSourceTest.kt` with a fake credential provider: token to `signInWithCredential`; user cancel produces no error state; failure produces an error. Spec: UA Google Sign-In.
- [ ] 2b.1.4 RED: `test/ui/auth/SignInScreenTest.kt` (Robolectric): Google button, email/password fields, forgot-password link, error text, and test tags. Spec: UA Sign-In Required, Email and Password Sign-In, Password Reset.

### 2b.2 GREEN

- [ ] 2b.2.1 GREEN: sign-in ViewModel and `AuthGateway` methods (`signInWithEmail`, `register`, `sendPasswordReset`, `signInWithGoogle`). Spec: UA Email and Password Sign-In, Password Reset, Google Sign-In; D1.
- [ ] 2b.2.2 GREEN: Credential Manager + `googleid` source and its `FirebaseAuthGateway` binding (server client id from `google-services.json`). Spec: UA Google Sign-In.
- [ ] 2b.2.3 GREEN: `SignInScreen` and password-reset screen, strings (English and Spanish if present), tags; wire into the gate. Spec: UA Sign-In Required, Password Reset.
- [ ] 2b.2.4 GREEN: in-memory `AuthGateway` (e2e) supports the same flows so the e2e login works offline. Spec: E2E Test-Only In-Memory Wiring.

### 2b.3 TRIANGULATE and commit

- [ ] 2b.3.1 TRIANGULATE: unknown email on reset, weak password on register, repeated Google cancel; `./gradlew :app:testDebugUnitTest spotlessCheck detekt`. **[USER]** enable Google and Email/Password providers in the Firebase console and register the SHA-1 (manual checklist in README at slice 5).
- [ ] 2b.3.2 Commit: `feat(auth): add Google and email/password sign-in with password reset`.

---

## Slice 3a: Users rules and `rules-tests` job (~400 lines)

Start: no rules. End: `firebase/firestore.rules` (users + default deny + `validBook`), emulator rules tests, `rules-tests` job in `ci.yml` gating `ci-gate`. Files: `firebase.json`, `firebase/{firestore.rules,firestore.indexes.json,package.json,tests/*}`, `.github/workflows/ci.yml`, `.gitignore`.

### 3a.1 RED

- [ ] 3a.1.1 RED: `firebase/tests/users.test.js` (using `@firebase/rules-unit-testing`, project `demo-libros`): unauthenticated read/write denied; user A cannot read/write user B's books; owner create/read/update/delete allowed with a valid book. Run `npm test` under `firebase emulators:exec`: fails (no rules). Spec: FSR Default Deny and Authentication, Personal Book Access; D15 note.
- [ ] 3a.1.2 RED: `firebase/tests/validation.test.js`: title/author 301 chars rejected, 300 accepted; review 5001 rejected; coverUrl > 2048 rejected, `null` accepted; rating 0.7 and 5.5 rejected, 0.5 and 5 accepted; non-int epoch day rejected; extra field rejected; `updatedAt` != `request.time` rejected. Spec: FSR Data Validation and Size Limits.
- [ ] 3a.1.3 RED: `rules-tests` CI check: add a failing-check assertion script (extend `scripts/test-e2e-ci-config.sh` or a new `scripts/test-rules-ci-config.sh` with fixtures) asserting `ci.yml` has a `rules-tests` job, `ci-gate.needs` includes it, permissions `contents: read` only, no `secrets.*`. Fails now. Spec: CI Rules Tests Job (Runs on every trigger, Read-only and secret-free, Weakened rule blocks the gate), Single Required Check (ci-gate).

### 3a.2 GREEN

- [ ] 3a.2.1 GREEN: `firebase.json`, `firebase/firestore.rules` (`rules_version = '2'`, default deny, `users/{uid}/**` owner-only, `validBook()`), `firestore.indexes.json` (empty plus the collection-group override reserved for 4a), `firebase/package.json` (pinned `firebase-tools` and `@firebase/rules-unit-testing`). Spec: FSR Default Deny and Authentication, Personal Book Access, Data Validation and Size Limits.
- [ ] 3a.2.2 GREEN: `ci.yml` job `rules-tests` (Node + JDK, pinned `firebase-tools`, `firebase emulators:exec --only firestore --project demo-libros "npm test"`), `permissions: contents: read`, no path filter, no secrets; add to `ci-gate.needs`; update `report` only if needed. Spec: CI Rules Tests Job; FSR Emulator Rules Tests.
- [ ] 3a.2.3 GREEN: tests 3a.1.1 to 3a.1.3 pass; `actionlint` exits 0; `bash scripts/test-e2e-ci-config.sh` still passes (update its expectation through a RED case first if the `ci-gate` needs list is asserted). Spec: CI Single Required Check (ci-gate), Out of Scope Boundaries.

### 3a.3 TRIANGULATE and commit

- [ ] 3a.3.1 TRIANGULATE: mutate a rule (open `users/{uid}` to any signed-in user), run `npm test`: it fails; revert. This is the "weakened rule blocks the gate" scenario. Spec: CI Rules Tests Job (Weakened rule blocks the gate).
- [ ] 3a.3.2 Commit: `feat(rules): add Firestore users rules and the rules-tests job` and `ci: gate ci-gate on rules-tests`.

---

## Slice 3b: Firestore adapter, DI switch, sign-out (~600 lines)

Start: Room is the source of truth. End: Firestore is the source of truth through `BooksRemote`; DI switches to cloud; uid-scoped flows; sign-out clears the cache with the unsynced-writes warning. Files: `data/firebase/{BooksRemote,FirestoreBooksRemote,FirestoreBookRepository,BookDto,BookMapper}.kt`, `domain/port/BookRepository.kt`, `domain/usecase/SignOutUseCase.kt`, `di/AppContainer.kt`, `ui/account/{SignOut...}.kt`, `ui/list` sign-out entry.

### 3b.1 RED

- [ ] 3b.1.1 RED: `test/data/firebase/BookMapperTest.kt`: domain to map DTO and back; null `coverUrl`; `updatedAt` handling; unknown extra fields ignored on read. Spec: CBS Per-User Book Storage; D1.
- [ ] 3b.1.2 RED: `test/data/firebase/FirestoreBookRepositoryTest.kt` with a fake `BooksRemote`: observe emits through `filterAndSort`; `save` and `delete` fire-and-forget (return before the fake completes the ack); a failure listener logs and does not throw; last-write-wins (later `updatedAt` replaces). Spec: CBS Offline Read and Non-Blocking Write, Last-Write-Wins, Client-Side Filter and Sort, Storage Behind a Port; D3, D4.
- [ ] 3b.1.3 RED: `test/data/firebase/UidScopedRepositoryTest.kt`: `authState.flatMapLatest` re-subscribes on sign-in as user A then user B (no data of A visible for B); signed-out emits an empty list. Spec: CBS Per-User Book Storage, Multi-Device Sync; D5.
- [ ] 3b.1.4 RED: `test/domain/usecase/SignOutUseCaseTest.kt`: pending writes resolve within 3 s then `signOut()`, `terminate()`, `clearPersistence()` in that order; timeout returns `PendingWritesWouldBeLost`; confirm proceeds anyway; cancel keeps the session; clear failure surfaces. Spec: UA Sign-Out Clears Local Cache; D6.
- [ ] 3b.1.5 RED: `test/ui/account/SignOutViewModelTest.kt` and a screen test for the warning dialog text. Spec: UA Sign-Out Clears Local Cache; P4.
- [ ] 3b.1.6 RED: `test/di/AppContainerTest.kt`: the default container wires the Firestore repository; the e2e services still wire the in-memory repository. Spec: CBS Storage Behind a Port; E2E Test-Only In-Memory Wiring; D9.

### 3b.2 GREEN

- [ ] 3b.2.1 GREEN: `BooksRemote`, DTO, mapper, `FirestoreBookRepository` (one listener per collection; path `users/{uid}/books`; `set` and `delete` not awaited). Spec: CBS Per-User Book Storage, Non-Blocking Write; D1, D3, D4.
- [ ] 3b.2.2 GREEN: `FirestoreBooksRemote` (real SDK, persistence enabled, App Check providers: Play Integrity in release, debug provider in debug/e2e). Spec: CBS Offline Read; FSR App Check Enforcement; D15.
- [ ] 3b.2.3 GREEN: uid-scoped repository via `flatMapLatest`; `AppContainer` switches default services to cloud. Spec: CBS Per-User Book Storage; D5.
- [ ] 3b.2.4 GREEN: `SignOutUseCase`, VM, warning dialog, sign-out action in the list screen. Spec: UA Sign-Out Clears Local Cache; D6.
- [ ] 3b.2.5 GREEN: Firestore adapter integration tests against the emulator (JVM, `FirestoreEmulatorIT`, run only in the emulator step): user A cannot read B's books through the adapter; offline write visible from cache. Gated behind a Gradle property so default `testDebugUnitTest` stays emulator-free. Spec: CBS Multi-Device Sync, Offline Read and Non-Blocking Write; FSR Personal Book Access.

### 3b.3 TRIANGULATE and commit

- [ ] 3b.3.1 TRIANGULATE: rapid sign-in/out/in, a user switch with unsynced writes of the previous user, sign-out while offline. Run `./gradlew :app:testDebugUnitTest spotlessCheck detekt` and the e2e suite on `assembleE2e`. **[USER]** with the real `google-services.json` in `app/src/debug/`: verify two devices see the same books and offline read/write on a device (manual check for the success criteria). Spec: CBS Multi-Device Sync.
- [ ] 3b.3.2 Commit (two units): `feat(data): store books in Firestore behind the BookRepository port`, `feat(auth): clear the local cache on sign-out with an unsynced-write warning`.

---

## Slice 3c: Remove Room, KSP and Room tests (~500 lines; deletions reported separately)

Start: Room and KSP remain beside the Firestore repository. End: no Room, KSP, `app/schemas/` or `_Impl` Kover exclusions. Files: `data/local/*`, `test/data/local/*`, `app/schemas/**`, `gradle/libs.versions.toml`, `app/build.gradle.kts`, `build.gradle.kts`, Kover config.

### 3c.1 RED

- [ ] 3c.1.1 RED: add a guard test `scripts/test-no-room.sh` (checker plus harness style) or a Gradle verification asserting there is no `androidx.room` or `ksp` reference in `*.kt`, `*.kts`, `libs.versions.toml`, and no `data/local` directory; fails now. Spec: CBS Room Removed Without Import.
- [ ] 3c.1.2 RED: run `./gradlew :app:koverVerify` (or the project's coverage verify) with the `*_Impl` exclusions removed from config first; observe it still passes or record the figure, to prove the exclusion is no longer needed. Spec: CQG Coverage Report.

### 3c.2 GREEN

- [ ] 3c.2.1 GREEN (deletion unit): delete `main/data/local/**`, `test/data/local/**`, `app/schemas/`; remove Room and KSP from `libs.versions.toml`, `build.gradle.kts`, `app/build.gradle.kts`. Report as pure deletions. Spec: CBS Room Removed Without Import (no import path).
- [ ] 3c.2.2 GREEN: Kover config drops `*_Impl` exclusions; any detekt baseline entries for removed files are pruned. Spec: CQG Coverage Report, Detekt Gate With Baseline.
- [ ] 3c.2.3 GREEN: make 3c.1.1 pass; add `scripts/test-no-room.sh` to lefthook only if it follows the existing pattern; otherwise keep it as a verify-only check.

### 3c.3 TRIANGULATE and commit

- [ ] 3c.3.1 TRIANGULATE: `./gradlew :app:assembleDebug :app:assembleRelease :app:assembleE2e :app:testDebugUnitTest spotlessCheck detekt` and coverage task; e2e suite green. Spec: CQG Coverage Report, Pre-commit Hook.
- [ ] 3c.3.2 Commit (two units): `test: assert Room is gone` and `refactor(data): remove Room and KSP`.

---

## Slice 4a: List and member rules, rules tests, domain (~700 lines)

Start: users rules only. End: rules for `lists`, `members`, `books`, `invites`, collection-group member read; rules tests; `SharedListRepository` port, models, use cases. If the slice nears 800, split into 4a-i (rules + tests) and 4a-ii (domain). Files: `firebase/firestore.rules`, `firebase/firestore.indexes.json`, `firebase/tests/{lists,members,invites,cascade}.test.js`, `domain/model/{SharedList,Member,Invite}.kt`, `domain/port/{SharedListRepository,InviteCodeGenerator}.kt`, `domain/usecase/{CreateList,LeaveList,...}UseCase.kt`.

### 4a.1 RED: rules tests (one file per concern)

- [ ] 4a.1.1 RED: `firebase/tests/lists.test.js`: list create with `existsAfter(member)`, shape `hasOnly(['name','createdAt'])`, name 1-100, `createdAt == request.time`; create without a member doc in the batch denied; non-member read/update/delete denied; member allowed. Spec: FSR List Creation Rules, List Access by Membership; SBL Create List.
- [ ] 4a.1.2 RED: `firebase/tests/members.test.js`: creator member (`!exists(list) && existsAfter(list)`, `inviteCode == null`) allowed; join with a valid invite allowed; a member cannot create another uid's member doc; displayName 0 and 101 chars rejected; extra fields rejected; update denied; read only own doc through the collection-group query `members where uid == me`; remove by any member allowed. Spec: FSR Member Entry Validation, List Access by Membership; SBL Equal Members, Remove Member; D7.
- [ ] 4a.1.3 RED: `firebase/tests/invites.test.js`: `get` by any signed-in user; `list` only with `listId ==` for members; create `uses == 0`, `1 <= maxUses <= 50`, `expiresAt <= now + 7d`; join increments `uses` by exactly 1 in the same batch (`getAfter`); expired, exhausted, replayed and wrong-list invites denied; two joins racing on the last use: the second is denied; delete by a member. Run on the emulator; if 0.3.2 recorded the fallback, use `lists/{listId}/invites/{code}` here. Spec: FSR Invite Code Enforcement, Invite Field Validation; SBL Invite Codes; D13, D14.
- [ ] 4a.1.4 RED: `firebase/tests/listBooks.test.js`: member read/write of `lists/{listId}/books/*` with `validBook`; non-member denied; size limits identical to personal books. Spec: FSR Data Validation and Size Limits, List Access by Membership; SBL Books Are Copies.
- [ ] 4a.1.5 RED: `firebase/tests/cascade.test.js`: permission ordering for the cascade (books, invites, other members, list, own member last); deleting the list doc while still a member allowed; deleting own member doc first then others denied. Spec: SBL Delete List; D8.

### 4a.2 RED: domain tests

- [ ] 4a.2.1 RED: `test/domain/usecase/CreateListUseCaseTest.kt`: trims and validates name 1-100; blank rejected; the creator becomes a member. Spec: SBL Create List.
- [ ] 4a.2.2 RED: `test/domain/usecase/InviteCodeTest.kt` (or `InviteCodeGeneratorTest`): 12 characters, Crockford base32 alphabet only, uses an injected random source; defaults 7 days and 10 uses; maximums 7 days and 50 uses clamp. Spec: SBL Invite Codes; P1; D14.
- [ ] 4a.2.3 RED: `test/domain/usecase/LeaveOrRemoveMemberUseCaseTest.kt`: leaving with other members left removes only the own member doc; leaving as the last member routes to the list cascade (D8); removing another member allowed; removing self as the last member cascades. Spec: SBL Remove Member, Delete List; D8.

### 4a.3 GREEN

- [ ] 4a.3.1 GREEN: `firestore.rules` lists, members (including `match /{path=**}/members/{m}`), list books, invites, helpers `signedIn`, `isMember`, `validBook`; `firestore.indexes.json` collection-group override on `members.uid`. Make 4a.1.* pass. Spec: FSR List Access by Membership, List Creation Rules, Member Entry Validation, Invite Code Enforcement, Invite Field Validation, Data Validation and Size Limits; D7, D13, D14.
- [ ] 4a.3.2 GREEN: domain models, `SharedListRepository` port, invite code generator (`SecureRandom` behind an interface), use cases (create, leave, remove member). Make 4a.2.* pass. Spec: SBL Create List, Equal Members, Invite Codes, Remove Member; D1, D8.

### 4a.4 TRIANGULATE and commit

- [ ] 4a.4.1 TRIANGULATE: mutate each of: the `existsAfter` clause, the `uses + 1` check, the collection-group read rule, the member `uid == m` check; each makes at least one rules test fail; revert. Spec: FSR Emulator Rules Tests.
- [ ] 4a.4.2 Commit (two units): `feat(rules): add list, member and invite rules with emulator tests`, `feat(domain): add shared-list models, ports and use cases`. If the diff is over 800 lines, split into two PRs along these units.

---

## Slice 4b: List adapter, cascade and list UI (~650 lines)

Start: rules and domain exist. End: `ListsRemote` + `FirestoreSharedListRepository`, the cascade, the lists screens, and shared books via `bookRepositoryFor(listId)`. Files: `data/firebase/{ListsRemote,FirestoreListsRemote,FirestoreSharedListRepository,ListCascade}.kt`, `ui/lists/*`, `ui/edit/BookEditViewModel.kt`, `di/AppContainer.kt`, e2e in-memory list repository.

### 4b.1 RED

- [ ] 4b.1.1 RED: `test/data/firebase/FirestoreSharedListRepositoryTest.kt` (fake `ListsRemote`): the lists observed via the membership collection-group query; create list writes list and creator member in one batch; no user-identifying field on list books (D12). Spec: SBL Create List, Books Are Copies; D7, D12.
- [ ] 4b.1.2 RED: `test/data/firebase/ListCascadeTest.kt`: operation order is list books, invites, other members, list doc, own member doc; each step is chunked at <= 500 ops; re-running after a mid-way failure is idempotent; offline refuses ("needs a connection"). Spec: SBL Delete List, Offline Behavior of Lists; D8.
- [ ] 4b.1.3 RED: `test/data/firebase/BookRepositoryForListTest.kt`: `bookRepositoryFor(listId)` reuses the `FirestoreBookRepository` with path `lists/{listId}/books`; `filterAndSort` applies. Spec: SBL Books Are Copies; CBS Storage Behind a Port; D12.
- [ ] 4b.1.4 RED: `test/ui/lists/{ListsViewModelTest,ListsScreenTest,ListDetailViewModelTest}.kt`: lists screen, create dialog, delete-list confirmation, remove-member, copy a personal book into a list (id kept or regenerated, no `addedBy`), offline messages. Spec: SBL Create List, Equal Members, Books Are Copies, Remove Member, Delete List, Offline Behavior of Lists.
- [ ] 4b.1.5 RED: update the `BookEditViewModelTest.kt` for the list scope (edit and add inside a list). Spec: SBL Equal Members; BE Edit Existing Book; D12.

### 4b.2 GREEN

- [ ] 4b.2.1 GREEN: `ListsRemote`, `FirestoreListsRemote`, `FirestoreSharedListRepository`, `ListCascade` (batched, online, idempotent). Spec: SBL Create List, Delete List, Remove Member; D7, D8.
- [ ] 4b.2.2 GREEN: `bookRepositoryFor(listId)` and `BookEditViewModel` reuse. Spec: SBL Books Are Copies; D12.
- [ ] 4b.2.3 GREEN: list screens and navigation (`lists`, `lists/{listId}`), DI wiring, in-memory list repository for the e2e build. Spec: SBL Create List, Equal Members; E2E Test-Only In-Memory Wiring.
- [ ] 4b.2.4 GREEN: emulator integration test (`FirestoreListsEmulatorIT`) of the cascade against the real rules from 4a. Spec: SBL Delete List; FSR List Access by Membership.

### 4b.3 TRIANGULATE and commit

- [ ] 4b.3.1 TRIANGULATE: cascade over a list with 1200 books (3 batches); sole-member leave triggers the cascade; two concurrent leaves accept the orphan (documented). `./gradlew :app:testDebugUnitTest spotlessCheck detekt`; the e2e suite stays green.
- [ ] 4b.3.2 Commit (two units): `feat(data): add the shared-list adapter and cascade deletion`, `feat(ui): add shared lists screens`. If over 800 lines, split adapter and UI into two PRs.

---

## Slice 4c: Invites and join (~550 lines)

Start: lists without invite UI. End: create/share an invite code, join with a code in one online transaction, error messages. Files: `domain/usecase/{CreateInvite,JoinList}UseCase.kt`, `data/firebase/{InvitesRemote,FirestoreInvites}.kt`, `ui/lists/{Invite,Join}*.kt`.

### 4c.1 RED

- [ ] 4c.1.1 RED: `test/domain/usecase/JoinListUseCaseTest.kt` (fake `InvitesRemote`): valid code joins (one transaction: read invite, validate, `uses+1`, create member); unknown code, expired, exhausted give distinct failures; already a member is idempotent; offline gives "Joining needs a connection"; last-use race loser (denied or aborted) gives "This invite is no longer valid". Spec: SBL Invite Codes (join scenarios), Offline Behavior of Lists; D13.
- [ ] 4c.1.2 RED: `test/domain/usecase/CreateInviteUseCaseTest.kt`: batch writes `invites/{code}` and private `users/{uid}/invites/{code}` `{listId}`; no `createdBy`; defaults 7 days and 10 uses; custom within limits; code collision retries. Spec: SBL Invite Codes; D14; P1.
- [ ] 4c.1.3 RED: `test/data/firebase/FirestoreInvitesTest.kt`: DTO mapping, `uses == 0` on create, member `inviteCode` set on a join and `null` on creator. Spec: SBL Invite Codes; FSR Member Entry Validation.
- [ ] 4c.1.4 RED: `test/ui/lists/{InviteViewModelTest,JoinViewModelTest,JoinScreenTest}.kt`: show/copy/share the code, expiry display, join input validation (12 chars, case-insensitive Crockford normalization), error messages. Spec: SBL Invite Codes.

### 4c.2 GREEN

- [ ] 4c.2.1 GREEN: use cases and `FirestoreInvites` (transaction for join). Spec: SBL Invite Codes; D13, D14.
- [ ] 4c.2.2 GREEN: invite and join UI plus strings. Spec: SBL Invite Codes, Offline Behavior of Lists.
- [ ] 4c.2.3 GREEN: emulator integration test `FirestoreInvitesEmulatorIT`: join, replay, expiry and last-use race against the 4a rules. Spec: FSR Invite Code Enforcement; SBL Invite Codes.
- [ ] 4c.2.4 GREEN: in-memory invites for the e2e build; e2e happy path extended only if the existing flow spec requires it (default: no new e2e flow). Spec: E2E Test-Only In-Memory Wiring.

### 4c.3 TRIANGULATE and commit

- [ ] 4c.3.1 TRIANGULATE: expired-by-one-second, `uses == maxUses - 1` boundary, malformed code, member already in the list. `./gradlew :app:testDebugUnitTest spotlessCheck detekt` and `npm test` for rules.
- [ ] 4c.3.2 Commit: `feat(lists): add invite codes and joining a shared list`.

---

## Slice 5: Account deletion and docs/config (~550 lines)

Start: no in-app deletion; docs describe the old setup. End: deletion flow per design (reauth first), docs and config updated. Files: `domain/port/AccountDeletion.kt`, `domain/usecase/DeleteAccountUseCase.kt`, `data/firebase/FirebaseAccountDeletion.kt`, `ui/account/{DeleteAccount...}.kt`, `README.md`, `openspec/config.yaml`, `docs/secrets.md` (if still needed).

### 5.1 RED

- [ ] 5.1.1 RED: `test/domain/usecase/DeleteAccountUseCaseTest.kt` (fakes): reauthentication is the first step (or `auth_time` <= 5 min accepted); declined or failed reauth aborts and nothing is deleted; order: own invites (via `users/{uid}/invites`), per membership (sole member runs the cascade, otherwise delete the own member doc), `users/{uid}/**`, then `currentUser.delete()`, then terminate and clear persistence; a failure in step N leaves earlier steps idempotent; an interrupted run resumes after the next sign-in. Spec: AD Recent Authentication Before Any Deletion, Data and Account Removed, Shared List Handling on Deletion, Failure Handling.
- [ ] 5.1.2 RED: shared-list handling cases: copies in other lists remain and contain no identifying data (D12, P3); a list where the user is the last member is deleted by the cascade. Spec: AD Shared List Handling on Deletion; D8, D12.
- [ ] 5.1.3 RED: `test/ui/account/DeleteAccountViewModelTest.kt` and a screen test: entry point in settings/account, confirmation with a typed or explicit confirm, progress, error, success returns to sign-in. Spec: AD Deletion Entry Point With Confirmation, Failure Handling.
- [ ] 5.1.4 RED: rules tests extension `firebase/tests/deletion.test.js`: the own invites path `users/{uid}/invites/*` readable and deletable by the owner only; a user can delete own member doc and `users/{uid}` tree; after account deletion removal permissions hold in the order of the design. Spec: AD Data and Account Removed; FSR Personal Book Access.

### 5.2 GREEN

- [ ] 5.2.1 GREEN: `AccountDeletion` port, `DeleteAccountUseCase`, `FirebaseAccountDeletion` (reuses `ListCascade`, batch delete of `users/{uid}/**`). Spec: AD Data and Account Removed, Shared List Handling on Deletion; D1.
- [ ] 5.2.2 GREEN: deletion UI and strings; in-memory implementation for e2e. Spec: AD Deletion Entry Point With Confirmation; E2E Test-Only In-Memory Wiring.
- [ ] 5.2.3 GREEN: `README.md` (Spanish): stack, Firebase project setup (providers, SHA-1, real `google-services.json` placement in `app/src/{debug,release}/`), App Check manual checklist (enforcement in the console, Play Integrity in release, debug provider), emulator and `rules-tests` commands, the `e2e` build type. `openspec/config.yaml`: stack and testing entries for Firebase, remove any remaining frozen-backend text, add the `rules-tests` verify rule. Spec: FSR App Check Enforcement; CQG Documented Setup; CI Backend Removed (no dangling references); D15.
- [ ] 5.2.4 GREEN: `docs/secrets.md` final pass (no Firebase secret in repo; placeholder is inert); `bash scripts/test-secrets-contract.sh` and `bash scripts/check-secrets-contract.sh` pass. Spec: CI Placeholder Firebase Configuration in CI (Placeholder is inert).

### 5.3 TRIANGULATE, verify and commit

- [ ] 5.3.1 TRIANGULATE: deletion with no memberships, with 3 memberships (one sole), with an expired session needing reauth; `grep -rIn 'backend/' . --exclude-dir=.git --exclude-dir=openspec --exclude-dir=node_modules` still empty.
- [ ] 5.3.2 Final verification (end of chain): `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:assembleE2e spotlessCheck detekt`, `npm test` under the emulator, `actionlint`, `shellcheck scripts/*.sh`, the e2e suite, `test -e backend` fails, `git diff --stat` shows no `google-services.json` outside the placeholder. **[USER]** manual checks: real-device two-device sync, offline edit, sign-out warning, invite join across two accounts, account deletion, App Check enforced in the console.
- [ ] 5.3.3 Commit (two units): `feat(account): add in-app account and data deletion`, `docs: document Firebase setup, App Check and update the project config`.

---

## Traceability: spec requirement to tasks

| Requirement (capability) | Tasks |
|---|---|
| UA Sign-In Required | 2a.1.2, 2a.2.4, 2b.1.4, 2b.2.3 |
| UA Google Sign-In | 2b.1.3, 2b.2.1, 2b.2.2 |
| UA Email and Password Sign-In | 2b.1.1, 2b.2.1 |
| UA Password Reset | 2b.1.2, 2b.2.1, 2b.2.3 |
| UA Session Persistence | 2a.1.1 |
| UA Sign-Out Clears Local Cache | 3b.1.4, 3b.1.5, 3b.2.4 |
| UA Observable Auth State | 2a.1.1, 2a.2.1 |
| CBS Per-User Book Storage | 3b.1.1, 3b.1.3, 3b.2.1, 3b.2.3 |
| CBS Client-Generated String Identifiers | 1a.1.2, 1a.2.2 |
| CBS Multi-Device Sync | 3b.1.3, 3b.2.5, 3b.3.1 |
| CBS Offline Read and Non-Blocking Write | 0.1.2, 0.3.1, 3b.1.2, 3b.2.5 |
| CBS Last-Write-Wins | 3b.1.2 |
| CBS Client-Side Filter and Sort | 1a.1.1, 1a.2.1, 3b.1.2 |
| CBS Room Removed Without Import | 3c.1.1, 3c.2.1 |
| CBS Storage Behind a Port | 1a.2.1, 3b.1.2, 3b.1.6, 4b.1.3 |
| SBL Create List | 4a.1.1, 4a.2.1, 4b.1.1, 4b.1.4 |
| SBL Equal Members | 4a.1.2, 4b.1.4, 4b.1.5 |
| SBL Books Are Copies | 4a.1.4, 4b.1.1, 4b.1.3, 4b.2.2 |
| SBL Invite Codes | 4a.1.3, 4a.2.2, 4c.1.1 to 4c.1.4, 4c.2.* |
| SBL Remove Member | 4a.1.2, 4a.2.3, 4b.1.4 |
| SBL Delete List | 4a.1.5, 4b.1.2, 4b.2.1, 4b.2.4 |
| SBL Offline Behavior of Lists | 4b.1.2, 4b.1.4, 4c.1.1, 4c.2.2 |
| AD Deletion Entry Point With Confirmation | 5.1.3, 5.2.2 |
| AD Recent Authentication Before Any Deletion | 5.1.1 |
| AD Data and Account Removed | 5.1.1, 5.1.4, 5.2.1 |
| AD Shared List Handling on Deletion | 5.1.1, 5.1.2, 5.2.1 |
| AD Failure Handling | 5.1.1, 5.1.3 |
| FSR Default Deny and Authentication | 3a.1.1, 3a.2.1 |
| FSR Personal Book Access | 3a.1.1, 3a.2.1, 5.1.4 |
| FSR List Access by Membership | 4a.1.1, 4a.1.2, 4a.1.4, 4a.3.1 |
| FSR List Creation Rules | 4a.1.1, 4a.3.1 |
| FSR Member Entry Validation | 4a.1.2, 4a.3.1, 4c.1.3 |
| FSR Invite Code Enforcement | 0.3.2, 4a.1.3, 4a.3.1, 4c.2.3 |
| FSR Invite Field Validation | 4a.1.3, 4a.3.1 |
| FSR Data Validation and Size Limits | 3a.1.2, 3a.2.1, 4a.1.4 |
| FSR App Check Enforcement | 3b.2.2, 5.2.3 (console checklist, **[USER]**) |
| FSR Emulator Rules Tests | 3a.2.2, 3a.3.1, 4a.4.1 |
| BL String Book Identifier | 1a.1.3, 1a.1.4, 1a.2.1, 1b.1.1 |
| BL List Read Books | 1a.1.1, 1a.2.2 |
| BL Delete Book Entry | 1a.1.3, 1b.1.3, 1b.2.3 |
| BE Add Book Manually, Allow Duplicate Titles | 1a.1.2, 1b.2.1 |
| BE Edit Existing Book | 1b.1.1, 1b.1.2, 1b.2.2, 4b.1.5 |
| E2E Test-Only In-Memory Wiring | 2a.1.4, 2a.1.5, 2a.2.3, 2b.2.4, 3b.1.6, 4b.2.3 |
| E2E Test Tag Naming | 1b.1.3, 1b.1.4, 1b.2.3, 1b.2.4 |
| E2E Isolation and Offline Execution | 2a.1.3, 2a.2.3, 2a.2.5 |
| E2E Happy-Path Flow | 1b.2.4, 2a.2.5, 2a.3.1 |
| CI Rules Tests Job | 3a.1.3, 3a.2.2, 3a.3.1 |
| CI Placeholder Firebase Configuration in CI | 0.1.1, 0.2.1, 0.2.2, 0.3.1, 5.2.4 |
| CI Backend Removed From the Repository | B.1.*, B.2.*, B.3.1, 5.2.3, 5.3.1 |
| CI Unit Test Job, Artifact Contract, Report Generation and Publishing | 3a.2.2 and 2a.2.5 (job edits), `scripts/test-e2e-ci-config.sh` and `scripts/test-kover-summary.sh` as regression, B.3.1 |
| CI Single Required Check (ci-gate), Out of Scope Boundaries | 3a.1.3, 3a.2.2, 3a.2.3 |
| CI CodeQL Analysis Workflow, Dependabot Configuration | B.1.1, B.3.1 (verified unaffected; no backend entries remain, `actionlint`) |
| CI E2E Test Job | 2a.2.5 |
| CI Static Contract Checks Self-Tested and Run Locally | B.1.2, B.2.4, B.3.1, 5.2.4 |
| CI REMOVED Backend Frozen in CI | B.2.1, B.2.2 |
| CQG Formatting Gate, Pre-commit Hook, Detekt Gate With Baseline | B.2.2, B.3.1, 3c.2.2, 3c.3.1 |
| CQG Coverage Report | 3c.1.2, 3c.2.2, 3c.3.1 |
| CQG Documented Setup | B.2.5, 5.2.3 |
| BA all REMOVED requirements (13) | B.2.1 |

## Design decision coverage

D1 1a.2.1, 2a.2.1, 3b.2.1; D2 1a.1.2; D3 1a.1.1, 3b.1.2; D4 0.1.2, 3b.1.2; D5 3b.1.3; D6 3b.1.4; D7 4a.1.2, 4b.1.1; D8 4a.2.3, 4b.1.2, 5.1.2; D9 2a.*, 3b.1.6; D10 0.2.2; D11 1b.1.1, 1b.2.1; D12 4b.1.1, 4b.1.3, 5.1.2; D13 4a.1.3, 4c.1.1; D14 4a.1.3, 4c.1.2; D15 3b.2.2, 5.2.3.

## Risks and notes for apply

- Slice 0 is a gate: do not branch 1a until 0.3.3 is signed off. A failed invites `list` rule (0.3.2) changes 4a/4c.
- Slices 4a and 4b are the closest to the 800-line budget; each has an internal split line (rules vs domain, adapter vs UI).
- Slice 3c and B are deletion-heavy: report additions and deletions separately in the PR body so the 800-line budget counts reviewed lines only.
- Emulator integration tests (`*EmulatorIT`) must not run in the default `testDebugUnitTest`; gate them behind a Gradle property and a dedicated CI step, or limit them to the `rules-tests` job to keep `unit-tests` emulator-free.
- Shared files across slices: `ci.yml` (2a, 3a), `AppContainer.kt` (2a, 3b, 4b), `firestore.rules` (3a, 4a), `README.md` and `openspec/config.yaml` (B, 5). Single writer and the chain order avoid conflicts; B must land before 5.
- `chain_strategy` is not chosen: the orchestrator must ask the user (options A, B, C above) before apply.
