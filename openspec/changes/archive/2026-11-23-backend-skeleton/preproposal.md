# Pre-proposal state: backend-skeleton

```yaml
schema: gentle-ai.sdd-preproposal/v1
revision: 6
change: backend-skeleton
issue: 6
exploration:
  outcome: done
  openspec_ref: openspec/changes/backend-skeleton/exploration.md
  engram_ref: sdd/backend-skeleton/explore
research:
  selected: true
  request_id: backend-skeleton-research-1
  intent_revision: 1
  source_classes: [documentation, open-web]
  admission: granted (documentation, open-web)
  outcome: partial (research revision 1)
  followup:
    request_id: backend-skeleton-research-2
    intent_revision: 2
    source_classes: [documentation, open-web]
    admission: granted (documentation, open-web)
    outcome: partial (research revision 2)
  closure:
    outcome: done (research revision 3)
    decided_by: user 2026-11-18
    deferred_to_apply_spikes: [SP1, SP2, SP3, SP4, SP5, SP6]
    questions:
      - G1 JdbcClient availability via spring-boot-starter-jdbc in Boot 4.1
      - G2 Actuator endpoint exposure properties in Boot 4.1 (expose only health)
      - G3 Spring Security 7.1 stateless API returning 401 (not redirect) in Kotlin DSL
      - G4 gradle-pitest-plugin excludedMethods / avoidCallsTo syntax for Kotlin-generated methods
      - G5 openapi-request-validator 3.0.0 changelog (OpenAPI 3.1, Jackson 3, minimum Java)
      - G6 Dependabot updates for Docker FROM image:tag@sha256 digests
      - G7 CodeQL java-kotlin manual build with two separate Gradle builds in one job
      - G8 openapi-generator kotlin-spring Boot 4 options (useSpringBoot4/interfaceOnly) and Gradle plugin version
  openspec_ref: openspec/changes/backend-skeleton/research.md
  engram_ref: sdd/backend-skeleton/research
  questions:
    - Q1 Spring Boot latest 4.x and compiling with Kotlin 2.4.21 (Boot-managed 2.3.21); Jackson 3 Kotlin module
    - Q2 Boot 4 modular starter names (webmvc, security, actuator, jdbc, flyway, validation); Flyway 12 PostgreSQL module
    - Q3 Testcontainers 2.x GA, PostgreSQL module artifact/package, @ServiceConnection in Boot 4
    - Q4 swagger-request-validator latest, OpenAPI 3.1, Spring Framework 7 / Jakarta, REST Assured module, Jackson 3; alternatives
    - Q5 REST Assured latest and Jackson 3 / Groovy 4 compatibility
    - Q6 gradle-pitest-plugin and pitest-junit5-plugin latest, Gradle 9 support, excluding Kotlin-generated methods
    - Q7 Spring Security 7 deny-by-default Kotlin DSL, stateless 401, Actuator exposure in Boot 4
    - Q8 Temurin 17 JRE image tags vs distroless java17, non-root, digest pinning with Dependabot
    - Q9 GHCR push with GITHUB_TOKEN, package visibility, Dependabot docker and gradle directories
    - Q10 CodeQL java-kotlin supported Kotlin versions (2.4.x), two Gradle builds in one job
product_decisions:
  status: confirmed
  confirmed_by: user, one by one, 2026-11-18
  confirmed:
    jdk: "21 for /backend (app stays on 17)"
    build_layout: "separate Gradle build under /backend (own wrapper, settings, catalog)"
    contract_tooling: "hand-written controllers + openapi-request-validator 3.0.0 (runtime filter + REST Assured); spec-coverage test per operation; SP3 dropped unless SP2 fails"
    persistence: "Spring JdbcClient with explicit owner_id filter in every query"
    kotlin_version: "2.3.21 (Spring Boot 4.1 managed) for /backend; app stays on 2.4.21"
    docker_in_scope: "keep Dockerfile + GHCR in #6 (user 2026-11-18)"
    docker_image: "distroless java21-debian13:nonroot pinned by digest; jar built on the runner and COPY-ed"
    ghcr: "build+push on push to dev with tags sha-<commit> and dev; promote.yml retags the same digest as staging (packages: write only on that job); package private for now, visibility decided in #8"
    deny_by_default: "no deployment until #26 and #54; no runtime security bypass; CRUD tests authenticate with a test-only user"
    pit: "mutationThreshold 80 on domain and use cases; blocking in ci-gate from the start"
    ci_gate_jobs: "backend-tests and contract-tests (plus PIT) are ci-gate needs; shown in the report job"
    sonarcloud_dod: "removed from #6 DoD, replaced by the issue-form line (local hooks + CodeQL + secret-scan)"
    pr_split: "3 chained PRs: PR1 build+domain+persistence+security+CI (SP1,SP4,SP5,SP6); PR2 openapi+controllers+contract tests+PIT (SP2); PR3 Docker+GHCR+promote retag+Dependabot docker+README"
    chain_strategy: "stacked-to-main: sequential PRs into dev; PR1 uses branch feat/6-backend-skeleton; Refs #6 on PR1-PR2, Closes #6 on PR3; promote only after PR3"
    openapi_location: "backend/src/main/resources/openapi.yaml (move later if #10 needs it)"
    spec_deltas: "NEW backend-api; MODIFIED ci-pipeline; MODIFIED code-quality-gates (Formatting, Detekt, Coverage Report, Pre-commit Hook)"
proposal_ready: true
```
