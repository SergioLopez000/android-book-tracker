# Research: backend-skeleton

```yaml
schema: gentle-ai.sdd-research/v1
revision: 3
request_id: backend-skeleton-research-1
intent_revision: 1
change: backend-skeleton
accessed_at: 2026-11-18
outcome: partial
admission:
  documentation: granted [WebFetch]
  open-web: granted [WebSearch, WebFetch]
observed_use: WebFetch (all sources), WebSearch (1 query, no usable result)
```

## Summary

Spring Boot 4.1.2 is the latest stable 4.x (22 Oct 2026). It manages Kotlin 2.3.21, needs Java 17+ and Kotlin 2.2+, and supports Gradle 8.14+ and 9.x. Boot 4.2.0-RC1 already uses Kotlin 2.4.21; the Boot Gradle plugin aligns `kotlin.version` with the applied Kotlin plugin, so 2.4.21 on 4.1.2 is plausible but unproven.

Key finding: swagger-request-validator 3.0.0 (2026-05-21) was renamed `openapi-request-validator-*`, targets Spring 7 and Jakarta Servlet 6.1, and compiles with `maven.compiler.release=21`, so it is not usable on JDK 17. The 2.46.x line targets Java 8 and Spring 6.2, with no Spring 7 module.

## Sources

| ID | Class | Title | Publisher | URL | Accessed | Excerpt |
|---|---|---|---|---|---|---|
| S1 | documentation | Spring Boot releases | Spring (GitHub) | https://github.com/spring-projects/spring-boot/releases | 2026-11-18 | v4.1.2 Latest, 22 Oct; v4.0.9 22 Oct; Kotlin 2.4.21 in v4.2.0-RC1 |
| S2 | documentation | Spring Boot 4.1 Release Notes, 4.0 Migration Guide | Spring (GitHub wiki) | https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.1-Release-Notes | 2026-11-18 | Kotlin 2.3.21; Flyway 12.4.0; Spring Framework 7.0.8; Security 7.1.0; starters webmvc/security/actuator/jdbc/flyway/validation; Jackson group tools.jackson |
| S3 | documentation | Spring Boot reference (system requirements, Kotlin, Testcontainers, data init); Spring Security Kotlin DSL | Spring | https://docs.spring.io/spring-boot/reference/features/kotlin.html | 2026-11-18 | Java 17 to 27; Kotlin 2.2+; plugin aligns kotlin.version; flyway-database-postgresql; spring-boot-testcontainers |
| S4 | open-web | Maven Central metadata and POMs | Maven Central | https://repo1.maven.org/maven2/com/atlassian/oai/openapi-request-validator/3.0.0/openapi-request-validator-3.0.0.pom | 2026-11-18 | 3.0.0: jackson 2.21.3, rest-assured 5.5.7, spring 7.0.7, release 21. Latest: testcontainers 2.0.5, rest-assured 7.0.0-RC1, pitest 1.30.2, pitest-junit5-plugin 1.2.3, jackson-module-kotlin 3.3.0 |
| S5 | documentation | gradle-pitest-plugin releases | szpak (GitHub) | https://github.com/szpak/gradle-pitest-plugin/releases | 2026-11-18 | 1.19.0: initial Gradle 9 support; Gradle 8.4+, Java 17; default PIT 1.22.1 |
| S6 | documentation | Container registry; Dependabot options reference | GitHub | https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry | 2026-11-18 | First publish is private; org.opencontainers.image.source label; Dependabot `directories` with globs |
| S7 | documentation | CodeQL supported languages | GitHub | https://codeql.github.com/docs/codeql-overview/supported-languages-and-frameworks/ | 2026-11-18 | Kotlin 1.8.0 through 2.4.20; Java 7 to 26 |
| S8 | open-web | openapi-generator releases; distroless README; Docker Hub eclipse-temurin | OpenAPITools, GoogleContainerTools, Docker | https://github.com/OpenAPITools/openapi-generator/releases | 2026-11-18 | 7.27.0 latest; 7.25.0 kotlin Jackson 3 + Boot 4; distroless java17/java21-debian13 nonroot; Temurin non-root needs writable /tmp |

