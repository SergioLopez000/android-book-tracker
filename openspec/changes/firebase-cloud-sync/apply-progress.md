# Apply progress: firebase-cloud-sync

## Slice B (backend deletion): B.1.1-B.3.1 complete; B.3.2 (commit/PR) left to the orchestrator
- RED: `test -e backend` succeeded; workflow grep empty; `backend/` grep listed 8 hits (README, build.gradle.kts, .gitignore, 3 scripts, kover fixture, image smoke). Secrets contract scripts green at baseline.
- GREEN: removed `backend/`, 3 backend-only scripts; edited lefthook.yml, build.gradle.kts, .gitignore, issue templates, docs/secrets.md (GOOGLE_BOOKS_API_KEY row untouched), README.md, openspec/config.yaml.
- Checks: absence checks pass; actionlint, shellcheck, secrets/e2e/kover scripts, `:app:testDebugUnitTest`, `spotlessCheck`, detekt CLI all exit 0 (no `detekt` Gradle task exists; it is the brew CLI).

## Slice 0 (build spike and STOP gate): 0.1.1-0.3.3 complete; 0.3.4 (commit/PR) left to the orchestrator
Mode: Strict TDD. Work unit: slice 0. Review size: about 212 reviewed lines (37 modified + 175 new), plus generated `firebase/package-lock.json` (8056 lines, not reviewed).

### TDD Cycle Evidence
| Task | Test file | Layer | Safety net | RED | GREEN | TRIANGULATE | REFACTOR |
|------|-----------|-------|------------|-----|-----|-------------|----------|
| 0.1.1 / 0.2.x | `app/src/test/.../data/firebase/FirebaseSpikeTest.kt` | Unit (Robolectric) | 128/128 baseline (`:app:testDebugUnitTest` exit 0) | Written; `compileDebugUnitTestKotlin` failed with `Unresolved reference 'FirebaseApp'` (no dependency) | 2/2 pass after BoM, plugin and placeholder | 2 cases (project id; key not `AIza`, package name) | None needed |
| 0.1.2 | `app/src/test/.../data/firebase/FirestoreOfflineSpikeTest.kt` | Integration (Firestore emulator, skipped without `FIRESTORE_EMULATOR_HOST`) | N/A (new) | Written; compile failure (no dependency), then two real emulator failures (`Tasks.await` on main thread; `useEmulator` after init) | 2/2 pass on the emulator | 2 cases (offline set + cache read; ack after `enableNetwork()`) | Replaced blocking await with a looper-draining await; named FirebaseApp per test |

### Work Unit Evidence
| Evidence | Value |
|---|---|
| Focused test command and result | `./gradlew :app:testDebugUnitTest --tests '*FirebaseSpikeTest'`: 2 tests, 0 failures. Full suite: 130 tests, 0 failures, 2 skipped (emulator-gated) without the emulator |
| Runtime harness | `JAVA_HOME=<jdk21> firebase/node_modules/.bin/firebase emulators:exec --only firestore --project demo-libros <scratchpad>/inner.sh` where inner.sh resets `JAVA_HOME` to JDK 17 and runs `./gradlew :app:cleanTestDebugUnitTest :app:testDebugUnitTest --tests '*FirestoreOfflineSpikeTest' --no-build-cache`: script exit 0, 2 tests, 0 failures, 0 skipped |
| Rollback boundary | Revert `gradle/libs.versions.toml`, `build.gradle.kts`, `app/build.gradle.kts`, `app/google-services.json`, `.gitignore` entries and `app/src/test/.../data/firebase/`; `firebase/` is independent |

### STOP gate (0.3.1)
- (a) PASS: `com.google.gms.google-services` 4.6.0 applies on AGP 9.5.3; `./gradlew :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest` BUILD SUCCESSFUL (`processDebugGoogleServices` and `processReleaseGoogleServices` ran).
- (b) PASS: offline `set()` returns an incomplete Task, cache read returns the value, ack arrives after `enableNetwork()`.
- (c) PASS: a dummy `app/src/debug/google-services.json` (project id `dummy-debug-override`) won for debug (`processDebugGoogleServices` values.xml), release kept `demo-libros`; after removing the dummy, debug returned to `demo-libros`. The path is gitignored.
- (d) PASS locally: `gitleaks dir app/google-services.json` and `gitleaks dir .` (v8.30.1, same as CI) report no leaks; positive control with an `AIza...` key in a scratch copy is flagged. The CI `secret-scan` run remains the final confirmation.
- 0.3.2 PASS: `list` on `invites` constrained by `listId == X` is allowed for a member under `resource.data.listId`; 6/6 cases. Command (throwaway, scratchpad `rules-spike/`, `@firebase/rules-unit-testing` installed only there): `JAVA_HOME=<jdk21> firebase/node_modules/.bin/firebase emulators:exec --only firestore --project demo-libros "node invites.test.js"`. No fallback needed; slices 3a/4a/4c unchanged.

### Decisions and notes
- Versions: Firebase BoM 35.2.0, google-services 4.6.0, androidx.credentials and credentials-play-services-auth 1.6.0, googleid 1.2.1, kotlinx-coroutines-play-services 1.11.0 (shares the coroutines version). `credentials-play-services-auth` was added beyond the task list because Credential Manager needs it on API below 34.
- `firebase-appcheck-debug` is `debugImplementation`; Play Integrity is `implementation`.
- Emulator needs JDK 21 (firebase-tools 15.x refuses older); Gradle stays on JDK 17 by resetting `JAVA_HOME` inside the `emulators:exec` script. No `firebase.json` was created (emulator defaults suffice); it stays in slice 3a.
- `firebase/package.json` (firebase-tools 15.37.0) was brought forward from slice 3a by the orchestrator; 3a adds `@firebase/rules-unit-testing`.
- Gates: `./gradlew spotlessCheck :app:lintDebug`, detekt CLI, `bash scripts/test-e2e-ci-config.sh`, `bash scripts/test-secrets-contract.sh` all exit 0.
- New untracked files from this slice: `app/google-services.json`, `app/src/test/java/com/librosleidos/data/firebase/FirebaseSpikeTest.kt`, `app/src/test/java/com/librosleidos/data/firebase/FirestoreOfflineSpikeTest.kt` (plus the orchestrator's `firebase/package.json` and `firebase/package-lock.json`).
- Deviations from design: none. Open Questions 1 and 2 resolved in design.md.
