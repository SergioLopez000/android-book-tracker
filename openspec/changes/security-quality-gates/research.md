# Research: security-quality-gates

Status: done (web lanes complete, 2026-11-17). Remaining unknowns are empirical and become spike tasks. Tags: V = verified in a source, I = inferred.

## Lane 1: CodeQL as a merge gate (ruleset "Require code scanning results")

- **When it blocks** (V, [rulesets](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/available-rules-for-rulesets) and [merge protection](https://docs.github.com/en/code-security/concepts/code-scanning/merge-protection)). The rule blocks when:
  - an alert at or above the threshold is found, or
  - the analysis is still in progress, or
  - the tool is not configured.
- **Thresholds** (V). They are set per tool:
  - "Alerts": None / Errors / All
  - "Security alerts": a cutoff, e.g. "Medium or higher"
- **Base-branch comparison: UNDOCUMENTED.**
  - The only wording found: "results ... for the commit and the reference the pull request targets" ([trial tutorial](https://docs.github.com/en/code-security/tutorials/trialing-github-advanced-security/explore-trial-code-scanning)).
  - It may require an analysis on `dev`.
- **Missing analysis blocks the PR** (V, docs).
  - Public issues report PRs stuck on "waiting for results" when the workflow did not run: [nova-imperium#70](https://github.com/Heiss/nova-imperium/pull/70), [osrs-tracker-packages#11](https://github.com/osrs-tracker/osrs-tracker-packages/issues/11).
  - So the CodeQL workflow must run on EVERY PR to `dev`, with no path filters (I).
- **Dependabot PRs** (V, [resource-not-accessible](https://docs.github.com/en/code-security/reference/code-scanning/troubleshoot-analysis-errors/resource-not-accessible)).
  - Quote: "code scanning always allows uploads when the workflow is triggered by `pull_request`".
  - The 403 only happens on Dependabot `push` runs.
  - The ruleset exemption covers only default setup, so with advanced setup Dependabot PRs need a real run.
- **Non-default branches** (V). The docs recommend merge protection "on long-lived feature branches". The about-alerts page says PR checks are "by default, limited to pull requests that target the default branch". Whether that applies to advanced setup is unknown.
- **Availability on this repo** (V, repo API 2026-11-17).
  - `GET /code-scanning/default-setup` returns `state: not-configured`, `languages: [actions, java-kotlin, python]`.
  - So code scanning is available on this public personal repo.
  - A license is only required for private repos ([about code scanning](https://docs.github.com/en/code-security/code-scanning/introduction-to-code-scanning/about-code-scanning)).

## Lane 2: CodeQL Kotlin manual build

- Kotlin needs a build. Manual mode analyzes only what the build steps compile (V, [compiled languages](https://docs.github.com/en/code-security/how-tos/find-and-fix-code-vulnerabilities/manage-your-configuration/codeql-for-compiled-languages)).
- Kotlin 1.8.0-2.4.20 is supported (V, [supported languages](https://codeql.github.com/docs/codeql-overview/supported-languages-and-frameworks/)).
- `github/codeql-action@v4`; latest seen v4.39.3.
- There is no official guidance on the Gradle daemon, build cache or Kotlin compile daemon.
  - Precedents:
    - testng moved to a manual build with `--no-daemon` after a kotlinc crash under the tracer ([testng#3328](https://github.com/testng-team/testng/pull/3328)).
    - ktlint uses `--no-build-cache`.
  - Candidate command (I): `./gradlew --no-daemon --no-build-cache -Pkotlin.compiler.execution.strategy=in-process :app:compileDebugKotlin`
  - The strategy values are verified in the [Kotlin docs](https://kotlinlang.org/docs/compiler-execution-strategy.html).
- Run time: not found.

## Lane 3: detekt 1.23.8 CLI

- 1.23.8 is built on Kotlin 2.0.21 (V, [compatibility](https://detekt.dev/docs/introduction/compatibility)).
- The brew formula is 1.23.8 and depends on `openjdk@21` (V, [formula](https://formulae.brew.sh/formula/detekt)).
- Parsing of syntax newer than 2.0.21 (context parameters, guard conditions) is unproven (I).
- `--input` takes multiple paths separated by `:` on Unix (V, [CLI docs](https://detekt.dev/docs/gettingstarted/cli)). That page may be the 2.x docs, so confirm with `detekt --help`.
- Baseline (V, [1.23.8 baseline](https://detekt.dev/docs/1.23.8/introduction/baseline)):
  - XML `SmellBaseline` with `ManuallySuppressedIssues` and `CurrentIssues`.
  - Create it with `--baseline <file> --create-baseline`.
- Compose config (V, [1.23.8 compose](https://detekt.dev/docs/1.23.8/introduction/compose)):
  - FunctionNaming `ignoreAnnotated: ['Composable']`
  - TopLevelPropertyNaming `constantPattern`
  - LongParameterList `functionThreshold` + `ignoreDefaultParameters`
  - MagicNumber `ignorePropertyDeclaration`
  - UnusedPrivateMember / TooManyFunctions ignore `Preview`

## Lane 4: Dependabot

- `target-branch` does NOT apply to security updates: "security updates always use the default branch" (V, [options reference](https://docs.github.com/en/code-security/dependabot/working-with-dependabot/dependabot-options-reference)).
- On this repo, Dependabot security updates are `disabled` and vulnerability alerts are off (repo API, 2026-11-17).
- `gradle/libs.versions.toml` is supported (V, [supported ecosystems](https://docs.github.com/en/code-security/dependabot/ecosystems-supported-by-dependabot/supported-ecosystems-and-repositories)). `[plugins]` entries are unconfirmed.
- `commit-message`:
  - `prefix` applies to commit messages and PR titles.
  - `include: scope` appends `(deps)` (V).
- Group keys: `applies-to`, `dependency-type`, `exclude-patterns`, `group-by`, `patterns`, `update-types` (V).
- Dependabot-triggered workflows get a read-only token, which `permissions` can raise (V, [troubleshooting](https://docs.github.com/en/code-security/dependabot/troubleshooting-dependabot/troubleshooting-dependabot-on-github-actions)).
- Config is read from the default branch only (I, general knowledge, unsourced).

## Lane 5: gitleaks v8.30.1

- The official checksums file lists `551f6fc83ea457d62a0d98237cbad105af8d557003051f41f3e7ca7b3f2470eb  gitleaks_8.30.1_linux_x64.tar.gz` (V, [checksums](https://github.com/gitleaks/gitleaks/releases/download/v8.30.1/gitleaks_8.30.1_checksums.txt)).
- `gitleaks git --log-opts="<a>..<b>"` passes options to `git log -p` (V, [README v8.30.1](https://raw.githubusercontent.com/gitleaks/gitleaks/v8.30.1/README.md)). This needs `fetch-depth: 0` (I).
- `--redact` with no value is full redaction in logs and stdout (V). Report-file redaction is unconfirmed.

## Conflicts with user decisions

1. **Possible conflict (unproven).** "CodeQL not on push to `dev`" vs "CodeQL blocks via the ruleset". The rule may need an analysis on the target branch.
2. **Dependabot security updates target `main`**, not `dev`. They are currently disabled on the repo.
3. **Every PR to `dev` must run CodeQL**, docs-only ones included, or it stays blocked.

## Spike items (for tasks)

1. Ruleset on `dev`:
   - Does it block when `dev` has no analysis?
   - Does it block on a skipped job or failed upload?
   - Do existing alerts count as new?
2. Does advanced-setup PR analysis work for PRs targeting `dev`, which is non-default?
3. Does a Dependabot `pull_request` CodeQL run satisfy the rule?
4. CodeQL Kotlin manual build: does extraction work with `--no-daemon` / `--no-build-cache` / in-process? Measure the run time.
5. detekt 1.23.8:
   - Does it parse the repo?
   - `--input` separator and `ignoreAnnotated` per `detekt --help`.
   - How large is the baseline?
6. Dependabot:
   - Are `[plugins]` entries updated?
   - Is the config read only from `main`?
7. Does gitleaks `--redact` also apply to report files?
