# Pre-proposal state: user-accounts

```yaml
schema: gentle-ai.sdd-preproposal/v1
revision: 2
change: user-accounts
issue: 26
exploration:
  outcome: done
  openspec_ref: openspec/changes/user-accounts/exploration.md
  engram_ref: sdd/user-accounts/explore
research:
  selected: true
  source_classes: [documentation]
  admission: granted (public documentation only)
  outcome: partial (research revision 2; the user explicitly accepted partial evidence and chose the provider on 2026-11-25)
  unresolved: google-services plugin on AGP 9.1.1 (Firebase only, not chosen); googleid minSdk; no official fake provider for Credential Manager on an account-less emulator
  openspec_ref: openspec/changes/user-accounts/research.md
  engram_ref: sdd/user-accounts/research
product_decisions:
  - id: D1
    topic: Identity provider
    value: Google Sign-In through Credential Manager (androidx.credentials 1.6.0, googleid 1.2.1), no Firebase; the backend validates Google ID tokens
    status: confirmed
  - id: D2
    topic: "New install, sign in, items downloaded" criterion
    value: app-side download moved to #10; #26 proves the server half with a contract test (a fresh token for the same sub lists the owner's books)
    status: confirmed
  - id: D3
    topic: Sign-in at app start
    value: required; the app cannot be used without an account, and the offline e2e signs in through a debug-only fake gateway
    status: confirmed
  - id: D4
    topic: Session token storage
    value: memory only; non-sensitive account metadata in DataStore; Tink-encrypted persistence deferred to #10
    status: confirmed
  - id: D5
    topic: Delivery
    value: a planning PR, then 3 sequential PRs into dev (S1 backend, S2a app session core, S2b sign-in UI and e2e)
    status: confirmed
proposal_ready: true
```

## Technical defaults carried into the proposal and design

- Contract tests use bearer tokens signed by a runtime-generated key and served from a local JWKS. Nothing private is committed.
- The 401 response stays bodiless. The owner is the raw `sub`, pinned to the Google issuers. JWKS failures fail closed with a 401.
- The backend refuses to start without the issuer and audience configuration.
- OAuth client IDs are non-secret configuration, blank by default.
- Cleartext is allowed only for `10.0.2.2`, and only in debug builds.
