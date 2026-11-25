# Design: User accounts with Google Sign-In (#26)

## Technical Approach

Backend: replace the unused `ApiAuthenticationCustomizer` hook with a built-in `oauth2ResourceServer` that has one `JwtDecoder` and an explicit Google validator chain. Tests reach that same decoder through a local JWKS instead of using their own auth path. App: domain ports with adapters in `data/auth`. A session gate above the `NavHost` controls access, and a debug or release source-set factory picks the gateway, following the `E2eRoot` precedent. Context7 was unavailable, so the S1 and S2b RED tests must confirm the API names below.

## Architecture Decisions

| Decision | Choice | Rejected | Rationale |
|---|---|---|---|
| Auth wiring | `SecurityConfig` sets `oauth2ResourceServer { authenticationEntryPoint = HttpStatusEntryPoint(401); jwt { jwtDecoder = ... } }`. Delete `ApiAuthenticationCustomizer` and both httpBasic test customizers | Keep the customizer hook | One auth path. The hook let tests authenticate through a different mechanism |
| Config | `GoogleAuthProperties` reads `librosleidos.auth.google.{issuers, audience, jwk-set-uri}`. The defaults are the Google issuers and JWKS, and the audience comes from `${GOOGLE_OAUTH_WEB_CLIENT_ID:}` | A `@Primary` test decoder; Boot `issuer-uri` | Tests run the production validators and only swap the key source. `issuer-uri` does network I/O and does not check the audience |
| Fail fast | `GoogleAuthConfigurationValidator` is a `BeanFactoryPostProcessor`. It reads the `Environment` before any bean is created and throws `MissingAuthConfigurationException` with the message "Missing required authentication configuration: <names>". Blank issuers, audience or JWKS URI each count as missing. The decoder factory also rejects a JWKS URI that is neither https nor loopback | Checking at decoder bean creation; checking at request time | The order is defined: the auth check always runs before `DataSourceCredentialsValidator`, which runs when the `JdbcConnectionDetails` bean is built. If both are missing, only the auth message appears. Names only, never values |
| Validators | `RS256` only (`jwsAlgorithm`), plus `JwtTimestampValidator`, an issuer check on `getClaimAsString(ISS)` in the set, an audience-contains check, and a non-blank `sub`. `JwtAuthenticationConverter` sets the principal claim to `sub` | `JwtValidators.createDefaultWithIssuer` | That helper accepts only one issuer, and Google uses two forms |
| JWKS outage | `FailClosedJwtDecoder` wraps the decoder and turns any non-`BadJwtException` `JwtException` into a `BadJwtException`. The JWKS `RestTemplate` gets 2 s connect and read timeouts | Default behaviour | Spring maps a JWKS failure to `AuthenticationServiceException`, and the entry-point failure handler rethrows it as a 500. The spec requires a bodiless 401 |
| Test keys | `TestJwks` generates an RSA key at runtime, serves it from a JDK `HttpServer` on an ephemeral loopback port, and mints tokens with Nimbus. The issuers and a test audience are static, in a test-only `src/{test,contractTest}/resources/config/application.yaml`, so they are visible to the validator. A `DynamicPropertyRegistrar` bean in `TestJwtSupport` overrides only `jwk-set-uri` with the local JWKS | A committed PEM; the `jwt()` helper; a `@Primary` test decoder | No key material in the repo. `jwt()` bypasses the decoder. Tests never replace the production decoder |
| App gate | `MainActivity` renders `SignInRoute` or `{TopBar + NavHost}` from `ObserveSession`. `Modifier.e2eRoot()` moves from the `Scaffold` to a root `Box` directly under `RinconDeLecturaTheme`, so it wraps both branches and the sign-in tags become resource ids | A sign-in nav destination; `e2eRoot` only on the `Scaffold` | Signed out, book routes do not exist. Sign-out drops the back stack. The sign-in screen stays reachable for Appium |
| Activity context | `CurrentActivityProvider` holds a `WeakReference` through `ActivityLifecycleCallbacks` | An Android `Context` in the port | Credential Manager needs an activity, the domain stays Android-free, and nothing leaks |
| Gateway selection | `di/AuthGatewayFactory.kt` exists in debug and in release. Debug returns `FakeAuthGateway` when `GOOGLE_WEB_CLIENT_ID` is blank (CI) and the real gateway otherwise. Release always returns the real one | A runtime flag or intent extra | Release has no bypass path. A local debug build with a configured ID can still test real sign-in against `10.0.2.2` |
| Sign-in request | `GetGoogleIdOption(serverClientId = web ID, filterByAuthorizedAccounts = false)`. No auto sign-in on start | Auto-select on start | Matches the spec's "Google ID option". The e2e can assert the sign-in screen deterministically |

