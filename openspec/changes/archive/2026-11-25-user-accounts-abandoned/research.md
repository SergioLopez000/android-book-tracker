```
schema: gentle-ai.sdd-research/v1
revision: 2
change: user-accounts (GitHub #26)
accessed_at: 2026-11-25
outcome: partial
```

## Questions
- Q1. Google Sign-In via Credential Manager: current API, versions, Cloud setup, emulator needs, legacy deprecation.
- Q2. Google ID token validation: issuer, JWKS, audience, checks.
- Q3. Firebase Auth: BoM, google-services plugin compatibility with AGP 9, API key secrecy, token issuer, audience and JWKS, emulator, limits, deletion.
- Q4. Spring Security 7 resource server: decoder, validators, multiple issuers, offline tests.
- Q5. Offline e2e options for a mandatory sign-in.
- Follow-up gaps:
  - G1. Compatibility of google-services with AGP 9.
  - G2. `googleid` version.
  - G3. Emulator behaviour.
  - G4. Spring Security test support.

## Admission
Public web documentation only:
- developer.android.com
- developers.google.com
- firebase.google.com
- docs.spring.io
- GitHub release pages

## Sources
| ID | Class | Title | Publisher | URL | Excerpt |
|---|---|---|---|---|---|
| S1 | documentation | Implement Sign in with Google | developer.android.com | https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation | "GetGoogleIdOption ... setServerClientId ... The webClientId is the Web Client ID"; "Validate the credential on your relying party server" |
| S2 | documentation | Sign in with Google overview | developer.android.com | https://developer.android.com/identity/sign-in/credential-manager-siwg | "If no Google Accounts exist on the device, the bottom sheet UI does not appear" |
| S3 | documentation | Migrate from legacy Google Sign-In | developer.android.com | https://developer.android.com/identity/sign-in/legacy-gsi-migration | "Google Sign-In for Android ... is deprecated and will be removed ... in a future release" |
| S4 | documentation | androidx.credentials release notes | developer.android.com | https://developer.android.com/jetpack/androidx/releases/credentials | Stable 1.6.0 (2026-04-08); 1.7.0-alpha03 (2026-07-29); default minSdk 21 → 23 |
| S5 | documentation | Verify the Google ID token on your server | developers.google.com | https://developers.google.com/identity/gsi/web/guides/verify-google-id-token | iss "accounts.google.com" or "https://accounts.google.com"; JWKS https://www.googleapis.com/oauth2/v3/certs |
| S6 | documentation | Verify Firebase ID tokens; API keys; Manage users | firebase.google.com | https://firebase.google.com/docs/auth/admin/verify-id-tokens | iss "https://securetoken.google.com/<projectId>"; aud = project ID; "API keys for Firebase services are OK to include in code" |
| S7 | documentation | Firebase Android release notes; Auth Emulator; pricing | firebase.google.com | https://firebase.google.com/support/release-notes/android | BoM 35.2.0 with firebase-auth 25.0.0; emulator issues "unsigned ID tokens"; Spark 50K MAU |
| S8 | documentation | Spring Security JWT resource server; multitenancy | docs.spring.io | https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html | `NimbusJwtDecoder.withJwkSetUri`, `JwtValidators.createDefaultWithIssuer`, `withPublicKey`, `JwtIssuerAuthenticationManagerResolver` |
| S9 | documentation | Firebase release notes, Gradle plugins | firebase.google.com | https://firebase.google.com/support/release-notes/android | google-services 4.6.0 (2026-11-11) with no AGP 9 entry; App Distribution 5.2.0 and Perf 2.0.2 "Fixed compatibility with AGP 9.0.0" |
| S10 | documentation | AGP 9.0 release notes; built-in Kotlin | developer.android.com | https://developer.android.com/build/releases/agp-9-0-0-release-notes | "Some third-party plugins might still depend on interfaces or APIs that are no longer exposed" |
| S11 | documentation | Credential Manager library releases | developers.google.com | https://developers.google.com/identity/android-credential-manager/releases | googleid 1.2.1 (2026-09-16) |
| S12 | documentation | Mocking OAuth2 (MockMvc) | docs.spring.io | https://docs.spring.io/spring-security/reference/servlet/test/mockmvc/oauth2.html | Spring Security 7.2.0 stable; `with(jwt())`, default header `alg: none` |

