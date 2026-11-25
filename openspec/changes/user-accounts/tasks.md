# Tasks: User accounts with Google Sign-In (#26)

Strict TDD is active: in every unit the failing test (RED) comes before the implementation (GREEN), followed by triangulation and refactor. Context7 was unavailable at design time, so the RED tests also confirm library API names. Tasks flagged **[USER]** are manual out-of-repo steps owned by the user, never performed by apply.

Spec refs: `UA` = user-account, `BA` = backend-api, `E2E` = e2e-testing, `CI` = ci-pipeline (requirement names). Design refs: the Architecture Decisions, Image Smoke, Open Validator Risk and Open Questions sections of `design.md`.

## Review Workload Forecast

| Field | Value |
|---|---|
| Estimated changed lines | S1 ~650 (500-700); S2a ~430 (350-500); S2b ~560 (400-600); total ~1640 |
| 400-line budget risk | High: S1 and S2b each exceed 400, S2a is borderline, the total is about 4x |
| 800-line budget risk | Low per slice (each slice under 800); High if combined into one PR (~1640) |
| Chained PRs recommended | Yes: three slices, one PR each |
| Suggested split | PR 1 (S1) -> PR 2 (S2a) -> PR 3 (S2b) |
| Delivery strategy | ask-on-risk |
| Chain strategy | pending |

Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: pending
400-line budget risk: High
800-line budget risk: Low per slice, High combined

Decision needed: choose `stacked-to-main` or `feature-branch-chain` (tracker branch `feat/26-user-accounts`), or `size:exception`. S1 and S2b are over 400 but under the 800 review budget, and no cohesive split below 400 exists (S1 is one trust boundary: decoder, tests and wiring must land together to stay green). Do not shrink code to fit.

### Suggested Work Units

| Unit | Goal | Likely PR | Focused test command | Runtime harness | Rollback boundary |
|---|---|---|---|---|---|
| S1 | Backend Google ID token validation, bearer tests, OpenAPI, smoke, secrets doc | PR 1 (base: tracker or main) | `cd backend && ./gradlew test contractTest integrationTest verifyPackagedArtifact` | `bash scripts/test-image-smoke.sh`, `bash scripts/test-secrets-contract.sh` | Revert PR 1: restores deny-by-default 401 |
| S2a | App session core: ports, use cases, in-memory store, DataStore metadata, backup rules | PR 2 (base: PR 1 branch) | `./gradlew :app:testDebugUnitTest` | N/A: no UI or runtime entry point yet (nothing consumes the session) | Revert PR 2: removes unused domain/data code |
| S2b | Sign-in UI, Credential Manager, debug fake gateway, gate, release check, e2e update | PR 3 (base: PR 2 branch) | `./gradlew :app:testDebugUnitTest :app:assembleRelease` + `bash scripts/test-release-no-fake-auth.sh` | `cd e2e && npm test` against the debug APK (CI Appium job) | Revert PR 3, then PR 2, then PR 1 |

Commit rule for every unit: `lefthook` pre-commit and commit-msg hooks MUST run (never `--no-verify`); conventional commits, no AI attribution. Tests live in the same commit as the behavior they verify.

---

## Unit S1: Backend validation (PR 1, ~650 lines)

Start: all requests 401 (no auth wired, httpBasic test customizers). End: Google ID token validation with tests through a local JWKS, bearer contract tests, fail-fast config, image smoke, secrets doc.

### 1. Spikes and dependency (first)

- [ ] 1.1 Add `spring-boot-starter-oauth2-resource-server` to `backend/gradle/libs.versions.toml` and `backend/build.gradle.kts`; extend `verifyPackagedArtifact` to also flag `oauth2.resourceserver` properties. RED: a planted `spring.security.oauth2.resourceserver.jwt.secret` entry fails the task (BA Deny-by-Default: No runtime bypass).
- [ ] 1.2 SPIKE (OpenAPI validator key): add `bearerAuth` + global `security` to `backend/src/main/resources/openapi.yaml`; run the `ListBooksTest` 401 case and record the actual key from the 3.0.0 jar (expected `validation.request.security.missing`). Have `api(authenticated = false)` in `ContractTestBase` ignore only that key with responses still validated; confirm bearer requests pass both validators. Spec: BA OpenAPI Bearer Security Scheme (Scheme declared, 401 declared bodiless).
- [ ] 1.3 SPIKE (JWKS outage): RED test with a decoder pointed at a dead loopback port; assert the unwrapped default yields 500 (`AuthenticationServiceException`), proving `FailClosedJwtDecoder` is needed. Spec: BA Bodiless Fail-Closed (JWKS outage fails closed).
- [ ] 1.4 SPIKE (Spring Security 7 API names): RED compile/run test for `oauth2ResourceServer { jwt { jwtDecoder } }`, `HttpStatusEntryPoint(401)`, `NimbusJwtDecoder.withJwkSetUri(..).jwsAlgorithm(RS256)`, `JwtTimestampValidator`, whether a default `typ` header validator exists (test tokens set `typ: JWT`), and whether Nimbus JWKS refetch rate-limiting breaks "recovery after outage". Record findings in apply evidence. Design Open Questions 2 and 3.