## Data Flow

    SignInButton -> SignInViewModel -> SignInUseCase -> AuthGateway (CredMan | Fake)
                                              | Success(token, account)
                                              v
                           InMemorySessionStore --write-through--> AccountMetadataStore (DataStore, no token)
                                              | StateFlow<Session>
    MainActivity gate <- SessionViewModel <- ObserveSessionUseCase
    App --Bearer--> SecurityFilterChain -> FailClosedJwtDecoder -> Nimbus(JWKS) -> validators -> Authentication.name = sub -> BooksController

## File Changes

| Slice | Files | Action |
|---|---|---|
| S1 | `backend/gradle/libs.versions.toml`, `backend/build.gradle.kts` | Add `spring-boot-starter-oauth2-resource-server`. `verifyPackagedArtifact` also flags `oauth2.resourceserver` properties |
| S1 | `config/SecurityConfig.kt`; new `config/GoogleAuthProperties.kt`, `GoogleIdTokenDecoder.kt` (factory, validators, `FailClosedJwtDecoder`) | Modify/Create |
| S1 | `config/ApiAuthenticationCustomizer.kt`, `testsupport/TestApiAuthentication.kt`, `testsupport/ContractTestAuthentication.kt` | Delete |
| S1 | `application.yaml`, `openapi.yaml` (`bearerAuth`, global `security`, 401 stays bodiless) | Modify |
| S1 | `config/GoogleAuthConfigurationValidator.kt` (the `BeanFactoryPostProcessor`) | Create |
| S1 | `src/{test,contractTest}/.../testsupport/{TestJwks,TestJwtSupport}.kt`, `src/{test,contractTest}/resources/config/application.yaml`; `ContractTestBase`; `BooksWebTest`, `UnexpectedErrorTest`, `SecuritySliceTest` | Create/Modify |
| S1 | `AuthenticatedActuatorTest.kt`: a bearer from `TestJwks`. Without a token, `/actuator/{env,beans,info}` return 401. With a valid bearer they return 404 | Modify |
| S1 | `src/integrationTest/resources/config/application.yaml` (new folder): test issuers and a non-secret test audience, so the full `@SpringBootTest` in `JdbcBookRepositoryTest` passes the startup auth validation (`build.gradle.kts:92` runs `integrationTest` in CI) | Create |
| S1 | Rename `NoDefaultUserTest.kt` to `ProductionAuthContextTest.kt`. `ApplicationContextRunner` does not load `application.yaml`, so the test audience is passed with `withPropertyValues`. With it there is no `UserDetailsService`, exactly one `JwtDecoder`, and HTTP Basic is not configured. Without the audience the context fails with the auth message | Rename/Rewrite |
| S1 | `scripts/test-image-smoke.sh` (see Image smoke below) | Modify |
| S1 | `docs/secrets.md`: drop only the `JWT_SIGNING_SECRET` row (line 25). No change to `scripts/check-secrets-contract.sh` or its test, since neither asserts that row | Modify |
| S2a | `domain/model/{Session,Account,IdToken}.kt`, `domain/port/{AuthGateway,SessionStore}.kt`, `domain/usecase/{SignIn,SignOut,ObserveSession}UseCase.kt` | Create |
| S2a | `data/auth/{InMemorySessionStore,AccountMetadataStore,DataStoreAccountMetadataStore}.kt`, `di/AppContainer.kt`, version catalog (`datastore-preferences`) | Create/Modify |
| S2a | `res/xml/{backup_rules,data_extraction_rules}.xml` and manifest attributes | Create/Modify |
| S2b | `data/auth/{CredentialManagerAuthGateway,CurrentActivityProvider}.kt`; `src/{debug,release}/.../di/AuthGatewayFactory.kt`; `src/debug/.../data/auth/FakeAuthGateway.kt` | Create |
| S2b | `ui/account/{SignInRoute,SignInScreen,SignInUiState,SignInViewModel,SignInTags,SessionViewModel}.kt`, `MainActivity.kt` (the gate, with `e2eRoot` on the root `Box`), `LibrosLeidosApp.kt`, `strings.xml` | Create/Modify |
| S2b | `app/build.gradle.kts` (credentials 1.6.0, play-services-auth, googleid 1.2.1, `GOOGLE_WEB_CLIENT_ID` field), `src/debug/res/xml/network_security_config.xml` with a debug manifest overlay | Modify/Create |
| S2b | `scripts/test-release-no-fake-auth.sh`, `ci.yml`, `scripts/test-e2e-ci-config.sh`; `e2e/src/pages/{signIn.page,tags}.ts`, `bookLifecycle.e2e.ts` | Create/Modify |

