# Apply progress: user-accounts (#26)

## Unit S1 (backend token validation): tasks 1.1-6.4 done, 6.5 (commit and PR) left to the orchestrator

Environment: Docker via Rancher Desktop (`DOCKER_HOST=unix:///Users/sergio.lopezm/.rd/docker.sock`, Ryuk disabled). Context7 tools were not available to this executor; API names were confirmed by compiling and running tests against Spring Security 7.1.2 / Boot 4.1.2 / validator 3.0.0.

### Spike findings
- 1.1: artifact is `spring-boot-starter-security-oauth2-resource-server` (Boot 4.1.2 -> spring-security 7.1.2, nimbus-jose-jwt 10.9.1). RED: planted `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` passed `verifyPackagedArtifact`; GREEN after adding `oauth2\.resourceserver` to the bypass regex. (A planted `...jwt.secret` would already fail via the credential rule, so a non-secret key was used.)
- 1.2: the validator key is `validation.request.security.missing` (jar 3.0.0). It fires ONLY when the operation itself declares `security`; global top-level `security` is NOT enforced by validator 3.0.0 (checked with `validateRequest`). Decision: bearerAuth is declared per operation (5 operations) AND globally. Bearer requests pass both validators; REST Assured `auth().oauth2()` is applied after the filter, so the header is set explicitly with `.header("Authorization", "Bearer ...")`. `api(authenticated = false)` ignores only that key; responses still validated. RED: ListBooksTest 401 failed with that key before the ignore.
- 1.3: unwrapped Nimbus decoder pointed at a dead loopback port: the failure reaches the filter as `AuthenticationServiceException` (rethrown, servlet error 500). `FailClosedJwtDecoder` is needed. Mutation "drop fail-closed wrapper" fails `JwksOutageTest`. The throwaway spike test was deleted to save lines; this finding plus the mutation are the durable evidence.
- 1.4: confirmed API names: `oauth2ResourceServer { authenticationEntryPoint = HttpStatusEntryPoint(401); jwt { jwtDecoder = ..; jwtAuthenticationConverter = .. } }`, `NimbusJwtDecoder.withJwkSetUri(..).jwsAlgorithm(SignatureAlgorithm.RS256).validateType(true).restOperations(RestTemplate(JdkClientHttpRequestFactory))`, `JwtTimestampValidator`. Open question 3 (typ): the DEFAULT builder does NOT validate `typ` (a token with `typ: at+jwt` was accepted); `.validateType(true)` rejects it and still accepts a missing `typ` and `typ: JWT`. Used explicitly. Open question 2 (rate limiting): with a fresh decoder (no key cached) an outage yields 401 and the next request after the JWKS returns is accepted immediately (no lockout) in `JwksOutageTest`. HS256 cannot be allowed through the JWK-set builder (`jwsAlgorithm` takes `SignatureAlgorithm` only; HS256 is a `MacAlgorithm`), so the HS256 mutation does not compile: the type system forbids it.
- Extra hardening found while writing validators: `JwtTimestampValidator` accepts a token with no `exp`; added an explicit "exp present" validator and a "missing exp" case.

### RED evidence (condensed)
- 2.1 `TestJwksTest`: compile error, unresolved `TestJwks`.
- 3.1 `GoogleIdTokenValidationTest`: 4 of 17 failed (valid, both issuers, unknown path with valid token: 401 instead of 200); rejections passed vacuously under deny-by-default.
- 3.2 `JwksOutageTest`: failed on recovery (401 instead of 200).
- 4.1 `GoogleAuthConfigurationValidatorTest`: compile error, unresolved `GoogleAuthConfigurationValidator`.
- 4.3 `ProductionAuthContextTest`: written after 4.2 GREEN, so it passed on first run (no independent RED; honest deviation).
- 5.1 `ListBooksTest` (bearer migration): 3 of 3 failed with `security.missing`, then 1 of 3 after the header fix, then green.
- 5.2 per-operation cross-user/owner tests: green on first run (behavior existed from 3.4); `a fresh token for the same sub` failed once on list order, fixed with `containsInAnyOrder`.
- 5.5: planted `TestJwks.yaml` and a PEM-header line passed `verifyPackagedArtifact` (rc 0); after extending the regexes both are reported and the clean jar passes.
- 6.1/6.2: the smoke script was edited and run once against the final image (no separate pre-validator RED run; the auth validator already existed from 4.2).
- 6.3: harness assertion failed on the real `docs/secrets.md` (`JWT_SIGNING_SECRET` row present), passes after deleting the row.

### Triangulation (3.5): each mutation fails a named case, then reverted
drop audience -> "wrong audience"; drop issuer -> "wrong issuer"; drop sub check -> "missing sub", "blank sub"; drop exp check -> "missing exp"; typ validation off -> "unexpected typ header"; drop fail-closed wrapper -> `JwksOutageTest`; drop timestamp validator -> "expired", "nbf in the future". HS256: not mutable (see 1.4).

### Deviations from the design
- Shared test support lives in `backend/src/testSupport/kotlin/com/librosleidos/testsupport/{TestJwks,TestJwtSupport}.kt` and is added as a source dir to both `test` and `contractTest` (Gradle), instead of two duplicated copies (saves ~130 lines; still test-only, never in `main`).
- `verifyPackagedArtifact` also rejects `TestJwks`/`TestJwt` class names, `jwks.json` and PEM private-key headers in text entries.
- openapi.yaml: `security` is declared on each operation as well as globally (needed by validator 3.0.0).
- `docs/secrets.md`: besides dropping the `JWT_SIGNING_SECRET` row, one Environments bullet states `GOOGLE_OAUTH_WEB_CLIENT_ID` is public configuration. The "Signing secrets (JWT) | 180 days" cadence row is left as is (design says drop only the inventory row); candidate follow-up.
- The non-https/non-loopback JWKS test calls `GoogleIdTokenDecoder.create` directly rather than through `ApplicationContextRunner`.

### Verification
- `cd backend && ./gradlew test integrationTest contractTest verifyPackagedArtifact pitest --continue`: BUILD SUCCESSFUL (pitest test strength 100%).
- `bash scripts/test-image-smoke.sh`: passed (no-datasource, blank-password, no-auth-audience, health 200, anonymous 401).
- `bash scripts/test-secrets-contract.sh`, `bash scripts/check-secrets-contract.sh`, `actionlint`, `shellcheck` on both scripts, root `spotlessCheck`, `detekt` on changed Kotlin, `gitleaks dir`: clean.
- Changed lines vs dev excluding openspec: 896 insertions, 163 deletions (about 1059), above the 800 review budget and the ~650 forecast.

### Remaining
6.5 (commit and PR 1) is owned by the orchestrator. S2a and S2b not started.
