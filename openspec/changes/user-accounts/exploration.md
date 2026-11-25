# Exploration: user-accounts (#26)

## Current state

### Backend
- `SecurityConfig` is deny-by-default. Only `/actuator/health` is public, and every other request must be authenticated.
- Sessions are stateless and CSRF is disabled. Unauthenticated requests get a 401 with no body.
- Authentication is plugged in through `ApiAuthenticationCustomizer` beans. None exists in production today, so every API call returns 401.
- `BooksController` derives the owner as `OwnerId(authentication.name)`. No owner is ever read from the request body or path.
- Every repository method is scoped by owner. A book that is missing or owned by someone else returns 404.
- `owner_id VARCHAR(255) NOT NULL` and its index exist from `V1__create_books.sql`. With a JWT resource server, `name` resolves to the token `sub`, so the controller and repository need no change.
- Tests authenticate with httpBasic as `test-owner`, through `ContractTestAuthentication` and `TestApiAuthentication`. `verifyPackagedArtifact` keeps test-support classes and bypass properties out of the jar.
- Adding `spring-boot-starter-oauth2-resource-server` gives Nimbus JWT validation:
  - Pin RS256.
  - Validate expiry, issuer and audience explicitly.
  - Require a non-blank `sub`.
  - Avoid `issuer-uri` discovery, which does network I/O and does not enforce audience.
  - Fail fast at startup when the issuer or audience is not configured.
- Tests can run fully offline. An RSA test key served from a local JWKS on the JDK `HttpServer` signs the tokens, covering these cases:
  - valid
  - expired
  - not yet valid
  - wrong issuer
  - wrong audience
  - wrong key
  - `alg=none`
  - missing `sub`
  - HS256 algorithm confusion
- Cross-user tests: user B cannot list, read, update or delete user A's books.

### Provider options
| Option | Setup | Backend validation | Tradeoff |
|---|---|---|---|
| A. Google Sign-In via Credential Manager | Google Cloud project, consent screen, Android and Web client IDs; no `google-services.json` | Issuer `https://accounts.google.com`, audience is the Web client ID, Google JWKS | Light. The ID token lasts about 1h, with silent re-authentication and no refresh token |
| B. Firebase Auth | A plus a Firebase project and `google-services.json` with its Gradle plugin | Issuer `securetoken.google.com/<project>`, audience is the project ID | Heavier. The SDK refreshes tokens and offers an emulator. Gitleaks may flag the `AIza` key, and compatibility with AGP 9.1.1 is unverified |
| C. Other IdP or own JWT | New vendor or own infrastructure | Generic OIDC | Own password and JWT handling is the worst fit for security |

### App
- Hexagonal layers (`domain/{model,port,usecase}`, `data/{local,remote}`, `ui/...`) with manual DI in `AppContainer` and a two-route `NavHost`. Books are local-only in Room, with no owner concept.
- Fit for this change:
  - `AuthGateway` and `SessionStore` ports.
  - `SignIn`, `SignOut` and `ObserveSession` use cases.
  - A `data/auth/` adapter.
  - A `ui/account/` screen with a top-bar entry.
- Storage: `EncryptedSharedPreferences` is deprecated. The lightest compliant option keeps the ID token in memory only, with non-sensitive account metadata in DataStore. Persisting the token would need DataStore with Tink and Keystore.
- `allowBackup="true"` with no rules means any persisted session data needs backup exclusions (coordinate with #53).
- Sign-in should be optional, so the local-first app and the offline Appium e2e keep working when signed out.

## The sync-dependent criterion
"New install, sign in, items downloaded" needs sync #10.

| Option | Delivers | Tradeoff |
|---|---|---|
| A. Defer the app-side download to #10 | Backend auth, authorization tests, app sign-in and session | The criterion moves to #10 |
| B. Backend-level proof | A contract test where a fresh token for the same `sub` lists the existing books | Cheap, about 40 lines; proves the server half |
| C. Minimal one-way download | `GET /api/books` into Room on sign-in | Needs a Room migration, a merge rule and a bearer HTTP client. That is about 400 lines and half of #10 |

## Dev reach and testability
- The backend is not deployed (#8). The emulator reaches a local backend at `http://10.0.2.2`, with a debug-only `networkSecurityConfig` that allows cleartext for that host only.
- The base URL is a per-build-type `buildConfigField`, left blank in release until #8.
- Not testable in CI: real Google sign-in and a production-like end-to-end flow.

## CI and secrets impact
- OpenAPI: add a `bearerAuth` security scheme. It is unverified whether the request validator then flags the unauthenticated contract tests.
- `ContractCoverageTest`: no new endpoint is planned, so it needs no change.
- No new GitHub secret. Client IDs, the issuer and the audience are public identifiers.
- `docs/secrets.md`:
  - The `JWT_SIGNING_SECRET` row becomes obsolete.
  - `ANDROID_RELEASE_KEYSTORE` is needed only at release time, to register its SHA-1.
- The `e2e-tests` job stays secret-free and offline. A blank client ID hides the sign-in entry.

## Slicing (rough, 800-line budget)
| Slice | Content | Lines |
|---|---|---|
| S1 Backend token validation | Starter, validators, fail-closed config, OpenAPI scheme, JWT test support, about 10 validation cases, cross-user contract tests, migration of contract-test auth, secrets docs | 500-700 |
| S2a App session core | Ports, use cases, session store, ViewModel, wiring, tests, backup rules | 350-500 |
| S2b App UI and Credential Manager | Adapter, account UI and navigation, client ID config, debug cleartext, tests, debug fake gateway | 400-600 |

## Technical recommendations for design
- Migrate contract tests from httpBasic to bearer tokens, so tests and production share one auth path.
- Keep the 401 response bodiless, as asserted by `SecuritySliceTest`.
- Use the raw `sub` as owner, pinned to one issuer.
- JWKS outages fail closed with a 401.

## Risks
- The backend is not deployed, so verifying the real flow is manual and local only.
- Criterion 1 cannot be met in full without #10.
- The OpenAPI validator's behaviour with a declared security scheme is unverified.
- Google issuer variants (with and without scheme) and JWKS caching must be pinned by tests.
- Google Cloud and OAuth consent setup is a manual step outside the repo, and can block S2b.
- Spring Boot 4.1.2 and Spring Security 7 APIs must be confirmed against the docs during design.
- The total is likely over 800 lines, so chained PRs are expected.