## Claims

- Q1
  - C1 Latest stable Boot 4.x is 4.1.2; 4.0.9 is the latest 4.0 patch [S1].
  - C2 Boot 4.1 manages Kotlin 2.3.21, Spring Framework 7.0.x, Security 7.1.0, Flyway 12.4.0 [S2].
  - C3 Boot needs Kotlin 2.2+, Java 17 to 27, Gradle 8.14+ or 9.x [S3].
  - C4 The Boot plugin aligns `kotlin.version` with the applied Kotlin plugin; no statement says 4.1.x is tested with 2.4.x [S3, S1].
  - C5 Jackson 3 Kotlin module is `tools.jackson.module:jackson-module-kotlin` 3.3.0 [S4, S2].
- Q2
  - C6 Starters: `spring-boot-starter-webmvc`, `-security`, `-actuator`, `-jdbc`, `-flyway`, `-validation`, each with a `-test` counterpart [S2].
  - C7 `spring-boot-starter-flyway` exists; PostgreSQL still needs `org.flywaydb:flyway-database-postgresql` [S3, S2].
- Q3
  - C9 Testcontainers 2.0.5; `testcontainers-postgresql` 2.0.5, package `org.testcontainers.postgresql`; JUnit 5 artifact `testcontainers-junit-jupiter` [S4, S3].
  - C10 `@ServiceConnection` needs `spring-boot-testcontainers` [S3].
- Q4
  - C11 swagger-request-validator 3.0.0 renamed to `com.atlassian.oai:openapi-request-validator-*`; old artifacts are relocation POMs [S4].
  - C12 3.0.0 targets Spring 7.0.7 and Jakarta Servlet 6.1.0, with `-spring-webmvc`, `-restassured`, `-mockmvc` modules [S4].
  - C13 3.0.0 compiles with release 21; JDK 17 is very likely incompatible (inferred from the release target) [S4].
  - C14 3.0.0 still uses Jackson 2.21.x [S4].
  - C15 2.46.1 targets Java 8, Spring 6.2.17, REST Assured 5.1.1 [S4].
  - C17 openapi-generator 7.27.0; kotlin-spring Boot 4 + Jackson 3 support since 7.25.0 [S8].
- Q5
  - C18 REST Assured stable 6.3.0; 7.0.0-RC1 published 2026-11-12; the validator's REST Assured module pins 5.5.7 [S4, S8].
- Q6
  - C19 gradle-pitest-plugin 1.19.0, initial Gradle 9 support, default PIT 1.22.1; PIT latest 1.30.2, so set `pitestVersion` [S5, S4].
  - C20 pitest-junit5-plugin latest 1.2.3 (May 2025) [S4].
- Q7
  - C22 Kotlin DSL: `http { authorizeHttpRequests { authorize(...) } }` with import `org.springframework.security.config.annotation.web.invoke` [S3].
- Q8
  - C25 Temurin supports non-root but needs a writable `/tmp` for the truststore [S8].
  - C26 Distroless publishes `java17-debian13` and `java21-debian13` with `nonroot` tags [S8].
- Q9
  - C27 First GHCR publish is private; link via `org.opencontainers.image.source` [S6].
  - C28 Dependabot supports `directories` with globs for docker and gradle [S6].
- Q10
  - C30 CodeQL supports Kotlin 1.8.0 through 2.4.20 (2.4.21 is one patch beyond) and Java 7 to 26 [S7].

## Contradictions, Uncertainty and Freshness

- Sources dated 2026-04 to 2026-11; REST Assured 7.0.0-RC1 and openapi-generator facts are weeks old.
- Kotlin 2.4.21 on Boot 4.1.2 is a minor jump from the tested 2.3.21; no source confirms or denies it.
- C13 is inferred from the POM release target; some excerpts came from a summarizer and are not verbatim.

