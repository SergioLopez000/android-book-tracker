# Apply progress: deployment-secrets (#54)

## Unit 1 (done, commit 0232142; plan commit 001f7cb)
Tasks 1.1 to 3.5. Checker and harness for the secrets contract, job-level permissions in the three workflows, lefthook job `secrets-contract`, README and verify-rule docs. Learned: macOS sed lacks GNU `0,/re/`; fixtures use env-var overrides in `new_root`.

## Unit 2 (DONE): Docker daemon was unavailable earlier, now resolved
Done: 4.1, 4.2, 4.3, 4.4, 4.6, 5.1, 5.2, 5.3, 5.4, 5.5, 6.3.
Open: 4.5 (unit suite green; `integrationTest` and `contractTest` need Docker for Testcontainers), 6.1, 6.2 (image smoke needs Docker), 6.4 (commit withheld until Docker tasks pass).
Blocker: Rancher Desktop is not running (`docker` fails to connect to `/var/run/docker.sock`; `rdctl` reports connection refused).

### Confirmed facts
- Spring Boot 4.1.2 package: `org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails` (and `DataSourceAutoConfiguration` in the same package). Design Open Question 1 resolved.
- `./gradlew spotlessCheck detekt` does not exist in this build (no lint plugin), so 4.6 had nothing to run beyond the compile and tests.

### RED evidence
- 4.1 to 4.3: `./gradlew test --tests '*DataSourceCredentialsValidatorTest'` failed to compile: `Unresolved reference 'missingVariables'` and `'DataSourceCredentialsValidator'`.
- 5.1: with `planted:\n  password: x` appended to `application.yaml`, `verifyPackagedArtifact` PASSED before the scan existed (baseline RED).
### GREEN evidence
- 4.4: `DataSourceCredentialsValidatorTest` 10 tests, 0 failures; full unit suite green.
- 5.2/5.4 failing planted credential (value `x` not printed):
  ```
  > Packaged artifact violations:
    credential-like key with a literal value in BOOT-INF/classes/application.yaml:31
  BUILD FAILED
  ```
- 5.3 triangulation (each planted alone, then reverted):
  - `url: jdbc:postgresql://u:secretpw@host/db` -> `jdbc URL with embedded credentials in BOOT-INF/classes/application.yaml:31`
  - `jdbc:postgresql://host/db?password=secretpw` -> same rule, line 31
  - `api-key: abc` -> `credential-like key with a literal value in ...application.yaml:31`
  - `spring.datasource.username: appuser` -> `datasource literal (url, username or password) in ...:32`
  - `password: ${SOME_PASSWORD}` -> passes (exempt)
  - `password: ${SOME_PASSWORD:fallbackpw}` -> fails (placeholder default)
  - Neither `secretpw`, `abc` nor `fallbackpw` appeared in any output.
- 5.4 passing clean run: `./gradlew verifyPackagedArtifact` exit 0, no violations, `application.yaml` restored via git.
- 5.5: `ci.yml` backend-tests now runs `./gradlew test integrationTest verifyPackagedArtifact koverXmlReport koverHtmlReport --continue`; `actionlint` clean.
- 6.3: no `ENV`/`ARG` added; `bash scripts/test-secrets-contract.sh` passes.

### Docker follow-up (4.5, 6.1, 6.2, 6.4 done)
- Rancher Desktop socket is `~/.rd/docker.sock`; use `DOCKER_HOST=unix://$HOME/.rd/docker.sock` (plus `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`, `TESTCONTAINERS_RYUK_DISABLED=true` for Testcontainers).
- 4.5: `./gradlew test integrationTest contractTest` BUILD SUCCESSFUL (integrationTest failed first only for lack of DOCKER_HOST).
- 6.1 RED: with the validator moved aside, `test-image-smoke.sh` failed: `FAIL (no-datasource): logs lack the missing-configuration message naming SPRING_DATASOURCE_URL`.
- 6.2 GREEN: all cases pass (no-datasource, blank-password, health 200, /api/books 401); `shellcheck` clean.

## Unit 3 (done, commit pending sha in report)
Done: 7.1, 7.2, 8.1 to 8.5, 9.1, 9.2, 9.3, 9.5. Open: 9.4 [USER] (manual Google Cloud Console restriction; steps are in `docs/secrets.md`, section Runbook).
Files: `scripts/check-secrets-contract.sh` (check 4 drift guard), `scripts/test-secrets-contract.sh` (default inventory fixture plus drift cases), `docs/secrets.md`, `README.md` (section "Secretos y rotación", lefthook note), `openspec/config.yaml` (verify rule).
- RED (7.1): after adding drift fixtures, 8 cases failed (`expected failure, got success`: omitted PROJECT_TOKEN, allow-list entry, missing file, four missing headings, owner).
- GREEN (7.2/8.x): only the real-repo case failed until `docs/secrets.md` existed; then `All secrets-contract checks passed`. PROJECT_TOKEN is the only `secrets.*` name.
- TRIANGULATE (8.5): removing the PROJECT_TOKEN row gave `FAIL: inventory: secret PROJECT_TOKEN is missing...`; removing `## Runbook` gave `lacks the heading ## Runbook`; both reverted.
- Learned: shellcheck (lefthook) rejects `$heading[` (SC1087) and backticks in single quotes (SC2016); the pre-commit glob matches `docs/secrets.md`.
