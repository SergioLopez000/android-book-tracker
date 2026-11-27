# Delta for E2E Testing

## ADDED Requirements

### Requirement: Test-Only In-Memory Wiring

The e2e build type (built with `assembleE2e`) MUST replace authentication and book storage with in-memory implementations through test-only wiring, so no Firebase network call is made. The debug and release build types MUST NOT contain this wiring, and the wiring MUST start the app in a signed-in state as a synthetic user.

#### Scenario: Offline signed-in start
- GIVEN the e2e build type on an emulator without network
- WHEN the app starts
- THEN the library screen is shown without the sign-in screen

#### Scenario: No Firebase traffic
- GIVEN the e2e build type
- WHEN the happy path runs
- THEN no request to a Firebase or Google endpoint is made

#### Scenario: Release and debug exclude wiring
- GIVEN a release build and a debug build
- WHEN their bytecode and sources are searched for the in-memory wiring
- THEN no reference is found in either

## MODIFIED Requirements

### Requirement: Test Tag Naming

Interactive and asserted elements of the book list screen, the book edit screen, the delete confirmation dialog and the `StarRating` targets MUST carry a Compose test tag following `<screen>_<element>[_<id>]`. The `_<id>` suffix MUST be present only for elements repeated per item, and the id MUST be the book's String id.
(Previously: ids were numeric.)

#### Scenario: List screen tagged
- GIVEN the e2e build type showing the book list with one book
- WHEN the semantics tree is inspected
- THEN the list container, the add action, the empty state and the book item each expose a tag matching `<screen>_<element>[_<id>]`

#### Scenario: Edit screen tagged
- GIVEN the e2e build type showing the edit screen
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

#### Scenario: String id in tag
- GIVEN a book with a UUID string id
- WHEN its list item tag is read
- THEN the suffix equals that id

### Requirement: Isolation and Offline Execution

The app data MUST be reset before each test, including the in-memory store. The suite MUST NOT require or use network access. The APK under test (the e2e build type APK by default) MUST be overridable through an environment variable.
(Previously: reset referred to app data only.)

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

### Requirement: Happy-Path Flow

The suite MUST include one happy-path test that, starting signed in through the in-memory wiring, adds a book by manual entry, sees it in the list, edits it, deletes it through the confirmation dialog and ends on the empty state.
(Previously: started from a freshly reset app without an authentication precondition.)

#### Scenario: Full flow passes
- GIVEN a freshly reset app, signed in as the synthetic user, with an empty library
- WHEN the test adds a book, edits its data, then confirms deletion
- THEN the edited values were shown in the list before deletion and the empty state is displayed afterwards

#### Scenario: Cancel keeps the book
- GIVEN the delete dialog is open for a book
- WHEN the cancel action is used
- THEN the book remains in the list
