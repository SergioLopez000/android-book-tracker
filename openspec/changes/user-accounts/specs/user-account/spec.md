# User Account Specification

## Purpose

Google account identity in the Android app: required sign-in at app start through Credential Manager, an in-memory session holding the Google ID token, non-sensitive account metadata, sign-out, and a debug-only fake sign-in gateway for offline end-to-end tests. Backend token validation lives in `backend-api`. App-side download of books on a new install and encrypted token persistence are out of scope (#10).

## ADDED Requirements

### Requirement: Required Sign-In at Start

The app MUST require a signed-in session before showing any book screen. When no session exists at app start, the app MUST show only the sign-in screen. Book screens (list, edit, delete dialog) MUST NOT be reachable until sign-in succeeds.

#### Scenario: First start shows sign-in
- GIVEN the app starts with no session
- WHEN the first screen is rendered
- THEN the sign-in screen is shown and no book list, add action or edit screen is reachable

#### Scenario: Successful sign-in opens the library
- GIVEN the sign-in screen is shown and the gateway returns a valid Google ID token
- WHEN the user completes sign-in
- THEN the session becomes signed in and the book list is shown

#### Scenario: Process restart requires sign-in again
- GIVEN a signed-in session and the app process is killed
- WHEN the app starts again
- THEN no token is available and the sign-in screen is shown

### Requirement: Credential Manager Google Sign-In

The production sign-in adapter MUST obtain the Google ID token through Android Credential Manager with the Google ID option (credentials 1.6.0, googleid 1.2.1), using the Web client ID as the server client ID. A user cancellation, a missing credential or any gateway error MUST leave the session signed out and MUST show a sign-in error state that allows retry, without exposing the token, exception class names or stack traces.

#### Scenario: Web client ID requested
- GIVEN the production adapter and a configured Web client ID
- WHEN sign-in is requested
- THEN the Credential Manager request uses that Web client ID as the server client ID

#### Scenario: User cancels
- GIVEN the account picker is shown
- WHEN the user cancels
- THEN the session stays signed out and the sign-in screen offers retry

#### Scenario: No credential available
- GIVEN the device has no usable Google credential
- WHEN sign-in is requested
- THEN the session stays signed out and an error state with retry is shown

#### Scenario: Gateway failure is safe
- GIVEN the gateway throws an unexpected exception
- WHEN sign-in is requested
- THEN the session stays signed out and the displayed message contains no exception class name, stack trace or token

### Requirement: In-Memory Token Only

The Google ID token MUST be held only in process memory by the `SessionStore` implementation. It MUST NOT be written to DataStore, SharedPreferences, files, logs, the database, backups or saved instance state.

#### Scenario: Token absent from persistent storage
- GIVEN a completed sign-in
- WHEN every persistent store of the app (DataStore, preferences, files, database) is inspected
- THEN the ID token value appears in none of them

#### Scenario: Token not logged
- GIVEN a completed sign-in with logging enabled
- WHEN the log output is inspected
- THEN the token value does not appear

#### Scenario: Token read through the session
- GIVEN a signed-in session
- WHEN the data layer needs a bearer token
- THEN it reads it from the in-memory session, and it is absent once the session is cleared

### Requirement: Non-Sensitive Account Metadata

After sign-in the app MUST persist only non-sensitive account metadata (display name and email or an equivalent subset chosen at design time, never the token) in DataStore, and MUST expose the session as an observable stream (`ObserveSession`) containing the signed-in state and that metadata. Persisted metadata MUST NOT by itself make the app signed in: a restored metadata record without an in-memory token MUST be treated as signed out.

#### Scenario: Metadata persisted
- GIVEN a successful sign-in for an account with a display name and email
- WHEN the DataStore content is read
- THEN it contains that metadata and no token

#### Scenario: Metadata alone is not a session
- GIVEN DataStore holds metadata from an earlier run and memory holds no token
- WHEN the app observes the session
- THEN the state is signed out

#### Scenario: Session observed
- GIVEN a collector of `ObserveSession`
- WHEN sign-in succeeds and later sign-out completes
- THEN the collector receives signed-out, signed-in with metadata, then signed-out

### Requirement: Sign-Out Clears Everything

`SignOut` MUST clear the in-memory token and all persisted account metadata, and the app MUST return to the sign-in screen. After sign-out no account data MUST remain recoverable from the app's storage.

#### Scenario: Sign-out clears memory and storage
- GIVEN a signed-in session with persisted metadata
- WHEN the user signs out
- THEN the in-memory token is gone, the DataStore account metadata is empty and the sign-in screen is shown

#### Scenario: Sign-out is idempotent
- GIVEN a signed-out session
- WHEN sign-out is invoked again
- THEN it completes without error and the session stays signed out

### Requirement: Ports and Use Cases Without Android Types

Session logic MUST live behind ports and use cases with no Android dependency in `domain`: ports `AuthGateway` and `SessionStore`, use cases `SignIn`, `SignOut` and `ObserveSession`. Credential Manager and the fake gateway MUST be adapters in `data/auth/`. The UI MUST live in `ui/account/`.

#### Scenario: Domain is Android-free
- GIVEN the `domain` package
- WHEN its imports are inspected
- THEN none reference `android.*`, `androidx.*` or Credential Manager classes

#### Scenario: SignIn stores the session
- GIVEN a fake `AuthGateway` returning a token and account metadata
- WHEN `SignIn` runs
- THEN the `SessionStore` holds that token and metadata

#### Scenario: SignIn failure stores nothing
- GIVEN a fake `AuthGateway` that fails
- WHEN `SignIn` runs
- THEN the failure is reported and the `SessionStore` is unchanged

### Requirement: Debug-Only Fake Sign-In Gateway

A fake `AuthGateway` MUST exist only in the debug source set so that the offline e2e suite can sign in without Google. A debug build MUST select the fake gateway when the OAuth Web client ID is blank (the CI and fresh-clone default) and the Credential Manager gateway when it is set. Release builds MUST always select the Credential Manager gateway, MUST NOT contain the fake gateway class in sources or compiled bytecode, and MUST NOT offer any path that bypasses real sign-in.

#### Scenario: Debug without a client ID uses the fake gateway
- GIVEN a debug build with a blank Web client ID
- WHEN sign-in is requested
- THEN the fake gateway returns a synthetic token and account without any network or Google call

#### Scenario: Debug with a client ID uses real sign-in
- GIVEN a debug build with a non-blank Web client ID
- WHEN the sign-in gateway is selected
- THEN the Credential Manager gateway is used

#### Scenario: Release always uses real sign-in
- GIVEN a release build, with or without a Web client ID
- WHEN the sign-in gateway is selected
- THEN the Credential Manager gateway is used

#### Scenario: Release has no fake gateway
- GIVEN a release build
- WHEN its sources and bytecode are searched for the fake gateway
- THEN no reference is found

#### Scenario: Release test guards the boundary
- GIVEN the release unit or build check
- WHEN the fake gateway class is added to a non-debug source set
- THEN that check fails

### Requirement: OAuth Client Configuration and Cleartext Scope

OAuth client IDs (Web and Android) are non-secret configuration and MUST default to blank so that builds and CI stay green without Google Cloud setup. A blank Web client ID MUST make real sign-in report a configuration error instead of crashing. Cleartext HTTP MUST be permitted only in debug builds and only for `10.0.2.2`; release builds MUST NOT permit cleartext traffic.

#### Scenario: Blank IDs build
- GIVEN no OAuth client IDs configured
- WHEN the debug and release builds run
- THEN both succeed

#### Scenario: Blank Web client ID at sign-in
- GIVEN a release configuration with a blank Web client ID
- WHEN sign-in is requested
- THEN a configuration error state is shown and the app does not crash

#### Scenario: Debug cleartext scoped to the emulator host
- GIVEN the debug network security configuration
- WHEN it is inspected
- THEN cleartext is permitted only for `10.0.2.2`

#### Scenario: Release forbids cleartext
- GIVEN the release network security configuration
- WHEN it is inspected
- THEN cleartext traffic is not permitted for any domain

### Requirement: Account Data Excluded From Backup

Android backup and device-transfer rules MUST exclude the account metadata store, so that a restored install does not carry another device's account record.

#### Scenario: Backup rules exclude account store
- GIVEN the backup and data-extraction rules
- WHEN they are inspected
- THEN the DataStore file holding account metadata is excluded from cloud backup and device transfer
