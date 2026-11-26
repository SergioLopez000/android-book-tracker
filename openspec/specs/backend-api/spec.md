# Backend API Specification

## Purpose

A secure, tested Kotlin and Spring Boot backend base under `/backend`: separate build, hexagonal domain, owner-isolated PostgreSQL persistence, deny-by-default security, an OpenAPI-first books CRUD contract, contract tests, mutation testing, and a hardened container image. Authentication (#26), deployment and secrets (#54), and package visibility (#8) are out of scope.

Frozen since 2026-11-26 (#91): the code is kept as is but CI no longer builds, tests, scans, publishes or promotes it, and the app does not use it; revert the `freeze-backend` squash commit to reactivate.

## Requirements

### Requirement: Separate Backend Build

The backend MUST be an independent Gradle build under `/backend` with its own wrapper, settings and version catalog, targeting JDK 21 and the Kotlin version managed by Spring Boot 4.1 (2.3.21). The Android app build MUST remain on JDK 17 and Kotlin 2.4.21 and MUST NOT depend on, or be changed by, the backend build.

#### Scenario: Backend builds on its own
- GIVEN a clean checkout
- WHEN `./gradlew build` runs inside `/backend`
- THEN it succeeds using only the backend wrapper, settings and catalog on JDK 21

#### Scenario: App build unaffected
- GIVEN the backend directory exists
- WHEN `./gradlew :app:testDebugUnitTest` runs at the repository root
- THEN it passes on JDK 17 and does not compile or resolve backend modules

### Requirement: Hexagonal Domain and Use Cases

The backend MUST separate domain, ports and use cases from adapters. Domain and use cases MUST NOT depend on Spring, JDBC or HTTP types. Books CRUD MUST be exposed as use cases acting on behalf of an owner, and every use case MUST receive the owner identity explicitly.

#### Scenario: Domain is framework-free
- GIVEN the domain and use case packages
- WHEN their imports are inspected
- THEN none reference Spring, JDBC or HTTP classes

#### Scenario: Use case requires owner
- GIVEN any books use case
- WHEN it is invoked
- THEN the owner identity is a mandatory input

### Requirement: Owner-Scoped Persistence

The first Flyway migration MUST create the `books` table with a non-null `owner_id` column. Every repository adapter query (select, insert, update, delete, existence, listing) MUST filter or set `owner_id` explicitly. A book MUST NOT be readable, modifiable or deletable by an owner other than its own.

#### Scenario: owner_id is non-null
- GIVEN the schema after the first migration
- WHEN a row is inserted without `owner_id`
- THEN the database rejects it

#### Scenario: Cross-owner read denied
- GIVEN owner A has stored a book
- WHEN owner B requests that book by its identifier
- THEN the result is not found and no data of owner A is returned

#### Scenario: Cross-owner write denied
- GIVEN owner A has stored a book
- WHEN owner B updates or deletes it by identifier
- THEN nothing changes and the result is not found

#### Scenario: Listing is isolated
- GIVEN owners A and B each have books
- WHEN owner A lists books
- THEN only owner A's books are returned

### Requirement: Integration Tests Against PostgreSQL

Persistence MUST be verified by integration tests running Flyway migrations against a real PostgreSQL instance provided by Testcontainers. H2 or other substitutes MUST NOT be used for these tests.

#### Scenario: Migrations apply to PostgreSQL
- GIVEN an empty PostgreSQL Testcontainer
- WHEN the integration tests start
- THEN all Flyway migrations apply and the adapter tests run against that instance

### Requirement: Deny-by-Default Security

The API MUST be stateless and MUST return 401 for every request except `/actuator/health` until authentication (#26) is wired. There MUST NOT be a runtime bypass, profile or flag that disables security. Tests MUST authenticate with a test-only user defined in test sources.

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

#### Scenario: No runtime bypass
- GIVEN the production configuration and every packaged profile
- WHEN they are inspected
- THEN no property, profile or code path permits unauthenticated access beyond `/actuator/health`

#### Scenario: Test user stays in tests
- GIVEN the packaged application artifact
- WHEN its contents are inspected
- THEN the test-only user is absent

### Requirement: Actuator Exposes Only Health

Actuator MUST expose only the `health` endpoint over HTTP, and the health response MUST NOT include component details.

#### Scenario: Other actuator endpoints unavailable
- GIVEN the running application
- WHEN `/actuator/env`, `/actuator/beans` or `/actuator/info` is requested
- THEN the response is 401 without credentials and 404 with the test-only user

#### Scenario: Health hides details
- GIVEN the running application
- WHEN `/actuator/health` is called
- THEN the body contains only the status and no component details

### Requirement: OpenAPI Contract as Source of Truth

`backend/src/main/resources/openapi.yaml` MUST define the books CRUD contract (paths, operations, request and response schemas, status codes). Every operation MUST have a matching route, and every route MUST appear in the contract. Request schemas MUST forbid unknown properties and MUST bound string lengths.

#### Scenario: Operation without a route
- GIVEN an operation declared in `openapi.yaml` with no controller route
- WHEN the spec-coverage tests run
- THEN the build fails naming the operation

#### Scenario: Route without an operation
- GIVEN a controller route absent from `openapi.yaml`
- WHEN the spec-coverage tests run
- THEN the build fails naming the route

### Requirement: Request Validation and Safe Errors

Requests violating the OpenAPI schema (unknown fields, oversized strings, invalid types, malformed JSON) MUST be rejected with 400 before reaching a use case. Error bodies MUST NOT contain stack traces, exception class names, SQL, file paths or other internal details. Unexpected failures MUST return a generic 500 body of the same safe shape.

#### Scenario: Unknown field
- GIVEN an authenticated create request with an undeclared property
- WHEN it is sent
- THEN the response is 400 and nothing is persisted

#### Scenario: Oversized string
- GIVEN an authenticated request whose string exceeds the declared maximum length
- WHEN it is sent
- THEN the response is 400

#### Scenario: Invalid type
- GIVEN an authenticated request with a string where a number is declared
- WHEN it is sent
- THEN the response is 400

#### Scenario: No internal details
- GIVEN any 400 or 500 response
- WHEN its body is inspected
- THEN it contains no stack trace, exception class name, SQL fragment or file path

### Requirement: Contract Tests

Each OpenAPI operation MUST have a REST Assured contract test in its own test class. Responses MUST be validated against `openapi.yaml` through a request-and-response validator filter. Tests MUST assert the status codes declared in the contract, with separate test methods for missing credentials (401), schema violations (400) and cross-owner access. One spec-coverage test per operation MUST fail when an operation has no contract test.

#### Scenario: Response matches schema
- GIVEN an authenticated valid request to an operation
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

### Requirement: Mutation Testing Gate

PIT MUST run on the domain and use case packages with `mutationThreshold` of 80, and a score below 80 MUST fail the build. Kotlin-generated mutants (`equals`, `hashCode`, `toString`, `copy`, `componentN`) MUST be excluded.

#### Scenario: Score below threshold
- GIVEN a mutation score of 79
- WHEN the PIT task runs
- THEN it exits non-zero

#### Scenario: Score at threshold
- GIVEN a mutation score of 80 or more
- WHEN the PIT task runs
- THEN it exits 0

#### Scenario: Junk mutants excluded
- GIVEN a Kotlin data class
- WHEN PIT runs
- THEN no mutants are reported for its generated `equals`, `hashCode`, `toString`, `copy` or `componentN`

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
