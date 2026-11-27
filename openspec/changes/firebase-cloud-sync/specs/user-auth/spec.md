# User Auth Specification

## Purpose

Required sign-in (Google and email/password) gating all app data; sign-out clears the local cache.

## Requirements

### Requirement: Sign-In Required

The system MUST show only the sign-in screen to an unauthenticated user and MUST NOT offer anonymous or guest access. Library, book entry and list screens MUST be unreachable until sign-in succeeds.

#### Scenario: Cold start unauthenticated
- GIVEN no user is signed in
- WHEN the app starts
- THEN the sign-in screen is shown and no library data is displayed

#### Scenario: No anonymous option
- GIVEN the sign-in screen
- WHEN its actions are listed
- THEN only Google sign-in, email/password sign-in, email/password account creation and "forgot password" are offered

### Requirement: Google Sign-In

The system MUST allow sign-in with a Google account through the Android Credential Manager.

#### Scenario: Google sign-in succeeds
- GIVEN the sign-in screen
- WHEN the user completes Google sign-in
- THEN the user is authenticated and the library screen is shown

#### Scenario: Google sign-in cancelled
- GIVEN the Google account chooser is open
- WHEN the user dismisses it
- THEN the sign-in screen remains with no crash and no error dialog

#### Scenario: Google sign-in fails
- GIVEN the provider returns an error or the device is offline
- WHEN sign-in is attempted
- THEN a message is shown and the user stays unauthenticated

### Requirement: Email and Password Sign-In

The system MUST allow creating an account and signing in with email and password, and MUST validate the email format before submitting. Email verification is out of scope: a signed-up user MUST NOT be required to verify the address to use the app.

#### Scenario: Create account
- GIVEN a valid unused email and an acceptable password
- WHEN the user submits account creation
- THEN the account is created, the user is signed in and the library is shown

#### Scenario: Sign in
- GIVEN an existing email/password account
- WHEN the user submits correct credentials
- THEN the user is signed in

#### Scenario: Wrong credentials
- GIVEN an existing account
- WHEN the user submits a wrong password
- THEN an error message is shown and the user stays on the sign-in screen

#### Scenario: Malformed email
- GIVEN the email field contains "not-an-email"
- WHEN the user submits
- THEN no request is sent and a validation message is shown

#### Scenario: Email already registered
- GIVEN an account exists for the email
- WHEN the user attempts to create another with it
- THEN an error message is shown and no second account is created

### Requirement: Password Reset

The sign-in screen MUST offer "forgot password" for email/password accounts, which sends a password reset email to the entered address, after validating the email format.

#### Scenario: Reset requested
- GIVEN the sign-in screen with a well-formed email entered
- WHEN the user chooses "forgot password"
- THEN a password reset email request is sent and a confirmation message is shown

#### Scenario: Malformed email on reset
- GIVEN the email field contains "not-an-email"
- WHEN the user chooses "forgot password"
- THEN no request is sent and a validation message is shown

#### Scenario: Reset fails offline
- GIVEN the device is offline
- WHEN the user chooses "forgot password"
- THEN a message is shown and the user stays on the sign-in screen

#### Scenario: No verification gate
- GIVEN a newly created email/password account whose address was never verified
- WHEN the user signs in
- THEN the library is shown

### Requirement: Session Persistence

The system MUST keep the user signed in across app restarts, including when offline.

#### Scenario: Restart while signed in
- GIVEN a signed-in user
- WHEN the app is killed and restarted, with or without connectivity
- THEN the library screen is shown without a sign-in prompt

### Requirement: Sign-Out Clears Local Cache

The system MUST offer sign-out and, on sign-out, MUST clear the local Firestore persistence and return to the sign-in screen, so no data of the previous user is readable by the next user on the device.

#### Scenario: Sign-out
- GIVEN a signed-in user with cached books
- WHEN the user signs out
- THEN the sign-in screen is shown and the local cache holds no books

#### Scenario: Different user signs in next
- GIVEN user A signed out on a device
- WHEN user B signs in offline-first on the same device
- THEN no book of user A is displayed

#### Scenario: Unsynced writes trigger a warning
- GIVEN a signed-in user with writes not yet acknowledged by the server
- WHEN the user chooses sign-out
- THEN a warning stating that unsynced changes will be lost is shown and the user MUST confirm before sign-out proceeds

#### Scenario: Confirm loses unsynced writes
- GIVEN the unsynced-writes warning is shown
- WHEN the user confirms
- THEN the user is signed out, the cache is cleared and those unsynced writes are lost

#### Scenario: Cancel keeps session
- GIVEN the unsynced-writes warning is shown
- WHEN the user cancels
- THEN the user stays signed in and the writes remain pending

#### Scenario: No warning when everything is synced
- GIVEN a signed-in user with no unsynced writes
- WHEN the user signs out
- THEN no warning is shown and sign-out proceeds

### Requirement: Observable Auth State

The domain MUST expose the current auth state (signed-out, signed-in with user id) through a port that is substitutable by a fake, so screens and use cases are testable on the JVM.

#### Scenario: State change drives navigation
- GIVEN a fake auth port
- WHEN it emits signed-out after signed-in
- THEN navigation moves to the sign-in screen

## Out of Scope

- Email verification.

## Open Questions

- How the app determines "unsynced writes exist" within a bounded wait (the design uses a 3-second pending-writes wait) is a design concern; the spec only requires that a warning appears when writes are not confirmed.
