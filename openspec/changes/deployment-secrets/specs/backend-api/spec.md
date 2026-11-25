# Delta for backend-api

## ADDED Requirements

### Requirement: Database Credentials From Environment Only

The backend MUST read its database connection settings (`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`) exclusively from the environment. No profile or configuration file packaged with the application MUST define a default value, placeholder default or fallback for any of them. A validator in the `config` package MUST run at startup of the production application context and MUST reject a missing, empty or whitespace-only value for any of the three settings, failing startup with a message that names every offending setting and does not echo any value. The validator MUST read the resolved datasource properties and MUST NOT run in the `NoDatabaseTestConfig` context or in contexts whose datasource is supplied by `@ServiceConnection`.

#### Scenario: All credentials present
- GIVEN the three settings are set to non-blank values
- WHEN the application context starts
- THEN the validator passes and startup continues

#### Scenario: Missing setting
- GIVEN `SPRING_DATASOURCE_PASSWORD` is not set
- WHEN the application context starts
- THEN startup fails with a message naming `SPRING_DATASOURCE_PASSWORD`

#### Scenario: Blank setting
- GIVEN `SPRING_DATASOURCE_USERNAME` is set to an empty or whitespace-only string
- WHEN the application context starts
- THEN startup fails with a message naming `SPRING_DATASOURCE_USERNAME`

#### Scenario: Several settings missing
- GIVEN none of the three settings is set
- WHEN the application context starts
- THEN the failure message names all three settings

#### Scenario: Values are never echoed
- GIVEN a validation failure with other settings set to real-looking values
- WHEN the failure message and logs are inspected
- THEN no setting value appears in them

#### Scenario: No credential defaults packaged
- GIVEN every configuration file and profile packaged in the jar
- WHEN their datasource settings are inspected
- THEN none defines a value or default for the URL, username or password

#### Scenario: Test slices unaffected
- GIVEN a unit test using `NoDatabaseTestConfig` and an integration test using a Testcontainers `@ServiceConnection` datasource
- WHEN their contexts start
- THEN the validator does not reject them and no credentials are required from the environment

### Requirement: Packaged Artifact Free of Credentials

`verifyPackagedArtifact` MUST fail `check` when the packaged jar contains a credential-like pattern or a `jdbc:` URL that embeds a password, in addition to its existing checks for test-only classes and security-bypass properties. The failure output MUST name the offending jar entry and MUST NOT print the matched secret value.

#### Scenario: Clean jar passes
- GIVEN a packaged jar with no credential-like content
- WHEN `verifyPackagedArtifact` runs
- THEN it succeeds

#### Scenario: Embedded JDBC password rejected
- GIVEN a jar entry containing a `jdbc:` URL with an embedded password
- WHEN `verifyPackagedArtifact` runs
- THEN it fails naming the entry and does not print the password

#### Scenario: Credential-like pattern rejected
- GIVEN a jar entry matching a credential-like pattern
- WHEN `verifyPackagedArtifact` runs
- THEN it fails naming the entry and does not print the matched value

#### Scenario: Wired into check
- GIVEN the backend build
- WHEN `check` runs
- THEN `verifyPackagedArtifact` runs as part of it

## MODIFIED Requirements

### Requirement: Hardened Container Image

The backend MUST ship a Dockerfile building an image from the distroless `java21-debian13:nonroot` base, pinned by digest, that runs as a non-root user. The application jar MUST be built outside the image and copied in. The image MUST NOT contain secrets or the test-only user. The image smoke test (`scripts/test-image-smoke.sh`) MUST include a case proving the container exits non-zero with the credential validation message when started without database credentials.
(Previously: no statement about starting without credentials.)

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