### 2. Test support: local JWKS (RED then GREEN)

- [ ] 2.1 RED: `TestJwksTest` in `backend/src/test/.../testsupport/`: a minted token verifies against the served JWKS; an unknown-key token does not; the JWKS URL is loopback with an ephemeral port. Expected: compile error.
- [ ] 2.2 GREEN: create `TestJwks.kt` (runtime RSA key, JDK `HttpServer` on ephemeral loopback port, Nimbus minting with issuer/audience/exp/nbf/sub/alg overrides) and `TestJwtSupport.kt` (`DynamicPropertyRegistrar` overriding only `jwk-set-uri`) under `backend/src/test/.../testsupport/` and the same under `backend/src/contractTest/.../testsupport/`. Spec: BA Contract Tests (Offline token tests).
- [ ] 2.3 Create test-only `backend/src/test/resources/config/application.yaml`, `backend/src/contractTest/resources/config/application.yaml` and new `backend/src/integrationTest/resources/config/application.yaml` with the two issuers and a non-secret test audience (so the full `@SpringBootTest` in `JdbcBookRepositoryTest` passes startup validation). Spec: BA Auth Config Required (Test contexts satisfy the validation).

### 3. Validator and decoder (RED then GREEN)

- [ ] 3.1 RED: `GoogleIdTokenValidationTest` (MockMvc + `TestJwks`), one method per case: valid; both issuer forms; wrong issuer; wrong audience; expired; `nbf` in the future; foreign key; `alg: none`; HS256; missing `sub`; blank `sub`; malformed; Basic credentials; no header. Each rejection asserts 401, empty body, no `WWW-Authenticate`. Spec: BA Google ID Token Validation (all scenarios except Owner is sub), Bodiless (401 has no body), Deny-by-Default (Valid token passes, HTTP Basic no longer accepted, Unauthenticated, Unknown path denied, Health is public), Contract Tests (Token-validation matrix).
- [ ] 3.2 RED: JWKS outage then recovery cases (token valid, JWKS stopped -> 401; JWKS restarted -> 200, no rate-limit lockout). Spec: BA Bodiless (JWKS outage fails closed, Recovery after outage).
- [ ] 3.3 GREEN: create `config/GoogleAuthProperties.kt` (`librosleidos.auth.google.{issuers,audience,jwk-set-uri}`, defaults = Google issuers and JWKS, audience from `${GOOGLE_OAUTH_WEB_CLIENT_ID:}`) and `config/GoogleIdTokenDecoder.kt` (RS256-only factory, timestamp + issuer-in-set + audience-contains + non-blank `sub` validators, `FailClosedJwtDecoder`, 2 s connect/read timeouts, `sub` principal converter). Tests 3.1 and 3.2 pass except wiring.
- [ ] 3.4 GREEN: rewrite `config/SecurityConfig.kt` with `oauth2ResourceServer { authenticationEntryPoint = HttpStatusEntryPoint(401); jwt { jwtDecoder } }`; delete `config/ApiAuthenticationCustomizer.kt`, `testsupport/TestApiAuthentication.kt`, `testsupport/ContractTestAuthentication.kt`. Keep stateless and health public. Tests 3.1 and 3.2 now pass.
- [ ] 3.5 TRIANGULATE: mutate each validator (drop audience, drop issuer, allow HS256, drop `sub` check, drop fail-closed wrapper); each fails a named case; revert. Add `typ`/algorithm-confusion variant if 1.4 found a typ validator.

### 4. Startup validation (RED then GREEN)