## Gaps (unvalidated, no claims)

- JdbcClient via `spring-boot-starter-jdbc` (C8), Actuator exposure properties in Boot 4 (C24), stateless 401 config (C23), PIT `excludedMethods`/`avoidCallsTo` syntax (C21).
- Primary changelog for validator 3.0.0 (OpenAPI 3.1, Jackson 3) not retrieved (C16).
- Dependabot digest bumps for `image:tag@sha256` (C29) and CodeQL with two Gradle builds in one job (C31).
- Temurin 17 JRE tag list; paid Kotlin PIT plugins.

## Product Choices (non-authoritative)

1. JDK 21 (enables openapi-request-validator 3.0.0) versus JDK 17 with a different contract validator.
2. Kotlin 2.4.21 override versus Boot-managed 2.3.21 (inside CodeQL's documented range).
3. Validator-based contract-first versus openapi-generator kotlin-spring interfaces.

## Decision Impact

| Area | Impact |
|---|---|
| Build layout | Separate `/backend` build fits; Gradle 9.9.1 is within Boot's range; PIT plugin Gradle 9 support is "initial"; Dependabot needs a `/backend` entry or `directories`. |
| Contract tooling | Validator 3.0.0 needs Java 21 and brings Jackson 2; 2.46.x is misaligned with Spring 7. On JDK 17 the option is openapi-generator 7.27.0 kotlin-spring. |
| JDK 17 vs 21 | Boot 4.1.2 supports 17 to 27; the only hard blocker for 17 is the validator. CodeQL supports both. |
| Docker base | Distroless `java17/java21-debian13:nonroot` or Temurin with non-root and writable `/tmp`; pin by digest; GHCR private on first publish. |
| PIT config | Plugin 1.19.0 with explicit `pitestVersion` 1.30.2; pitest-junit5-plugin 1.2.3; Kotlin exclusions unverified. |

## Apply Spikes

1. Boot 4.1.2 with Kotlin 2.4.21 (override) and with managed 2.3.21.
2. Jackson 3 Kotlin module with Boot 4.1.2.
3. Contract validator on the chosen JDK; Jackson 2 and 3 coexisting.
4. REST Assured 6.3.0 vs 7.0.0-RC1 with Jackson 3 and Spring 7.
5. PIT plugin 1.19.0 on Gradle 9.9.1 with PIT 1.30.2 and Kotlin exclusions.
6. Spring Security 7.1 stateless 401 Kotlin DSL; Actuator exposure.
7. Dependabot digest bumps; GHCR `packages: write` and visibility.
8. CodeQL with two Gradle builds in one job; Kotlin 2.4.21 vs 2.4.20.
9. Temurin 17 JRE tags and non-root setup.

## Revision 2 (request backend-skeleton-research-2, intent revision 2)

```yaml
request_id: backend-skeleton-research-2
intent_revision: 2
accessed_at: 2026-11-18
outcome: partial
admission:
  documentation: granted [WebFetch]
  open-web: granted [WebSearch, WebFetch]
note: extends revision 1; revision 1 claims stand
```

### Sources (revision 2)

| ID | Class | Title | Publisher | URL | Excerpt |
|---|---|---|---|---|---|
| R1 | documentation | Spring Boot Reference: SQL Databases | Spring | https://docs.spring.io/spring-boot/reference/data/sql.html | "Spring's JdbcClient is auto-configured based on the presence of a NamedParameterJdbcTemplate." |
| R2 | documentation | Spring Boot Reference: Endpoints | Spring | https://docs.spring.io/spring-boot/reference/actuator/endpoints.html | "By default, only the health endpoint is exposed over HTTP and JMX."; `management.endpoints.access.default=none`; show-details default `never` |
| R3 | documentation | Spring Security 7.1.2: Session Management | Spring | https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html | Kotlin: `sessionManagement { sessionCreationPolicy = SessionCreationPolicy.STATELESS }` |
| R4 | documentation | Spring Security: Kotlin Configuration | Spring | https://docs.spring.io/spring-security/reference/servlet/configuration/kotlin.html | `authorizeHttpRequests { authorize(anyRequest, authenticated) }`; `authorize(allowedPaths, permitAll)` |
| R5 | documentation | HttpStatusEntryPoint Javadoc | Spring | https://docs.spring.io/spring-security/reference/api/java/org/springframework/security/web/authentication/HttpStatusEntryPoint.html | "An AuthenticationEntryPoint that sends a generic HttpStatus as a response." |
| R6 | documentation | gradle-pitest-plugin README; PIT quickstart | szpak; pitest.org | https://github.com/szpak/gradle-pitest-plugin | `targetClasses.set(...)`, `pitestVersion.set(...)`, `outputFormats.set(setOf("XML","HTML"))`, `timestampedReports.set(false)`; excludedMethods "List of globs to match against method names"; avoidCallsTo "List of packages and classes ... outside the scope of mutation" |
| R7 | documentation | atlassian/openapi-request-validator README, tags, pom.xml | Atlassian | https://github.com/atlassian/openapi-request-validator | "The project requires JDK21+ to build."; "Compatible with Spring 7+, Spring Boot 4+ and the Jakarta namespace. Requires JDK17+."; release 21; jackson 2.21.3; tag 3.0.0 2026-05-13 |
| R8 | documentation | dependabot-core issue #14065 | GitHub | https://github.com/dependabot/dependabot-core/issues/14065 | "If you already have a digest, Dependabot handles it correctly" / "But it won't add a digest." Closed via PR #14071 (2026-02-04) |
| R9 | documentation | CodeQL supported languages | GitHub | https://codeql.github.com/docs/codeql-overview/supported-languages-and-frameworks/ | Kotlin 1.8.0 to 2.4.20; Java 7 to 26 |
| R10 | documentation | Creating CodeQL databases (CLI) | GitHub | https://docs.github.com/code-security/codeql-cli/creating-codeql-databases/ | "Use --no-daemon because a build delegated to an existing daemon cannot be detected by CodeQL."; `--command` may be given multiple times |
| R11 | documentation | CodeQL code scanning for compiled languages | GitHub | https://docs.github.com/en/code-security/code-scanning/creating-an-advanced-setup-for-code-scanning/codeql-code-scanning-for-compiled-languages | "CodeQL will analyze whatever source code is built by your specified build steps."; "Kotlin analysis requires a build." |
| R12 | documentation | openapi-generator kotlin-spring options | OpenAPITools | https://github.com/OpenAPITools/openapi-generator/blob/master/docs/generators/kotlin-spring.md | `useSpringBoot4`, `useJackson3` ("Only available with useSpringBoot4"), `interfaceOnly`, `useTags`, defaults false |
| R13 | open-web | Gradle Plugin Portal org.openapi.generator; releases | Gradle; OpenAPITools | https://plugins.gradle.org/plugin/org.openapi.generator | 7.27.0 (2026-11-02); v7.25.0 Kotlin Jackson 3 + Boot 4; README: supports Configuration Cache, Build Cache |

### Claims (revision 2)

- G1: JdbcClient is auto-configured when a NamedParameterJdbcTemplate exists [R1]. That `spring-boot-starter-jdbc` provides it is inferred.
- G2: Only health is exposed over HTTP by default; `management.endpoints.access.default=none` plus per-endpoint access opts back in; health show-details defaults to `never` [R2].
- G3: `sessionManagement { sessionCreationPolicy = STATELESS }` is documented in the Kotlin DSL [R3]; `HttpStatusEntryPoint(HttpStatus)` returns a bare status [R5]; `authorize(path, permitAll)` and `authorize(anyRequest, authenticated)` [R4].
- G4: excludedMethods (method-name globs), avoidCallsTo (packages/classes), mutationThreshold (0-100), outputFormats (HTML, XML, CSV), timestampedReports default false [R6].
- G5: openapi-request-validator 3.0.0 is the rename of swagger-request-validator (tag 2026-05-13); build needs JDK 21, POM release 21, README says the Spring adapter "Requires JDK17+"; Jackson 2.21.3 [R7].
- G6: Dependabot updates an existing `image:tag@sha256` digest [R8].
- G7: Manual build mode analyzes whatever the build steps compile; Kotlin needs a build; use `--no-daemon` [R10, R11]. Kotlin 2.4.21 is outside the documented 2.4.20 [R9].
- G8: openapi-generator plugin 7.27.0; kotlin-spring `useSpringBoot4`, `useJackson3`, `interfaceOnly`, `useTags` [R12, R13].

### Contradictions and Uncertainty (revision 2)

- R7 contradiction: build needs JDK 21 and POM release is 21, but the README says JDK 17+. Published bytecode level unverified.
- Not documented: Kotlin DSL for `exceptionHandling` with `HttpStatusEntryPoint` and `csrf { disable() }`; PIT guidance for Kotlin-generated methods; validator OpenAPI 3.1 and Jackson 3 support; Dependabot digest-adding behavior; two Gradle builds in one CodeQL job; openapi-generator plugin on Gradle 9.9.1.

### Remaining Apply Spikes (revision 2)

1. Security Kotlin DSL: 401 entry point, csrf, permitAll health (MockMvc test).
2. openapi-request-validator 3.0.0 bytecode level (`javap`) on JDK 17 and 21; OpenAPI 3.1; Jackson 2 and 3 coexistence.
3. openapi-generator 7.27.0 on Gradle 9.9.1 with `useSpringBoot4`, `interfaceOnly`, `useTags`, `useJackson3`.
4. PIT plugin on Gradle 9.9.1 with Kotlin exclusions.
5. CodeQL with two Gradle build steps; Kotlin 2.4.21 warning.
6. Dependabot digest behavior.

### Decision Impact (revision 2)

| Area | Impact |
|---|---|
| JDK 17 vs 21 | JDK 21 removes the validator's documented build-target unknown and is within CodeQL's range. JDK 17 relies on an unverified README line. Evidence leans to JDK 21. |
| Contract tooling | Validator 3.0.0 on JDK 21, or openapi-generator 7.27.0 kotlin-spring (Boot 4, Jackson 3) with unverified Gradle 9.9.1 support. |
| Kotlin version | 2.4.21 is outside CodeQL's documented range; 2.4.20 or Boot-managed 2.3.21 stays inside. |
| Actuator | Health-only exposure is the default; details hidden by default. |
| Docker | Keep `image:tag@sha256` pins; Dependabot updates existing digests. |

## Revision 3: closure (user decision 2026-11-18)

```yaml
outcome: done
closed_by: user decision on 2026-11-18 ("sí, ciérralo así")
rescoped: questions that need runtime verification are removed from research scope and become mandatory apply spikes
deferred_to_apply_spikes:
  - SP1 Spring Security 7.1 Kotlin DSL returns 401 via HttpStatusEntryPoint, csrf disabled, permitAll /actuator/health (MockMvc test)
  - SP2 openapi-request-validator 3.0.0 published bytecode level (javap) on JDK 17 and 21; OpenAPI 3.1; Jackson 2 and 3 coexistence
  - SP3 openapi-generator 7.27.0 Gradle plugin on Gradle 9.9.1 with kotlin-spring useSpringBoot4, interfaceOnly, useTags, useJackson3
  - SP4 gradle-pitest-plugin 1.19.0 on Gradle 9.9.1 with PIT 1.30.2 and Kotlin-generated method exclusions
  - SP5 CodeQL java-kotlin manual build with two Gradle builds in one job; Kotlin version within documented range
  - SP6 Dependabot digest behavior for Dockerfile FROM image:tag@sha256
rule: spikes run first in apply, before the rest of the implementation; a failed spike reopens the affected decision
```

No unvalidated claim is added by this closure; the gaps above remain unclaimed.