## Interfaces / Contracts

```kotlin
@JvmInline value class IdToken(val value: String) { override fun toString() = "IdToken(***)" }
sealed interface Session { data object SignedOut : Session; data class SignedIn(val account: Account) : Session }
sealed interface SignInResult { data class Success(val token: IdToken, val account: Account) : SignInResult
  data object Cancelled : SignInResult; data object NoCredential : SignInResult
  data object NotConfigured : SignInResult; data object Failure : SignInResult }
interface AuthGateway { suspend fun signIn(): SignInResult; suspend fun signOut() }
interface SessionStore { val session: StateFlow<Session>; fun currentToken(): IdToken?
  suspend fun save(token: IdToken, account: Account); suspend fun clear() }
```

`SignOut` clears the store first and then calls `gateway.signOut()` (`clearCredentialState`) on a best-effort basis. It is idempotent.

## OpenAPI Validator Risk (first task of S1)

The Atlassian validator enforces declared `security`. The client-side REST Assured filter should therefore report `validation.request.security.missing` on the "without credentials" contract tests. The server interceptor runs after Spring Security, so it only sees authenticated requests. The S1 spike works in this order:

1. Add `bearerAuth`.
2. Run `ListBooksTest`'s 401 case and record the actual key from the 3.0.0 jar.
3. Have `api(authenticated = false)` ignore only that key, with responses still validated. Then confirm that the bearer requests pass both validators.

## Image Smoke (`scripts/test-image-smoke.sh`)

- **Happy path.** Add `-e GOOGLE_OAUTH_WEB_CLIENT_ID=smoke-web-client-id.apps.googleusercontent.com`, a dummy non-secret value. The "Image starts" scenario still returns 200 on health and 401 on anonymous `/api/books`.
- **Existing DB-failure cases.** These are `no-datasource` and `blank-password`. Each also gets the dummy audience, so only the DB validator fails and the grep for "Missing required database configuration" stays valid.
- **New `no-auth-audience` case.** It runs with valid-looking dummy DB variables and no client ID. It expects a non-zero exit, logs containing "Missing required authentication configuration" and `GOOGLE_OAUTH_WEB_CLIENT_ID`, and no dummy value in the logs. The auth validator runs first, so the outcome does not depend on DB reachability.
- **Shared helper.** Generalize `expect_startup_failure` with a message argument.

## Spec Wording (for the orchestrator)

Replace backend-api `spec.md:80` with: "The validation MUST run in every application context, including tests. Test contexts satisfy it with non-secret test values (a test audience and a loopback JWKS URI) from test-only configuration. They MUST NOT replace the production JWT decoder or validators."

