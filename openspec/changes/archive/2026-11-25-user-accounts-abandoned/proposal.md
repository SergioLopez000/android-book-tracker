# Proposal: User accounts with Google Sign-In (#26)

## Intent

No production authentication exists, so every API call returns 401 and no book can be tied to a person. #26 adds a Google account identity in the app and bearer-token validation in the backend. This is the base for sync (#10).

## Scope

### In Scope
- Backend: JWT resource server for Google ID tokens. It pins RS256 and validates expiry, issuers `accounts.google.com` and `https://accounts.google.com`, the Web client ID as audience, and a non-blank `sub` used as owner. It loads keys from the Google JWKS, fails closed when the JWKS is unavailable, and refuses to start when the issuer or audience is unconfigured.
- Offline local-JWKS test support: about 10 token-validation cases, cross-user authorization tests, and contract tests moved from httpBasic to bearer tokens. A fresh token for the same `sub` lists that owner's existing books.
- App: required sign-in at start through Credential Manager (credentials 1.6.0, googleid 1.2.1). The ID token is held in memory only, non-sensitive metadata goes to DataStore, and sign-out clears everything.
- A debug-only fake sign-in gateway keeps the offline Appium e2e working.
- Debug-only cleartext for `10.0.2.2`. OAuth client IDs are non-secret config, blank by default.
- `docs/secrets.md`: remove the obsolete `JWT_SIGNING_SECRET` row, in step with `scripts/check-secrets-contract.sh`.

### Out of Scope
- App-side download of books on a new install. This part of the acceptance criterion moves explicitly to #10.
- Encrypted token persistence with Tink, which moves to #10.
- Firebase, other identity providers, and passwords.
- Deployment (#8) and the release keystore SHA-1 registration.

## Capabilities

### New Capabilities
- `user-account`: Required sign-in, in-memory session, account metadata, sign-out, and the debug fake gateway.

### Modified Capabilities
- `backend-api`: "Deny-by-Default Security" now authenticates Google bearer tokens. "Contract Tests" use bearer tokens and add cross-user cases.
- `e2e-testing`: "Isolation and Offline Execution" and "Happy-Path Flow" sign in through the debug fake gateway.
- `ci-pipeline`: "Secret Inventory Document" drops `JWT_SIGNING_SECRET`.

## Approach

Ports and adapters, with no Android code in `domain`:
- Ports: `AuthGateway` and `SessionStore`.
- Use cases: `SignIn`, `SignOut` and `ObserveSession`.
- Adapters: Credential Manager and the fake gateway in `data/auth/`.
- UI: `ui/account/`.

The backend controller and repository stay unchanged, because `authentication.name` resolves to `sub`. The 401 response stays bodiless. Chained PRs:

| Slice | Content | Lines |
|---|---|---|
| S1 | Backend validation, tests, OpenAPI `bearerAuth`, secrets docs | 500-700 |
| S2a | App session core, ports, use cases, DataStore, backup rules | 350-500 |
| S2b | Sign-in UI, Credential Manager, fake gateway, config, e2e | 400-600 |

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `backend/src/main/kotlin/**/config/`, `backend/build.gradle.kts` | Modified | Resource server, validators |
| `backend/src/contractTest/`, `backend/src/test/` | Modified | Local JWKS, bearer auth |
| `backend/src/main/resources/openapi.yaml` | Modified | `bearerAuth` scheme |
| `app/src/main/java/com/librosleidos/{domain,data/auth,ui/account,di}` | New/Modified | Session, sign-in |
| `app/src/debug/` | New | Fake gateway, cleartext config |
| `e2e/` | Modified | Sign-in step |
| `docs/secrets.md`, `scripts/check-secrets-contract.sh` | Modified | Inventory |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Manual Google Cloud and OAuth consent setup blocks S2b | Med | Document the steps. Blank IDs keep builds green |
| Real sign-in cannot be tested in CI | High | Fake gateway and local JWKS; a manual local check |
| Required sign-in blocks offline use without an account | High | The user accepted it; e2e uses the fake gateway |
| The OpenAPI validator may flag the security scheme | Med | Spike in S1 |
| The fake gateway leaks into release | Low | Debug source set and a release test |

## Rollback Plan

Revert each slice PR independently, starting with S2b, then S2a, then S1. Reverting S1 restores deny-by-default 401. No schema or data migration is involved, and no secret changes.

## Dependencies

- A Google Cloud project with Android and Web OAuth client IDs (manual).
- Sync #10, for the app-side download and the Tink persistence.

## Success Criteria

- [ ] Valid Google tokens are accepted, and every invalid case returns a bodiless 401. A JWKS outage also fails closed.
- [ ] User B cannot list, read, update or delete user A's books.
- [ ] A fresh token for the same `sub` lists the existing books, which proves the server half of the new-install criterion.
- [ ] The app requires sign-in, keeps the token only in memory, and sign-out clears the session.
- [ ] The offline e2e passes with the fake gateway, and the release build contains no fake gateway.
- [ ] The secrets contract check passes.
