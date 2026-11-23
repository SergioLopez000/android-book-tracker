# E2E Testing Specification

## Purpose

End-to-end coverage of the real Android app, driven through Appium on an emulator. Defines test tags, debug-only resource-id exposure, Page Objects, the happy-path flow, data isolation, evidence policy and dependency pinning. CI wiring (job, artifacts, permissions) lives in `ci-pipeline`.

## Requirements

### Requirement: Test Tag Naming
Interactive and asserted elements of the book list screen, the book edit screen, the delete confirmation dialog and the `StarRating` targets MUST carry a Compose test tag following `<screen>_<element>[_<id>]`. The `_<id>` suffix MUST be present only for elements repeated per item.

#### Scenario: List screen tagged
- GIVEN a debug build showing the book list with one book
- WHEN the semantics tree is inspected
- THEN the list container, the add action, the empty state and the book item each expose a tag matching `<screen>_<element>[_<id>]`

#### Scenario: Edit screen tagged
- GIVEN a debug build showing the edit screen
- WHEN the semantics tree is inspected
- THEN every input, the rating targets and the save action expose a tag matching the pattern

#### Scenario: Delete dialog tagged
- GIVEN the delete confirmation dialog is open
- WHEN the semantics tree is inspected
- THEN its confirm and cancel actions expose tags matching the pattern

#### Scenario: Per-item tags are unique
- GIVEN the list shows two books
- WHEN item tags are read
- THEN each repeated element has a distinct `_<id>` suffix

### Requirement: Debug-Only Resource-Id Exposure
Test tags MUST be exposed as Android resource ids (`testTagsAsResourceId`) only in debug builds, at the activity root and inside the delete dialog content. Release builds MUST NOT reference `testTagsAsResourceId` in source or in compiled bytecode.

#### Scenario: Debug exposes tags as resource ids
- GIVEN a debug APK on a device
- WHEN the UI hierarchy is dumped on the list screen and with the delete dialog open
- THEN every tag from the naming requirement appears as a resource id, including the dialog actions

#### Scenario: Release exposes nothing
- GIVEN a release build
- WHEN its bytecode and sources are searched for `testTagsAsResourceId`
- THEN no reference is found

#### Scenario: Release behavior unchanged
- GIVEN a release build
- WHEN the root modifier is applied
- THEN it leaves the element unchanged

### Requirement: Page Objects
The suite MUST access screen elements only through Page Objects for the list screen and the edit screen (the dialog belongs to the list Page Object). Test files MUST NOT contain raw selectors.

#### Scenario: Selectors encapsulated
- GIVEN the e2e test sources
- WHEN they are searched for selector strings
- THEN selectors appear only inside Page Object files

#### Scenario: Both screens covered
- GIVEN the Page Object folder
- WHEN its contents are listed
- THEN a list Page Object and an edit Page Object exist

### Requirement: Happy-Path Flow
The suite MUST include one happy-path test that adds a book by manual entry, sees it in the list, edits it, deletes it through the confirmation dialog and ends on the empty state.

#### Scenario: Full flow passes
- GIVEN a freshly reset app with an empty library
- WHEN the test adds a book, edits its data, then confirms deletion
- THEN the edited values were shown in the list before deletion and the empty state is displayed afterwards

#### Scenario: Cancel keeps the book
- GIVEN the delete dialog is open for a book
- WHEN the cancel action is used
- THEN the book remains in the list

### Requirement: Isolation and Offline Execution
The app data MUST be reset before each test. The suite MUST NOT require or use network access. The APK under test MUST be overridable through an environment variable.

#### Scenario: Data reset between tests
- GIVEN a prior test left a book in the library
- WHEN the next test starts
- THEN the library is empty

#### Scenario: No network needed
- GIVEN an emulator without network connectivity
- WHEN the happy path runs
- THEN it passes

#### Scenario: APK override
- GIVEN the APK path environment variable is set
- WHEN the suite starts
- THEN that APK is the one installed

### Requirement: Failure Evidence Policy
A screenshot MUST be attached to the Allure results only when a test fails. Passing tests MUST NOT attach screenshots.

#### Scenario: Failure attaches a screenshot
- GIVEN a test forced to fail
- WHEN results are written
- THEN the failed test has a screenshot attachment

#### Scenario: Pass attaches none
- GIVEN a passing test
- WHEN results are written
- THEN it has no screenshot attachment

### Requirement: Synthetic Data Only
Tests, screenshots, page-source dumps and logs MUST contain only synthetic book data and no real personal data or secrets.

#### Scenario: Fixture data synthetic
- GIVEN the e2e test data and generated evidence
- WHEN they are reviewed
- THEN titles, authors and notes are obviously fictional and no credential or personal identifier appears

### Requirement: Pinned Dependencies and Runtime
The `/e2e` project MUST pin exact versions (no ranges) of Appium 3.9.1, appium-uiautomator2-driver 8.7.3 and WebdriverIO 10.2.0 (including its `@wdio/cli`, `@wdio/appium-service` and `@wdio/allure-reporter` packages). It MUST commit `package-lock.json` and be installed with `npm ci`. The Node version MUST be pinned in `.nvmrc` and satisfy the engines of the pinned packages.

#### Scenario: No version ranges
- GIVEN `e2e/package.json`
- WHEN its dependency versions are read
- THEN each is an exact version with no `^`, `~` or range

#### Scenario: Pinned versions match
- GIVEN `e2e/package.json`
- WHEN Appium, the driver and WebdriverIO are read
- THEN they are 3.9.1, 8.7.3 and 10.2.0

#### Scenario: Reproducible install
- GIVEN a clean checkout with the committed lockfile
- WHEN `npm ci` runs with the Node version from `.nvmrc`
- THEN it exits 0 without modifying the lockfile

#### Scenario: Lockfile drift fails
- GIVEN `package.json` and `package-lock.json` disagree
- WHEN `npm ci` runs
- THEN it exits non-zero
