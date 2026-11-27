# Account Deletion Specification

## Purpose

In-app deletion of the user's authentication account and all personal data.

## Requirements

### Requirement: Deletion Entry Point With Confirmation

The system MUST offer account deletion in the app to a signed-in user and MUST require explicit confirmation naming the irreversible loss of data.

#### Scenario: Confirm
- GIVEN a signed-in user
- WHEN the user starts deletion and confirms
- THEN deletion proceeds

#### Scenario: Cancel
- GIVEN the confirmation is open
- WHEN the user cancels
- THEN nothing is deleted and the user stays signed in

### Requirement: Recent Authentication Before Any Deletion

When the provider requires recent authentication, the system MUST ask the user to re-authenticate BEFORE deleting any data, and MUST then continue deletion. No data MUST be deleted until re-authentication has succeeded.

#### Scenario: Reauthentication needed
- GIVEN the provider requires recent login
- WHEN the user re-authenticates successfully
- THEN deletion proceeds and completes

#### Scenario: Reauthentication declined
- GIVEN re-authentication is requested
- WHEN the user cancels it
- THEN no data was deleted: books, memberships, lists, invites and the account are all intact

### Requirement: Data and Account Removed

On confirmed deletion the system MUST delete all of the user's personal books and user data, then the authentication account; afterwards no data of that user MUST remain readable.

#### Scenario: Full deletion
- GIVEN a user with personal books
- WHEN deletion completes
- THEN the books and the auth user no longer exist and signing in with the old credentials fails

#### Scenario: Return to sign-in
- GIVEN deletion completed
- WHEN the UI updates
- THEN the local cache is cleared and the sign-in screen is shown

### Requirement: Shared List Handling on Deletion

On deletion the system MUST remove the user from every list they belong to and MUST delete every invite code the user created. A list whose sole member is the deleted user MUST be deleted with its copies, invite codes and member entries. In every other list, the lists and all copies, including copies the user added, MUST remain available to the remaining members, and those copies MUST hold no data identifying the deleted user.

#### Scenario: Member of a list
- GIVEN the user and B are members of a list
- WHEN the user's account is deleted
- THEN B still sees the list and its books and the user is no longer a member

#### Scenario: Sole-member list deleted
- GIVEN the user is the only member of a list
- WHEN the user's account is deleted
- THEN the list, its copies, invite codes and member entries no longer exist

#### Scenario: Copies added by the user remain
- GIVEN the user added a copy to a list that B also belongs to
- WHEN the user's account is deleted
- THEN B still sees that copy and it contains no identifying data of the deleted user

#### Scenario: User's invites deleted
- GIVEN the user created invite codes for lists that remain
- WHEN the user's account is deleted
- THEN those invite codes no longer exist

### Requirement: Failure Handling

If deletion fails (including being offline), the system MUST tell the user, MUST allow retry, and MUST NOT leave the user signed in to an account whose data was partly removed without notice. Deletion is online only and MUST be safe to retry after an interruption.

#### Scenario: Offline
- GIVEN the device is offline
- WHEN the user confirms deletion
- THEN a message states deletion needs connectivity and the account remains

#### Scenario: Retry after partial failure
- GIVEN data deletion succeeded but account deletion failed
- WHEN the user retries
- THEN deletion completes

## Open Questions

- None open: sole-member list deletion and retention of the user's copies in other lists are settled above.