## Validated claims
- **Credential Manager is the recommended Google sign-in API (C1–C5).** [S1, S2, S3]
  - `GetGoogleIdOption` drives the bottom sheet and `GetSignInWithGoogleOption` drives the button.
  - Auto sign-in needs `setFilterByAuthorizedAccounts(true)` and `setAutoSelectEnabled(true)`.
  - Call `clearCredentialState()` on sign-out.
- **Cloud setup (C6).** A Google Auth Platform project is needed, and the Web client ID is the token audience. [S1, S2]
- **Versions (C7, C28).** credentials 1.6.0 is stable and 1.7.0-alpha03 is the latest pre-release. googleid is 1.2.1. [S4, S11]
- **Legacy Google Sign-In (C8).** It is deprecated, with no removal date. [S3]
- **Device requirements (C9, C30).** Google Play services are required, and the bottom sheet does not appear without a Google account on the device. [S2, S4]
- **Google ID token checks (C10–C13).** [S5]
  - Accept issuer `accounts.google.com` or `https://accounts.google.com`.
  - Fetch keys from the JWKS at `https://www.googleapis.com/oauth2/v3/certs`.
  - Check the signature, `aud`, `iss` and `exp`.
  - Use `sub` as the user ID.
  - No token lifetime is documented, so rely on `exp`.
- **Firebase (C14–C19).** [S6, S7]
  - The API key in `google-services.json` is not a secret.
  - Issuer is `https://securetoken.google.com/<projectId>`, audience is the project ID, and the token lasts about 1h.
  - `delete()` needs a recent login.
  - The Auth Emulator, reached at `10.0.2.2:9099`, issues unsigned tokens.
  - The Spark plan allows 50K MAU.
- **Spring Security APIs (C20–C22).** [S8]
  - `NimbusJwtDecoder.withJwkSetUri`.
  - `JwtValidators.createDefaultWithIssuer`.
  - `JwtClaimValidator` for the audience.
  - `JwtIssuerAuthenticationManagerResolver.fromTrustedIssuers`.
  - `withPublicKey` for offline tests.
- **google-services and AGP 9 (C24–C27).** [S9, S10]
  - The google-services notes up to 4.6.0 never mention AGP 9, while sibling Firebase plugins shipped explicit AGP 9 fixes.
  - AGP 9 warns that some third-party plugins may break.
  - Compatibility can only be confirmed with a build spike.
- **minSdk (C29).** credentials 1.6.x defaults to minSdk 23. [S4]
- **Spring Security test support (C32–C34).** [S8, S12]
  - The `jwt()` MockMvc post-processor bypasses the decoder, so issuer and audience validation need separate decoder tests with a local key.
  - The docs are for Spring Security 7.2.0.

## Contradictions, uncertainty and freshness
- **G1, partly closed.** No source confirms google-services 4.6.0 on AGP 9.1.1; a spike is required.
- **G2, version closed.** The googleid minSdk is not stated.
- **G3, unresolved.** No official fake or test provider for Sign in with Google was found. Search snippets, which were not fetched, suggest a Play system image with a signed-in account. A mandatory Google sign-in on the account-less CI emulator is inferred, not documented, to be impossible.
- **G4, closed.** The mapping from Spring Boot 4.1.2 to its Spring Security version is not stated in the docs.
- **Excluded as unvalidated:**
  - The Google ID token lifetime.
  - The `alg` of the emulator tokens.
- **Freshness.** All sources were reachable on 2026-11-25.

## Product choices (non-authoritative)
- P1. Provider: Google-only through Credential Manager, or Firebase Auth. Firebase needs the google-services plugin, whose AGP 9 compatibility is undocumented.
- P2. E2E bypass: a debug-only fake sign-in gateway, plus a backend test decoder with a local public key.
- P3. Use stable credentials 1.6.0 and pin googleid 1.2.1.
- P4. Spike google-services on AGP 9.1.1 before committing to Firebase.
