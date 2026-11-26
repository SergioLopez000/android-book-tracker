# Delta for ci-pipeline

## MODIFIED Requirements

### Requirement: Secret Inventory Document

`docs/secrets.md` (in English) MUST exist and MUST list every secret and credential with its owner, scope, storage location and rotation policy. The owner of every row MUST be `SergioLopez000`. Rows for secrets that do not yet exist MUST be marked as planned and name the change that creates them ("created by #8" or "created by #29"). The inventory MUST NOT contain a `JWT_SIGNING_SECRET` row: the backend validates Google ID tokens against public JWKS keys and holds no signing secret. OAuth client IDs (Web and Android) are non-secret configuration and MUST NOT be listed as secrets. The document MUST describe the Environments model (`dev` and `staging`, with `dev` created by #8), a rotation runbook (create the new value, set it in each environment, restart or redeploy, revoke the old value, verify), per-class rotation cadence, and the stop-gap manual restriction of the shipped Google Books key with its rotation gated on #29. The document MUST remain host-agnostic and MUST NOT contain secret values.
(Previously: the inventory listed a planned `JWT_SIGNING_SECRET` row created by #8.)

#### Scenario: Row fields complete
- GIVEN any row of the inventory
- WHEN it is read
- THEN it has owner `SergioLopez000`, scope, location and rotation policy

#### Scenario: Planned rows marked
- GIVEN a secret that does not exist yet
- WHEN its row is read
- THEN it is marked planned and names #8 or #29 as its creator

#### Scenario: Obsolete signing secret removed
- GIVEN `docs/secrets.md`
- WHEN its inventory rows are listed
- THEN no row named `JWT_SIGNING_SECRET` exists

#### Scenario: Client IDs not inventoried as secrets
- GIVEN `docs/secrets.md`
- WHEN its inventory rows are listed
- THEN no row lists a Google OAuth client ID

#### Scenario: Runbook and cadence present
- GIVEN `docs/secrets.md`
- WHEN its sections are listed
- THEN it has the Environments model, the rotation runbook and a rotation cadence per secret class

#### Scenario: Google Books key gated
- GIVEN the Google Books key row or runbook entry
- WHEN it is read
- THEN it states that no client key exists (not provisioned) and that the row is superseded by `GOOGLE_BOOKS_SERVER_API_KEY` (#29)
- AND the runbook warns that an Android-app restriction would reject the direct REST calls

#### Scenario: Secrets contract check still passes
- GIVEN the updated `docs/secrets.md`
- WHEN `scripts/check-secrets-contract.sh` and its self-test `scripts/test-secrets-contract.sh` run
- THEN both pass
