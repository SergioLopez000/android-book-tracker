# Pre-proposal state: freeze-backend

```yaml
schema: gentle-ai.sdd-preproposal/v1
revision: 1
change: freeze-backend
issue: 91
exploration:
  outcome: done
  openspec_ref: openspec/changes/freeze-backend/exploration.md
  engram_ref: sdd/freeze-backend/explore
research:
  selected: true
  source_classes: [documentation]
  admission: granted (public GitHub documentation)
  outcome: partial (revision 1; gaps closed by orchestrator repository verification; the user explicitly accepted it on 2026-11-26)
  openspec_ref: openspec/changes/freeze-backend/research.md
  engram_ref: sdd/freeze-backend/research
product_decisions:
  - id: D1
    topic: Scope of the freeze
    value: keep backend/ byte-identical; remove every CI job, workflow step, Dependabot entry and check that builds, tests, scans, publishes, promotes or updates it; mark it frozen in docs and living specs; one revertible PR
    status: confirmed
  - id: D2
    topic: Dockerfile checks in scripts/check-secrets-contract.sh
    value: remove them, together with their self-test fixtures
    status: confirmed
  - id: D3
    topic: scripts/test-image-config.sh
    value: retire it, together with its references in README.md and openspec/config.yaml
    status: confirmed
  - id: D4
    topic: Issue #84 (backend image smoke test in CI)
    value: stays open and deferred; a comment linking #91 was posted on 2026-11-26
    status: confirmed
  - id: D5
    topic: Open /backend Dependabot PRs (#66, #67, #69)
    value: close them by hand with a comment linking #91 once the change is merged
    status: confirmed
  - id: D6
    topic: Dependabot security updates for backend manifests (enabled in the repository)
    value: keep them enabled and add no ignore entry; close any security-update PR for backend manifests by hand with a comment linking #91 (decided 2026-11-26, after the proposal)
    status: confirmed
  - id: D7
    topic: Delivery size
    value: one PR with size:exception, approved by the owner on 2026-11-26 (about 900-1000 changed lines including specs, mostly deletions)
    status: confirmed
proposal_ready: true
```