## Testing Strategy (Strict TDD)

| Slice | Test | Approach |
|---|---|---|
| S1 | `GoogleIdTokenValidationTest` (MockMvc + `TestJwks`) | The spec matrix: valid, both issuers, wrong issuer or audience, expired, `nbf` in the future, foreign key, `none`, HS256, missing or blank `sub`, malformed, Basic, and JWKS outage then recovery. Each 401 is bodiless with no `WWW-Authenticate` |
| S1 | `GoogleAuthConfigurationValidatorTest` (`ApplicationContextRunner`) | A blank value fails the start, the message names the setting and holds no values, auth fails before the DB check when both are missing, and a non-https, non-loopback JWKS URI is rejected |
| S1 | `AuthenticatedActuatorTest`, `ProductionAuthContextTest` | Without a token: 401. With a valid bearer: 404. No `UserDetailsService` |
| S1 | `test-image-smoke.sh` | Happy path, the two DB failures and the new auth failure |
| S1 | Contract | Bearer tokens. Cross-user list, read, update and delete. A fresh token for the same `sub` lists the books. `SecuritySliceTest` stays green |
| S1 | `verifyPackagedArtifact` | `testsupport` is absent from the jar |
| S2a | JVM unit tests with fakes and Turbine | The use cases. `InMemorySessionStore` gives signed-out, signed-in, signed-out. Metadata alone is not a session. Domain has no `android`/`androidx` imports |
| S2a | Robolectric | The DataStore holds no token and sign-out empties it. The backup XML excludes the account store |
| S2b | Robolectric and Compose | Sign-in states with retry, safe error text, and the gate. The gateway maps exceptions to results (mocked `CredentialManager`) and a blank ID gives `NotConfigured` |
| S2b | Script | No `FakeAuthGateway` in release sources or bytecode, with a debug positive control. The release network config has no cleartext |
| S2b | Appium | Sign in through the fake gateway, then the existing lifecycle |

## Threat Matrix

N/A for routing, shell, subprocess, VCS/PR automation, executable-file classification and process integration. The new script only greps build output, following the existing pattern. The auth trust boundary is covered by the S1 matrix above.

## Migration / Rollout

No data migration. `owner_id` already stores opaque strings, and `sub` fits in 255 characters. Revert S2b, then S2a, then S1. Reverting S1 restores deny-by-default 401. Deployment (#8) must set `GOOGLE_OAUTH_WEB_CLIENT_ID`, otherwise the backend refuses to start by design.

## Open Questions

- [x] Closed: the debug fake applies only while the Web client ID is blank. The spec now says the same.
- [x] Closed by spike 1.4: with no key cached, a 401 during a JWKS outage is followed by an accepted request as soon as the JWKS is back. Rate-limiting does not lock out recovery (`JwksOutageTest`).
- [x] Closed by spike 1.4: the default builder does not validate `typ` (a `typ: at+jwt` token was accepted). The decoder sets `validateType(true)`, which rejects `at+jwt` and accepts `JWT` or no `typ`.

## Accepted Deviations (S1 apply)

- The dependency is `spring-boot-starter-security-oauth2-resource-server`, the Spring Boot 4 starter name (Spring Security 7.1.2, Nimbus 10.9.1).
- `TestJwks` and `TestJwtSupport` live once in `backend/src/testSupport/kotlin`. That directory is added as a source directory to the `test` and `contractTest` suites instead of keeping two copies. It never reaches the jar, and `verifyPackagedArtifact` also rejects `TestJwks` and `TestJwt` class names, `jwks.json` and PEM private-key headers.
- An explicit exp-present validator was added, because `JwtTimestampValidator` accepts tokens without `exp`.
- `openapi.yaml` declares `bearerAuth` on every books operation as well as globally, because swagger-request-validator 3.0.0 enforces only operation-level `security` (spike 1.2, key `validation.request.security.missing`).
- Test tokens carry a random `jti`, so two tokens minted for the same `sub` are always distinct (the new-install contract test).
