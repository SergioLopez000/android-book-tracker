# Delta for e2e-testing

## ADDED Requirements

### Requirement: Sign-In Screen Testability

The sign-in screen's interactive and asserted elements (the sign-in action and the error state) MUST carry Compose test tags following `<screen>_<element>[_<id>]`, and the suite MUST access them only through a sign-in Page Object, in line with "Page Objects". Release builds MUST NOT expose them as resource ids, per "Debug-Only Resource-Id Exposure".

#### Scenario: Sign-in screen tagged
- GIVEN a debug build showing the sign-in screen
- WHEN the semantics tree is inspected
- THEN the sign-in action exposes a tag matching `<screen>_<element>`

#### Scenario: Sign-in selectors encapsulated
- GIVEN the e2e test sources
- WHEN they are searched for sign-in selector strings
- THEN they appear only inside the sign-in Page Object

## MODIFIED Requirements

### Requirement: Happy-Path Flow

The suite MUST include one happy-path test that signs in through the debug fake gateway, adds a book by manual entry, sees it in the list, edits it, deletes it through the confirmation dialog and ends on the empty state.
(Previously: the flow began directly at the book list with no sign-in step.)

#### Scenario: Full flow passes
- GIVEN a freshly reset app with an empty library and no session
- WHEN the test signs in through the fake gateway, adds a book, edits its data, then confirms deletion
- THEN the edited values were shown in the list before deletion and the empty state is displayed afterwards

#### Scenario: Sign-in gates the flow
- GIVEN a freshly reset app with no session
- WHEN the app launches
- THEN the sign-in screen is shown and the book list appears only after signing in

#### Scenario: Cancel keeps the book
- GIVEN the delete dialog is open for a book
- WHEN the cancel action is used
- THEN the book remains in the list

### Requirement: Isolation and Offline Execution

The app data MUST be reset before each test, which includes clearing any session and account metadata so that every test starts signed out. The suite MUST NOT require or use network access, and sign-in MUST go through the debug fake gateway, never through Google. The APK under test MUST be overridable through an environment variable.
(Previously: no statement about session reset or sign-in.)

#### Scenario: Data reset between tests
- GIVEN a prior test left a book in the library and a signed-in session
- WHEN the next test starts
- THEN the library is empty and the app shows the sign-in screen

#### Scenario: No network needed
- GIVEN an emulator without network connectivity
- WHEN the happy path runs, including sign-in
- THEN it passes

#### Scenario: Sign-in never reaches Google
- GIVEN the e2e run
- WHEN sign-in is performed
- THEN it is served by the debug fake gateway and no Google account or credential is used

#### Scenario: APK override
- GIVEN the APK path environment variable is set
- WHEN the suite starts
- THEN that APK is the one installed