- [ ] 4.1 RED: `GoogleAuthConfigurationValidatorTest` (`ApplicationContextRunner`): blank audience fails naming the setting; blank issuers fails; blank JWKS URI fails; whitespace-only counts as blank; message holds no configured value (use real-looking values for others); non-https, non-loopback JWKS URI rejected; with both auth and DB settings missing only the auth message appears (auth first). Spec: BA Auth Config Required (Audience unconfigured, Issuer unconfigured, Values not echoed).
- [ ] 4.2 GREEN: create `config/GoogleAuthConfigurationValidator.kt` (`BeanFactoryPostProcessor`, `MissingAuthConfigurationException`, message "Missing required authentication configuration: <names>") and the https-or-loopback JWKS check in the decoder factory. Confirm ordering against `DataSourceCredentialsValidator`.
- [ ] 4.3 RED then GREEN: rename `NoDefaultUserTest.kt` to `ProductionAuthContextTest.kt`, pass the test audience with `withPropertyValues`; assert no `UserDetailsService`, exactly one `JwtDecoder`, no HTTP Basic, and failure with the auth message when the audience is absent. Spec: BA Deny-by-Default (No runtime bypass); Auth Config (Test contexts satisfy).

### 5. Wiring existing tests, OpenAPI, actuator

- [ ] 5.1 RED then GREEN: migrate `BooksWebTest`, `UnexpectedErrorTest`, `SecuritySliceTest` (stays green) and `ContractTestBase` to bearer tokens from `TestJwks`; delete httpBasic use. Spec: BA Contract Tests (Response matches schema, Contract drift fails, Untested operation fails coverage).
- [ ] 5.2 RED: per-operation contract tests in `backend/src/contractTest/...`: Owner is sub (created book `owner_id` equals `sub`); cross-user read 404; cross-user list/update/delete leave A's book unchanged; new install (second fresh token, same `sub`) lists existing books; separate methods for 401 and 400. GREEN with the 1.2 validator config. Spec: BA Owner is sub, Cross-user read denied, Cross-user list update and delete denied, New install lists existing books.
- [ ] 5.3 RED then GREEN: update `AuthenticatedActuatorTest.kt`: the actuator `env`, `beans` and `info` endpoints return 401 without a token and 404 with a valid bearer; health body has no details. Spec: BA Actuator Exposes Only Health.
- [ ] 5.4 GREEN: finalize `openapi.yaml` (`bearerAuth` http bearer JWT, global `security`, 401 declared with no content on every books operation, the actuator health endpoint excluded). Spec: BA OpenAPI Bearer Security Scheme.
- [ ] 5.5 RED: `verifyPackagedArtifact` asserts `testsupport`, `TestJwks`, and test key material are absent from the jar; plant one and confirm failure, then GREEN. Spec: BA Deny-by-Default (Test key stays in tests); Hardened Image (No test authentication support in image).

### 6. Image smoke and secrets doc

- [ ] 6.1 RED: in `scripts/test-image-smoke.sh` generalize `expect_startup_failure` with a message argument; add case `no-auth-audience` (valid-looking dummy DB vars, no client ID; expects non-zero exit, logs contain "Missing required authentication configuration" and `GOOGLE_OAUTH_WEB_CLIENT_ID`, no dummy value in logs). Run against the image before 4.2: expected failure.
- [ ] 6.2 GREEN: add `-e GOOGLE_OAUTH_WEB_CLIENT_ID=smoke-web-client-id.apps.googleusercontent.com` to the happy path and to `no-datasource` and `blank-password` so only the DB validator fails there. Run `bash scripts/test-image-smoke.sh` and `shellcheck`. Spec: BA Hardened Image (Image starts, Image fails without credentials).
- [ ] 6.3 RED then GREEN: add a fixture to `scripts/test-secrets-contract.sh` where a `JWT_SIGNING_SECRET` row must be absent and a client-ID row must be absent; then delete the `JWT_SIGNING_SECRET` row (line 25) in `docs/secrets.md`. No change to `scripts/check-secrets-contract.sh` unless the fixture proves it necessary. Spec: CI Secret Inventory (Obsolete signing secret removed, Client IDs not inventoried, Secrets contract check still passes).
- [ ] 6.4 Verify the full backend suite: `cd backend && ./gradlew test contractTest integrationTest verifyPackagedArtifact`, then `bash scripts/test-image-smoke.sh`, `bash scripts/test-secrets-contract.sh`, `actionlint`.
- [ ] 6.5 Commit S1 (for example `feat(backend): validate Google ID tokens as an OAuth2 resource server`). Lefthook hooks run; report the summary as evidence. Open PR 1.

