# Delta for backend-api

## ADDED Requirements

### Requirement: Google ID Token Validation

The API MUST act as an OAuth2 resource server that accepts only Google ID tokens as bearer credentials. A token MUST be accepted only when all of the following hold: its signature is RS256 and verifies against a key from the configured Google JWKS; it is not expired; its issuer is exactly `accounts.google.com` or `https://accounts.google.com`; its audience contains the configured Web client ID; and its `sub` claim is present and not blank. Any other algorithm (including `none` and HS256) MUST be rejected. The authenticated principal name MUST be the token `sub`, and `sub` MUST be the owner identity passed to every use case. The controller and repository MUST NOT parse tokens themselves.

#### Scenario: Valid token accepted
- GIVEN a token signed RS256 by a key in the JWKS with a valid issuer, audience, future expiry and non-blank `sub`
- WHEN a books endpoint is called with it as a bearer credential
- THEN the request is authenticated and the owner is that `sub`

#### Scenario: Both issuer forms accepted
- GIVEN otherwise valid tokens with issuer `accounts.google.com` and with `https://accounts.google.com`
- WHEN each is presented
- THEN both are accepted

#### Scenario: Other issuer rejected
- GIVEN an otherwise valid token with issuer `https://evil.example`
- WHEN it is presented
- THEN the response is 401

#### Scenario: Wrong audience rejected
- GIVEN an otherwise valid token whose audience is not the configured Web client ID
- WHEN it is presented
- THEN the response is 401

#### Scenario: Expired token rejected
- GIVEN an otherwise valid token whose expiry is in the past
- WHEN it is presented
- THEN the response is 401

#### Scenario: Bad signature rejected
- GIVEN a token signed by a key that is not in the JWKS
- WHEN it is presented
- THEN the response is 401

#### Scenario: Non-RS256 algorithm rejected
- GIVEN tokens using `alg: none` and an HS256 signature
- WHEN each is presented
- THEN each response is 401

#### Scenario: Missing or blank sub rejected
- GIVEN otherwise valid tokens with no `sub` and with a blank `sub`
- WHEN each is presented
- THEN each response is 401

#### Scenario: Malformed token rejected
- GIVEN a bearer value that is not a well-formed JWT
- WHEN it is presented
- THEN the response is 401

#### Scenario: Owner is sub
- GIVEN a valid token with `sub` S
- WHEN a book is created with it
- THEN the stored `owner_id` equals S

### Requirement: Bodiless Fail-Closed Authentication Errors

Every authentication failure MUST return 401 with an empty body and MUST NOT disclose the failure reason, key identifiers or exception details. When the JWKS cannot be fetched or no matching key is available, the API MUST reject the request with 401 and MUST NOT accept the token. Stateless behavior MUST be preserved.

#### Scenario: 401 has no body
- GIVEN any invalid, expired or missing bearer token
- WHEN a books endpoint is called
- THEN the response is 401 with an empty body

#### Scenario: JWKS outage fails closed
- GIVEN the JWKS endpoint is unreachable and no key is cached for the token
- WHEN a token that would otherwise be valid is presented
- THEN the response is 401

#### Scenario: Recovery after outage
- GIVEN a prior request failed during a JWKS outage
- WHEN the JWKS becomes available again
- THEN a valid token is accepted

### Requirement: Authentication Configuration Required at Startup

The production application context MUST refuse to start when the Google issuer configuration or the Web client ID audience is missing, empty or whitespace-only, with a message that names each offending setting and does not echo any value. The validation MUST run in every application context, including tests. Test contexts satisfy it with non-secret test values (a test audience and a loopback JWKS URI) from test-only configuration and MUST NOT replace the production JWT decoder or validators.

#### Scenario: Audience unconfigured
- GIVEN the Web client ID setting is not set
- WHEN the production application context starts
- THEN startup fails with a message naming that setting

#### Scenario: Issuer unconfigured
- GIVEN the issuer setting is blank
- WHEN the production application context starts
- THEN startup fails with a message naming that setting

#### Scenario: Values not echoed
- GIVEN a validation failure
- WHEN the message and logs are inspected
- THEN no configured value appears

#### Scenario: Test contexts satisfy the validation
- GIVEN a contract or unit test context with a test audience and a loopback JWKS URI from test-only configuration
- WHEN it starts
- THEN the startup validation passes and the production JWT decoder and validators are used

### Requirement: OpenAPI Bearer Security Scheme

`openapi.yaml` MUST declare a `bearerAuth` HTTP bearer security scheme (bearer format JWT) applied to every books operation, and MUST declare the 401 response for those operations without a body.

#### Scenario: Scheme declared and applied
- GIVEN `openapi.yaml`
- WHEN its security definitions are read
- THEN `bearerAuth` exists and every books operation requires it

#### Scenario: 401 declared bodiless
- GIVEN each books operation
- WHEN its responses are read
- THEN 401 is declared with no content

## MODIFIED Requirements

### Requirement: Deny-by-Default Security

The API MUST be stateless and MUST return 401 for every request except `/actuator/health` unless it carries a valid Google ID token per "Google ID Token Validation". Authentication via HTTP Basic or any other mechanism MUST NOT be accepted. There MUST NOT be a runtime bypass, profile or flag that disables security. Tests MUST authenticate with tokens signed by a test-only key whose JWKS is defined in test sources only.
(Previously: returned 401 for every request except health "until authentication (#26) is wired", and tests authenticated with a test-only user defined in test sources.)

#### Scenario: Unauthenticated books request
- GIVEN no credentials
- WHEN any books endpoint is called
- THEN the response is 401

#### Scenario: Unknown path denied
- GIVEN no credentials
- WHEN a path that matches no route is called
- THEN the response is 401, not 404

