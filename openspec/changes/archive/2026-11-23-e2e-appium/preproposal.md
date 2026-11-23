# Pre-proposal state: e2e-appium

```yaml
schema: gentle-ai.sdd-preproposal/v1
revision: 2
change: e2e-appium
issue: 7
exploration:
  outcome: done
  openspec_ref: openspec/changes/e2e-appium/exploration.md
  engram_ref: sdd/e2e-appium/explore
research:
  selected: true
  request_id: e2e-appium-research-1
  intent_revision: 1
  source_classes: [documentation, open-web]
  admission: granted (documentation, open-web)
  outcome: done (research revision 2; revision 1 was partial and its gaps were closed with npm registry and compiled-artifact evidence)
  openspec_ref: openspec/changes/e2e-appium/research.md
  engram_ref: sdd/e2e-appium/research
product_decisions:
  - id: D1
    topic: Appium client language
    value: TypeScript + WebdriverIO in /e2e, exact-pinned npm dependencies, committed lockfile
    status: confirmed
  - id: D2
    topic: Debug-only testTagsAsResourceId mechanism
    value: src/debug + src/release source-set pair defining Modifier.e2eRoot()
    status: confirmed
  - id: D3
    topic: Appium version line
    value: Appium 3.9.1 + appium-uiautomator2-driver 8.7.3 + WebdriverIO 10.2.0 (documented pairing; revisit Appium 4 when WDIO documents it)
    status: confirmed
  - id: D4
    topic: e2e-tests job gating
    value: non-gating at first (report needs only); make gating in a follow-up delta after a flake burn-in
    status: confirmed
  - id: D5
    topic: e2e-tests trigger scope
    value: every pull request and push to dev, no path or label filters
    status: confirmed
  - id: D6
    topic: package-lock.json against the 800-line review budget
    value: counted as generated (excluded from the budget), reviewed only for consistency with package.json
    status: confirmed
proposal_ready: true
```

## Defaults proposed for the remaining open decisions

These go into the proposal as defaults unless the user overrides them:

- Allure screenshots attached on failure only.
- The `allure-results-e2e` artifact kept for 7 days.
- Emulator API level and image chosen by the design spike.
- Node version pinned in an `.nvmrc` file.
- CodeQL `javascript-typescript` not added in this change.
- `tsc --noEmit` added to lefthook for `e2e/**/*.ts`.
- Local override of the APK path through an environment variable.
