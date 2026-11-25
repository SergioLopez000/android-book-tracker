# Archive Report: user-accounts (abandoned)

- **Archived:** 2026-11-25.
- **Archive path:** `openspec/changes/archive/2026-11-25-user-accounts-abandoned/`
- **Issue:** #26. The owner decides whether it stays open as future work.
- **Outcome:** abandoned by the owner's decision. This is not a completed change.

## Why it was abandoned

On 2026-11-25 the owner changed the project scope. For now, the app stays local-only, with no users, no accounts and no backend sync. User accounts, sign-in and everything that depends on them are out of scope until that decision is revisited.

Earlier the same day the owner had also rejected Google Sign-In, in favour of free tools and an own login. That decision was superseded by the local-only scope before an own-login plan was written.

## What happened to the work

| Slice | State before | Action |
|---|---|---|
| Planning (#87, `3b1a88b`) | Merged into `dev` | Kept here as history only |
| S1 backend Google ID token validation (#88, `b6003fb`) | Merged into `dev`, never promoted | Reverted |
| S2a app session core (#89, `7bfb9d9`) | Merged into `dev`, never promoted | Reverted |
| S2b sign-in UI, Credential Manager, fake gateway | Partial, uncommitted | Discarded; never pushed |

After the reverts, the code is identical to `3b1a88b`. Nothing from this change reached `main` or `staging`.

## Specs

The delta specs in `specs/` were **not** merged into `openspec/specs/`. The living specs (`backend-api`, `e2e-testing`, `ci-pipeline`) are unchanged, and no `user-account` capability exists.

## Reusable findings for a future accounts change

- swagger-request-validator 3.0.0 enforces only operation-level `security`, under the key `validation.request.security.missing`.
- Spring Boot 4.1.2 names the resource-server starter `spring-boot-starter-security-oauth2-resource-server`.
- The Nimbus default builder does not validate `typ`, and `JwtTimestampValidator` accepts tokens without `exp`.
- Without a fail-closed wrapper, a JWKS outage surfaces as a 500 instead of a 401.
- Robolectric SDK 36 needs Java 21. The app tests use `@Config(sdk = [34])`.
- Local Testcontainers on Rancher Desktop needs `DOCKER_HOST` pointing at the Rancher socket and `TESTCONTAINERS_RYUK_DISABLED=true`.