#### Scenario: Health is public
- GIVEN no credentials
- WHEN `/actuator/health` is called
- THEN the response is 200

#### Scenario: Valid token passes security
- GIVEN a valid Google ID token per the validation rules
- WHEN a books endpoint is called
- THEN the request is not rejected with 401

#### Scenario: HTTP Basic no longer accepted
- GIVEN HTTP Basic credentials, including the former test-only user
- WHEN a books endpoint is called
- THEN the response is 401

#### Scenario: No runtime bypass
- GIVEN the production configuration and every packaged profile
- WHEN they are inspected
- THEN no property, profile or code path permits unauthenticated access beyond `/actuator/health`

#### Scenario: Test key stays in tests
- GIVEN the packaged application artifact
- WHEN its contents are inspected
- THEN the test signing key, the test JWKS and any test token helper are absent

### Requirement: Contract Tests

Each OpenAPI operation MUST have a REST Assured contract test in its own test class. Contract tests MUST authenticate with bearer tokens signed by a test key served from a local JWKS, with no network access to Google. Responses MUST be validated against `openapi.yaml` through a request-and-response validator filter. Tests MUST assert the status codes declared in the contract, with separate test methods for missing credentials (401), schema violations (400) and cross-owner access, where cross-owner access uses two tokens with different `sub` values. One spec-coverage test per operation MUST fail when an operation has no contract test. A server-half new-install contract test MUST prove that a fresh token for the same `sub` lists the books that `sub` already owns.
(Previously: tests authenticated with the test-only httpBasic user and had no cross-user token cases or new-install case.)

#### Scenario: Response matches schema
- GIVEN an authenticated valid request with a bearer token to an operation
- WHEN the response is validated against `openapi.yaml`
- THEN the declared status code and schema are satisfied

#### Scenario: Contract drift fails
- GIVEN a response whose body deviates from the declared schema
- WHEN the contract test runs
- THEN the test fails

#### Scenario: Untested operation fails coverage
- GIVEN an operation with no contract test
- WHEN the spec-coverage test runs
- THEN it fails naming the operation

#### Scenario: Offline token tests
- GIVEN the contract and token-validation tests
- WHEN they run with no network access
- THEN they pass using the local JWKS

#### Scenario: Cross-user read denied
- GIVEN user A (`sub` A) has a book
- WHEN user B (`sub` B) reads that book by identifier
- THEN the response is 404 and no data of user A is returned

#### Scenario: Cross-user list, update and delete denied
- GIVEN user A has a book and user B holds a valid token
- WHEN user B lists books, updates it, or deletes it
- THEN B's list excludes A's book, and update and delete return 404 leaving A's book unchanged

#### Scenario: New install lists existing books
- GIVEN `sub` A created books with one token
- WHEN a different, freshly issued valid token for the same `sub` A lists books
- THEN the response contains those existing books

#### Scenario: Token-validation matrix
- GIVEN the validation cases (valid, both issuers, wrong issuer, wrong audience, expired, bad signature, `none`, HS256, missing `sub`, blank `sub`, malformed, JWKS outage)
- WHEN each runs against a protected endpoint
- THEN accepted cases succeed and every other case returns a bodiless 401

### Requirement: Actuator Exposes Only Health

Actuator MUST expose only the `health` endpoint over HTTP, and the health response MUST NOT include component details.
(Previously: the non-health endpoint scenario expected 404 "with the test-only user"; it now uses a valid test bearer token.)

#### Scenario: Other actuator endpoints unavailable
- GIVEN the running application
- WHEN `/actuator/env`, `/actuator/beans` or `/actuator/info` is requested
- THEN the response is 401 without a bearer token and 404 with a valid test bearer token

#### Scenario: Health hides details
- GIVEN the running application
- WHEN `/actuator/health` is called
- THEN the body contains only the status and no component details

### Requirement: Hardened Container Image

The backend MUST ship a Dockerfile building an image from the distroless `java21-debian13:nonroot` base, pinned by digest, that runs as a non-root user. The application jar MUST be built outside the image and copied in. The image MUST NOT contain secrets or any test-only authentication support (test signing keys, test JWKS, test token helpers). The image smoke test (`scripts/test-image-smoke.sh`) MUST include a case proving the container exits non-zero with the credential validation message when started without database credentials.
(Previously: "MUST NOT contain secrets or the test-only user".)

#### Scenario: Non-root runtime
- GIVEN the built image
- WHEN its configured user is inspected
- THEN it is a non-root, non-zero UID

#### Scenario: Pinned base
- GIVEN the Dockerfile
- WHEN its `FROM` line is read
- THEN the base reference includes an immutable `@sha256:` digest

#### Scenario: Image starts
- GIVEN the built image and a reachable PostgreSQL
- WHEN it starts
- THEN `/actuator/health` returns 200

#### Scenario: Image fails without credentials
- GIVEN the built image started with no database credentials in its environment
- WHEN the smoke test "fails without credentials" runs
- THEN the container exits non-zero and its output contains the validator message naming the missing settings

#### Scenario: No test authentication support in image
- GIVEN the built image filesystem
- WHEN it is inspected
- THEN no test signing key, test JWKS or test token helper is present

## Notes for archive

- The main spec Purpose line "Authentication (#26), deployment and secrets (#54), and package visibility (#8) are out of scope" must become "Deployment and secrets (#54) and package visibility (#8) are out of scope; authentication is Google ID token validation (#26)". Purpose is not a requirement, so it cannot be a MODIFIED entry.
- "Packaged Artifact Free of Credentials" says "test-only classes" generically and stays correct, since test key and helper classes are test-only classes.
