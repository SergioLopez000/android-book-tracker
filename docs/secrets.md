# Secrets and credentials

Single inventory of every secret and credential of the project: who owns it, where it lives, who consumes it and when it rotates. The document is host-agnostic and never contains secret values.

## Rules

- Secret values live only in the stores named in the inventory (repository secrets, environment stores of the hosting provider, `local.properties` for local development). Never in the repository, a Dockerfile `ENV`/`ARG`, a workflow file or a log.
- Workflows reference only the secrets allow-listed in `scripts/check-secrets-contract.sh` (`ALLOWED_SECRETS`). Every allow-listed name must have a row in the inventory below.
- The owner of every row is `SergioLopez000`.
- `bash scripts/test-secrets-contract.sh` (local, via lefthook) fails when a `secrets.*` name used by a workflow, or an allow-list entry, is missing from this inventory, or when a required heading is removed.

## Inventory

| Secret | Class | Owner | Scope | Location | Consumers | Rotation | Status |
|---|---|---|---|---|---|---|---|
| `PROJECT_TOKEN` | Classic PAT | `SergioLopez000` | `project` | Repository secret | `board-sync.yml`, `token-expiry.yml` | At most 90 days; expiry date: 2027-02-15 (record here on every rotation) | active |
| `GITHUB_TOKEN` | Automatic token | `SergioLopez000` | Per job `permissions:` | Provided by GitHub Actions (referenced as `github.token`) | All workflows | Automatic, per run | active |
| `GOOGLE_BOOKS_API_KEY` | Provider API key | `SergioLopez000` | Books API | `local.properties` (local); would ship inside the APK | Android app (Google Books fallback, disabled while blank) | None while not provisioned; see the runbook before creating one | not provisioned; superseded by `GOOGLE_BOOKS_SERVER_API_KEY` (#29), which removes this client key |
| `SPRING_DATASOURCE_URL` | Database connection | `SergioLopez000` | Per environment (`dev`, `staging`) | Environment store of the host | Backend | 180 days or on leak | planned: created by #8 |
| `SPRING_DATASOURCE_USERNAME` | Database credential | `SergioLopez000` | Per environment | Environment store of the host | Backend | 180 days or on leak | planned: created by #8 |
| `SPRING_DATASOURCE_PASSWORD` | Database credential | `SergioLopez000` | Per environment | Environment store of the host | Backend | 180 days or on leak | planned: created by #8 |
| `NEON_HOST_CREDENTIALS` | Database host account | `SergioLopez000` | Per environment | Provider console | Operators | 180 days or on leak | planned: created by #8 |
| `ANDROID_RELEASE_KEYSTORE` | Signing keystore | `SergioLopez000` | Release builds | Offline backup plus encrypted store | Release pipeline | Never rotated; compromise procedure below | planned: created by #26 |
| `GOOGLE_BOOKS_SERVER_API_KEY` | Provider API key | `SergioLopez000` | Books API, server side | Environment store of the host | Backend | 365 days or on leak | planned: created by #29 |

## Environments

- Repository-scoped secrets are used only for CI automation (today `PROJECT_TOKEN`).
- Runtime secrets are scoped per environment: `dev` (created by #8) and `staging`, with required reviewers on `staging`.
- `GOOGLE_OAUTH_WEB_CLIENT_ID` (the Google Web client ID the backend validates ID tokens against) is public configuration, not a secret, so it is not inventoried. The backend refuses to start without it.
- If the hosting provider changes, keep the same model: one store per environment, no shared values between `dev` and `staging`.

## Rotation cadence

| Class | Cadence |
|---|---|
| Classic PAT (`PROJECT_TOKEN`) | At most 90 days |
| Database passwords | 180 days or on leak |
| Provider API keys | 365 days or on leak |
| Signing secrets (JWT) | 180 days |
| Android release keystore | Never rotated; if compromised, generate a new key and publish under a new upload key per the store procedure |
| `GITHUB_TOKEN` | Automatic, per run |

### Adding a secret

1. Add its row to the Inventory (owner `SergioLopez000`, scope, location, consumers, rotation, status).
2. If workflows use it, add it to `ALLOWED_SECRETS` in `scripts/check-secrets-contract.sh` in the same change.
3. Give the consuming job the narrowest `permissions:` and keep `e2e-tests` secret-free.
4. Run `bash scripts/test-secrets-contract.sh`.

## Runbook

### On leak or scheduled rotation

1. Create the new value at the provider.
2. Set it in each environment store that holds the old one.
3. Restart or redeploy the consumers.
4. Revoke the old value.
5. Verify the consumer works (health check, workflow run).
6. Record the date in the Inventory row.

### `PROJECT_TOKEN`

1. Generate a new classic PAT with only the `project` scope and an expiry of at most 90 days.
2. Replace the repository secret `PROJECT_TOKEN` (Settings, Secrets and variables, Actions).
3. Revoke the old token.
4. Update the expiry date in the `PROJECT_TOKEN` row and close the expiry reminder issue, if one is open.

### Google Books key (`GOOGLE_BOOKS_API_KEY`)

No key has been created. With a blank `GOOGLE_BOOKS_API_KEY`, `GoogleBooksMetadataSource` returns no results without calling Google, so metadata search uses Open Library only. Prefer the server-side key from #29 over creating a client key.

If a client key is ever created before #29, it ships inside the APK and must be treated as leaked from day one:

1. Google Cloud Console, APIs & Services, Credentials: create the key in a project owned by `SergioLopez000` and record the project here.
2. API restrictions: restrict the key to the Books API only.
3. Application restrictions: leave them at None. The app calls the REST API directly and does not send the `X-Android-Package` and `X-Android-Cert` headers, so an Android-app restriction would reject every request. Add that restriction only after the app sends those headers.
4. Revoking the key requires shipping a new APK, so rotation waits for #29.
