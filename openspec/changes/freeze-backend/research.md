```
schema: gentle-ai.sdd-research/v1
revision: 1
change: freeze-backend (GitHub #91)
accessed_at: 2026-11-26
outcome: partial
```

## Questions
- Q1. GitHub Actions `needs`: how it treats a skipped dependency or one that no longer exists, and what values `needs.<job>.result` can take.
- Q2. Required status checks that stop being reported.
- Q3. Open Dependabot PRs when an `updates` entry is removed.
- Q4. Retention and cost of existing GHCR images once publishing stops.
- Q5. CodeQL `java-kotlin` when a separate Gradle build is no longer compiled.

## Admission
Public GitHub documentation only (docs.github.com).

## Sources
| ID | Class | Title | Publisher | URL | Excerpt |
|---|---|---|---|---|---|
| S1 | documentation | Workflow syntax for GitHub Actions (`needs`) | GitHub Docs | https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax | "If a job fails or is skipped, all jobs that need it are skipped" unless a conditional lets them continue |
| S2 | documentation | Using jobs in a workflow | GitHub Docs | https://docs.github.com/en/actions/using-jobs/using-jobs-in-a-workflow | "a failure or skip applies to all jobs in the dependency chain from the point of failure or skip onwards" |
| S3 | documentation | Contexts reference | GitHub Docs | https://docs.github.com/en/actions/reference/workflows-and-actions/contexts | "needs.<job_id>.result ... Possible values are success, failure, cancelled, or skipped" |
| S4 | documentation | Troubleshooting required status checks | GitHub Docs | https://docs.github.com/en/pull-requests/collaborating-with-pull-requests/collaborating-on-repositories-with-code-quality-features/troubleshooting-required-status-checks | "Waiting for status to be reported" when a required workflow does not trigger; "The job reports 'Success'" when skipped by a conditional |
| S5 | documentation | Configuring Dependabot version updates | GitHub Docs | https://docs.github.com/en/code-security/dependabot/dependabot-version-updates/configuring-dependabot-version-updates | Disable updates by deleting `dependabot.yml`, or per ecosystem with `open-pull-requests-limit: 0` or by removing the block |
| S6 | documentation | GitHub Packages billing | GitHub Docs | https://docs.github.com/en/billing/concepts/product-billing/github-packages | "GitHub Packages usage is free for public packages"; "Container image storage and bandwidth for the Container registry is currently free" |
| S7 | documentation | Deleting and restoring a package | GitHub Docs | https://docs.github.com/en/packages/learn-github-packages/deleting-and-restoring-a-package | Deleting a package needs admin permission; it can be restored within 30 days |
| S8 | documentation | CodeQL code scanning for compiled languages | GitHub Docs | https://docs.github.com/en/code-security/code-scanning/creating-an-advanced-setup-for-code-scanning/codeql-code-scanning-for-compiled-languages | With `manual`, "CodeQL will analyze whatever source code is built by your specified build steps" |

## Validated claims
- C1 [S1, S2]. A job whose needed job is skipped or fails is itself skipped, unless its `if` lets it run (for example `always()`).
- C2 [S3]. `needs.<job>.result` is one of success, failure, cancelled or skipped. A gate that requires `success` from every needed job therefore fails when one of them is skipped.
- C3 [S4]. A required check that is never reported blocks the PR with "Waiting for status to be reported". A job skipped by its own conditional reports "Success".
- C4 [S5]. The documented ways to stop Dependabot updates are deleting the file, setting `open-pull-requests-limit: 0`, or removing the ecosystem block. The documentation does not say that open PRs are closed.
- C5 [S6, S7]. Public packages are free, and Container registry storage and bandwidth are currently free. Deleting a package needs admin permission and can be undone within 30 days.
- C6 [S8]. With `build-mode: manual`, CodeQL analyzes only the code compiled by the workflow's build steps.

## Contradictions, uncertainty and freshness
- Not validated from the docs:
  - Whether a `needs` entry pointing to a non-existent job invalidates the workflow.
  - Whether Dependabot closes its open PRs when an entry is removed.
  - What happens to existing GHCR images after publishing stops.
  - CodeQL behaviour when a separate build is not compiled.
- The Contexts and workflow-syntax pages were read only in part.
- All pages were live on 2026-11-26.

## Orchestrator repository verification (not web evidence)
These checks were run against the repository and local tooling on 2026-11-26. They close the practical gaps above.
- **Missing `needs` target.** `actionlint` rejects it: on a probe workflow it reports `job "gate" needs job "ghost" which does not exist in this workflow [job-needs]`. lefthook runs actionlint on every workflow change.
- **Required checks.** The `dev` ruleset requires only `ci-gate`, and classic branch protection is not configured. Removing the backend job names leaves no required check unreported.
- **CodeQL.** `.github/workflows/codeql.yml` uses `languages: java-kotlin` with `build-mode: manual`. Removing the backend build step leaves the app as the only analysed code, and the matrix stays valid.
- **Dependabot.** There are three open PRs for `/backend`:
  - #66 (distroless base image)
  - #67 (Spring Boot 4.2.0)
  - #69 (Gradle wrapper in `/backend`)

## Product choices (non-authoritative)
- Close the three open `/backend` Dependabot PRs by hand, or leave them open.
- Keep the existing GHCR images (free for this public repository) or delete them.
