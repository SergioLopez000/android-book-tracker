# Proposal: Deployment secrets and credential rotation (#54)

## Intent

No secret has a recorded owner, location or rotation policy. The backend's DB config contract is implicit, and nothing alerts before `PROJECT_TOKEN` expires. #54 blocks #8, so the contract, inventory and CI token policy must exist before any environment is deployed.

## Scope

### In Scope
- Backend fail-fast validator in `config/`: rejects missing or blank `SPRING_DATASOURCE_URL`/`USERNAME`/`PASSWORD` with a clear message. No credential defaults in any profile.
- Smoke case "fails without credentials"; `verifyPackagedArtifact` scans the jar for credential patterns and `jdbc:` URLs with embedded passwords.
- Static script: `secrets.*` allow-list, `permissions:` on every workflow and job, no secret-like `ENV`/`ARG` in the Dockerfile.
- `docs/secrets.md` (English): inventory (owner `SergioLopez000`, scope, location, rotation), planned rows marked "created by #8/#29", Environments model, runbook, per-class cadence. Drift guard test. Spanish README section "Secretos y rotación".
- `PROJECT_TOKEN` expiry reminder: schedule plus `workflow_dispatch`, opens or updates one issue when fewer than 14 days remain.

### Out of Scope
- Live rotation drill, real environment values, creating the `dev` Environment (#8).
- Rotating the shipped Google Books key (gated on #29). Only a manual key restriction is documented.
- GitHub App migration (follow-up issue), managed secret stores.
- Trivy or gitleaks in CI (static analysis stays local via lefthook).

## Capabilities

### New Capabilities
None

### Modified Capabilities
- `backend-api`: DB config from env only, fail-fast on missing or blank credentials.
- `ci-pipeline`: least-privilege permissions, `secrets.*` allow-list, no secret-like Dockerfile `ENV`/`ARG`, extended `verifyPackagedArtifact`.
- `issue-workflow`: `PROJECT_TOKEN` expiry reminder workflow.

## Approach

Option B from the exploration (docs plus enforced contract). Tests come first in every unit. Commits:
1. Static contract checks.
2. Backend validator, smoke case, `verifyPackagedArtifact`.
3. `docs/secrets.md`, drift guard, README.
4. Expiry reminder: `curl -s -D - -o /dev/null` with an Authorization header, defensive date parsing. A missing header opens a warning issue. The first run is a manual dispatch to confirm the header format.

The validator must not run in `NoDatabaseTestConfig` or `@ServiceConnection` contexts. Estimate: 300-500 lines (budget 800).

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `backend/src/main/kotlin/**/config/` | New | Credential validator plus unit test |
| `backend/build.gradle.kts` | Modified | `verifyPackagedArtifact` patterns |
| `scripts/test-image-smoke.sh` | Modified | Missing-credentials case |
| `scripts/` (new static check) | New | Allow-list, permissions, Dockerfile, drift guard |
| `.github/workflows/` | New | Expiry reminder workflow |
| `docs/secrets.md`, `README.md` | New/Modified | Inventory, runbook, link |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Criteria 1 and 3 verifiable only at contract level until #8 | High | Acceptance accepts contract evidence |
| Validator breaks test slices | Med | Read resolved properties; tests cover both contexts |
| String-based static checks are brittle | Med | Keep `test-image-config.sh` style, self-tests |
| Expiry header undocumented | Med | Defensive parse, warn on missing header, manual first run |
| APK key already leaked | High | Manual restriction now, rotation in #29 |
| Live state not inspected | Med | Confirm while writing the inventory |
| #8 host may not use GitHub Environments | Low | Host-agnostic inventory |

## Rollback Plan

Revert the four work-unit commits independently. Disable the reminder workflow in the Actions UI. No data or secret values change.

## Dependencies

- Blocks #8. Google Books rotation depends on #29.

## Success Criteria

- [ ] The app fails at startup with a clear message when DB credentials are missing or blank. Unit and smoke tests prove it.
- [ ] Static checks pass locally through the lefthook pre-commit hook; `verifyPackagedArtifact` passes in `check` and CI.
- [ ] The drift guard passes, and every `secrets.*` name is listed in the inventory.
- [ ] A manual run of the reminder confirms the header format, and duplicate issues are not created.