---

## Unit S2a: App session core (PR 2, ~430 lines)

Start: PR 1 merged or stacked. End: domain ports/use cases, in-memory session store, DataStore metadata, backup exclusion, all JVM and Robolectric tested; no UI yet.

### 7. Domain (RED then GREEN)

- [ ] 7.1 RED: `SignInUseCaseTest`, `SignOutUseCaseTest`, `ObserveSessionUseCaseTest` in `app/src/test/.../domain/usecase/` with fake `AuthGateway` and `SessionStore` and Turbine: SignIn success stores token + account; failure results (`Cancelled`, `NoCredential`, `NotConfigured`, `Failure`) leave the store unchanged; SignOut clears the store then calls `gateway.signOut()`, is idempotent, and still clears when the gateway throws; ObserveSession emits signed-out, signed-in, signed-out. Spec: UA Ports and Use Cases (SignIn stores the session, SignIn failure stores nothing); UA Non-Sensitive Metadata (Session observed); UA Sign-Out (Sign-out is idempotent).
- [ ] 7.2 GREEN: create `app/src/main/java/com/librosleidos/domain/model/{Session,Account,IdToken}.kt`, `domain/port/{AuthGateway,SessionStore}.kt`, `domain/usecase/{SignIn,SignOut,ObserveSession}UseCase.kt` per the design interfaces (`IdToken.toString()` masked).
- [ ] 7.3 RED then GREEN: `DomainIsAndroidFreeTest` greps `domain` imports for `android.*`, `androidx.*` or Credential Manager (planted import fails); `IdTokenTest` asserts `toString` does not contain the value. Spec: UA Domain is Android-free; UA In-Memory Token Only (Token not logged).

### 8. Data (RED then GREEN)

- [ ] 8.1 RED: `InMemorySessionStoreTest`: initial signed-out; save then clear gives signed-out/signed-in/signed-out; `currentToken()` null once cleared; metadata alone without a token is signed out (store built with pre-existing metadata). Spec: UA Token read through the session; UA Metadata alone is not a session; UA Process restart requires sign-in again.
- [ ] 8.2 RED (Robolectric): `DataStoreAccountMetadataStoreTest`: after `save`, the DataStore file contains display name and email and not the token value (scan the file bytes and all preference files); `clear()` leaves it empty. Spec: UA Metadata persisted; UA Token absent from persistent storage; UA Sign-out clears memory and storage.
- [ ] 8.3 GREEN: create `data/auth/{InMemorySessionStore,AccountMetadataStore,DataStoreAccountMetadataStore}.kt`, add `datastore-preferences` to the version catalog and `app/build.gradle.kts`, wire in `di/AppContainer.kt` (write-through: store writes metadata, never token).
- [ ] 8.4 RED then GREEN (Robolectric/XML test): `BackupRulesTest` parses `res/xml/backup_rules.xml` and `data_extraction_rules.xml` and asserts the account DataStore file is excluded from cloud backup and device transfer; create both XML files and set `android:fullBackupContent` and `android:dataExtractionRules` in `AndroidManifest.xml`. Spec: UA Account Data Excluded From Backup.
- [ ] 8.5 REFACTOR and verify: `./gradlew :app:testDebugUnitTest spotlessCheck detekt` (or the repo's lint tasks) clean.
- [ ] 8.6 Commit S2a (for example `feat(app): add session core with in-memory token and account metadata`). Lefthook hooks run. Open PR 2.

---

## Unit S2b: Sign-in UI, Credential Manager, fake gateway, e2e (PR 3, ~560 lines)

Start: PR 2 merged or stacked. End: required sign-in gate, both gateways, release-no-fake guard, updated Appium flow.

### 9. Gateway and config (RED then GREEN)

- [ ] 9.1 RED (SPIKE, androidx.credentials API names): `CredentialManagerAuthGatewayTest` with a mocked `CredentialManager`: request uses `GetGoogleIdOption(serverClientId = webId, filterByAuthorizedAccounts = false)`; `GetCredentialCancellationException` -> `Cancelled`; `NoCredentialException` -> `NoCredential`; other exception -> `Failure`; blank Web client ID -> `NotConfigured` without calling Credential Manager; a `GoogleIdTokenCredential` maps to `Success(token, account)`. Expected: compile error, which confirms credentials 1.6.0 / googleid 1.2.1 names. Spec: UA Credential Manager (Web client ID requested, User cancels, No credential available, Gateway failure is safe); UA OAuth Config (Blank Web client ID at sign-in).
- [ ] 9.2 GREEN: add credentials 1.6.0, `play-services-auth`, googleid 1.2.1 and a `GOOGLE_WEB_CLIENT_ID` BuildConfig field (default blank, from Gradle property or env) to `app/build.gradle.kts` and the version catalog; create `data/auth/CredentialManagerAuthGateway.kt` and `data/auth/CurrentActivityProvider.kt` (`WeakReference` via `ActivityLifecycleCallbacks`). Spec: UA OAuth Config (Blank IDs build).
- [ ] 9.3 RED: `AuthGatewayFactoryTest` for debug (blank ID -> `FakeAuthGateway`; set ID -> `CredentialManagerAuthGateway`) in `src/testDebug`, and for release (always `CredentialManagerAuthGateway`, with and without an ID) in `src/testRelease`. Spec: UA Debug-Only Fake Gateway (Debug without a client ID uses the fake gateway, Debug with a client ID uses real sign-in, Release always uses real sign-in).
- [ ] 9.4 GREEN: create `app/src/debug/.../data/auth/FakeAuthGateway.kt` (synthetic token and account, no network), `app/src/debug/.../di/AuthGatewayFactory.kt` and `app/src/release/.../di/AuthGatewayFactory.kt`; wire the factory in `di/AppContainer.kt`.
- [ ] 9.5 RED then GREEN: `scripts/test-release-no-fake-auth.sh` (checker + harness with a debug positive control): fails when `FakeAuthGateway` appears in non-debug sources or in release bytecode (dex/class scan after `assembleRelease`), passes on the clean release, and asserts the release network security config has no cleartext. Add a planted-class case and a debug positive control. Wire into `.github/workflows/ci.yml` and `scripts/test-e2e-ci-config.sh`; run `actionlint`. Spec: UA Release has no fake gateway, Release test guards the boundary.
- [ ] 9.6 RED then GREEN: network security tests: `src/debug/res/xml/network_security_config.xml` permits cleartext only for `10.0.2.2`, with a debug manifest overlay; release config permits none. Spec: UA Debug cleartext scoped to the emulator host, Release forbids cleartext.

### 10. Sign-in UI and gate (RED then GREEN)

- [ ] 10.1 RED (Robolectric + Compose): `SignInViewModelTest` and `SignInScreenTest`: states idle, loading, error with retry for `Cancelled`, `NoCredential`, `NotConfigured`, `Failure`; the displayed text contains no exception class name, stack trace or token (use a throwing gateway with a distinctive message); retry re-invokes sign-in. Spec: UA Credential Manager (User cancels, No credential available, Gateway failure is safe); UA OAuth Config (Blank Web client ID at sign-in).
- [ ] 10.2 GREEN: create `ui/account/{SignInRoute,SignInScreen,SignInUiState,SignInViewModel,SignInTags,SessionViewModel}.kt` and `strings.xml` entries; tags follow `<screen>_<element>`. Spec: E2E Sign-In Screen Testability (Sign-in screen tagged).
- [ ] 10.3 RED: `SessionGateTest`: signed-out first frame shows only the sign-in screen and no book list/add/edit is reachable; after a successful fake sign-in the book list is shown; sign-out returns to sign-in and drops the back stack; process restart (new store) is signed out. Spec: UA Required Sign-In (First start shows sign-in, Successful sign-in opens the library, Process restart requires sign-in again); UA Sign-Out (Sign-out clears memory and storage).
- [ ] 10.4 GREEN: modify `MainActivity.kt` (gate on `ObserveSession`; move `Modifier.e2eRoot()` to a root `Box` under `RinconDeLecturaTheme` so it covers both branches) and `LibrosLeidosApp.kt`; add a sign-out action. Debug and release tag exposure stay per "Debug-Only Resource-Id Exposure".
- [ ] 10.5 TRIANGULATE: mutate the gate (render `NavHost` signed-out; keep session after `clear`); each fails a named test; revert.

### 11. e2e update (RED then GREEN)

- [ ] 11.1 RED: update `e2e/src/pages/tags.ts` and create `e2e/src/pages/signIn.page.ts` (the only place with sign-in selectors); change `e2e/.../bookLifecycle.e2e.ts` to assert the sign-in screen first, sign in, then run the existing lifecycle; add the reset (clear data) and a "sign-in gates the flow" test. Expected failure against the pre-S2b debug APK. Spec: E2E Happy-Path (Full flow passes, Sign-in gates the flow); E2E Isolation (Data reset between tests).
- [ ] 11.2 GREEN: with the S2b debug APK, run the Appium suite locally or let the CI job run; add a test or lint check that sign-in selector strings appear only in `signIn.page.ts` (E2E Sign-in selectors encapsulated); no network used and no Google call (E2E No network needed, Sign-in never reaches Google). APK override env var unchanged (E2E APK override).
- [ ] 11.3 Verify: `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease`, `bash scripts/test-release-no-fake-auth.sh`, `bash scripts/test-e2e-ci-config.sh`, `actionlint`.
- [ ] 11.4 Commit S2b (for example `feat(app): require Google sign-in with debug fake gateway`). Lefthook hooks run. Open PR 3.

---

## User-owned manual steps (out of repo)

These block only REAL sign-in verification (V.2), not CI, builds, the fake-gateway e2e or any PR merge. Blank client IDs keep every build green.

- [ ] U.1 **[USER]** Create a Google Cloud project for the app.
- [ ] U.2 **[USER]** Configure the OAuth consent screen (app name, support email, scopes `openid`, `email`, `profile`; add test users while in Testing mode).
- [ ] U.3 **[USER]** Create the OAuth client of type Web application; note the Web client ID (non-secret). Set it as `GOOGLE_OAUTH_WEB_CLIENT_ID` for the backend and as the app `GOOGLE_WEB_CLIENT_ID` build value. Deployment (#8) must also set the backend variable, otherwise it refuses to start by design.
- [ ] U.4 **[USER]** Create the OAuth client of type Android with the package name from `app/build.gradle.kts` and the debug keystore SHA-1 (`keytool -list -v -keystore ~/.android/debug.keystore`). The release keystore SHA-1 is out of scope (#8).

## Post-merge verification

- [ ] V.1 Full CI green on each PR (`backend-tests`, contract, unit, e2e job, `secret-scan`, `ci-gate`).
- [ ] V.2 **[USER]** After U.1 to U.4: local debug build with the Web client ID set and the backend started with `GOOGLE_OAUTH_WEB_CLIENT_ID`; sign in with a real Google account against `http://10.0.2.2`; list books; sign out. Covers real Credential Manager behavior only. Spec: UA Credential Manager (Web client ID requested) in real conditions.
- [ ] V.3 Archive note: the backend-api main spec Purpose line becomes "Deployment and secrets (#54) and package visibility (#8) are out of scope; authentication is Google ID token validation (#26)".

---

## Traceability (spec scenario -> task)

UA Required Sign-In: 10.3. UA Credential Manager: 9.1 (Web client ID, cancel, no credential, safe failure), 10.1. UA In-Memory Token: 8.2 (absent), 7.3 (not logged), 8.1 (read via session). UA Metadata: 8.2, 8.1, 7.1. UA Sign-Out: 7.1 (idempotent), 8.2, 10.3. UA Ports: 7.1, 7.3. UA Fake Gateway: 9.3, 9.4, 9.5. UA OAuth/Cleartext: 9.2, 9.1, 9.6. UA Backup: 8.4.
BA Token Validation: 3.1 (all but Owner is sub), 5.2 (Owner is sub). BA Fail-Closed: 3.1 (401 no body), 3.2. BA Startup Config: 4.1, 4.2, 4.3, 2.3. BA OpenAPI: 1.2, 5.4. BA Deny-by-Default: 3.1, 3.4, 4.3, 1.1, 5.5. BA Contract Tests: 5.1, 5.2, 3.1, 2.2. BA Actuator: 5.3. BA Image: 6.1, 6.2, 5.5.
E2E Sign-In Testability: 10.2, 11.2. E2E Happy-Path: 11.1. E2E Isolation: 11.1, 11.2.
CI Secret Inventory: 6.3 (Obsolete row, Client IDs, Contract check still passes); Row fields, Planned rows, Runbook and cadence, Google Books key gated are unchanged by this change and stay covered by the existing `scripts/test-secrets-contract.sh` run in 6.3/6.4.

## Dependencies and order

- Sequential chain: S1 -> S2a -> S2b. S2a does not need S1 code and could run in a separate worktree, but a single writer is the default and e2e in S2b needs both.
- Inside each unit: spikes, RED, GREEN, triangulate/refactor, commit.
- Revert order: S2b, S2a, S1. No data migration.
