# Shared Book Lists Specification

## Purpose

Named lists of book copies shared by equal members, joined through in-app invite codes.

## Requirements

### Requirement: Create List

The system MUST let a signed-in user create a list with a non-blank name of at most 100 characters, and the creator MUST become its first member with the same rights as any other member. The list and the creator's membership MUST be created together atomically.

#### Scenario: Create
- GIVEN a signed-in user
- WHEN the user creates a list named "Club"
- THEN the list exists and the user is listed as a member

#### Scenario: Blank name
- GIVEN the name is empty or whitespace
- WHEN the user submits
- THEN creation is blocked with a validation message

#### Scenario: Name too long
- GIVEN a name longer than 100 characters
- WHEN the user submits
- THEN creation is blocked with a validation message

### Requirement: Equal Members

Every member, including the creator, MUST be able to add, edit and delete the list's books, generate invite codes, remove any member (including themselves and the creator) and delete the list. No role or ownership distinction SHALL exist.

#### Scenario: Non-creator manages
- GIVEN member B joined a list created by A
- WHEN B edits a book, removes A and generates an invite code
- THEN each action succeeds

#### Scenario: Non-member denied
- GIVEN user C is not a member
- WHEN C opens, edits or deletes the list
- THEN access is denied and the list is not shown to C

### Requirement: Books Are Copies

Adding a book to a list MUST copy it; the copy MUST be independent of the source book in the user's personal library, and the copy's `review` MUST be visible to all members. A copy MUST NOT carry data that identifies the user who added it.

#### Scenario: Copy independence
- GIVEN a personal book was copied into a list
- WHEN the user edits or deletes the personal book
- THEN the list copy is unchanged

#### Scenario: Edit copy only
- GIVEN a member edits the list copy
- WHEN the owner's personal book is read
- THEN it is unchanged

#### Scenario: Review visible
- GIVEN a list copy with a review
- WHEN another member opens it
- THEN the review text is shown

#### Scenario: Copy has no adder identity
- GIVEN a book copied into a list by user A
- WHEN the stored copy is inspected
- THEN it contains no field identifying A

### Requirement: Invite Codes

A member MUST be able to generate an invite code. A code MUST expire 7 days after creation and allow 10 uses by default; a code MUST NOT be creatable with an expiry later than 7 days or more than 50 uses. A user MUST join by entering a valid code in the app. Joining MUST require connectivity, MUST validate the code on the server, and MUST apply the membership and the use count in a single atomic batch. The creator of a code MUST NOT be recorded on it.

#### Scenario: Default code
- GIVEN a member generates a code without choosing limits
- WHEN the code is created
- THEN it expires in 7 days and allows 10 uses

#### Scenario: Limits capped
- GIVEN a member requests a code with 51 uses or an expiry beyond 7 days
- WHEN creation is attempted
- THEN it is rejected

#### Scenario: Join with valid code
- GIVEN an unexpired code with remaining uses
- WHEN a signed-in non-member enters it
- THEN the user becomes a member and one use is consumed

#### Scenario: Expired code
- GIVEN a code past its expiry
- WHEN a user enters it
- THEN joining is rejected with a message and membership is unchanged

#### Scenario: Exhausted code
- GIVEN a code whose uses equal its maximum
- WHEN a user enters it
- THEN joining is rejected

#### Scenario: Unknown code
- GIVEN a code that does not exist
- WHEN a user enters it
- THEN joining is rejected with a message and no information about lists is revealed

#### Scenario: Last use race
- GIVEN a code with one use left and two users joining simultaneously
- WHEN both attempt
- THEN exactly one becomes a member and the other sees a user-facing error stating the code can no longer be used

#### Scenario: Existing member re-enters code
- GIVEN a user is already a member
- WHEN the user enters a valid code of that list
- THEN the user remains a single member, no use is consumed and the join is a no-op

#### Scenario: Join offline
- GIVEN the device is offline
- WHEN a user enters a code
- THEN a message states that joining needs connectivity and membership is unchanged

### Requirement: Remove Member

Any member MUST be able to remove any member; a removed member MUST immediately lose access to the list and its books, while personal books are unaffected. When the last remaining member leaves or is removed, the list MUST be deleted automatically, including its copies, invite codes and member entries.

#### Scenario: Removed loses access
- GIVEN B is a member and A removes B
- WHEN B opens the list
- THEN access is denied

#### Scenario: Leave
- GIVEN B is a member
- WHEN B removes themselves
- THEN B no longer sees the list

#### Scenario: Last member leaves
- GIVEN B is the only member of a list
- WHEN B leaves
- THEN the list, its copies, its invite codes and its member entries are deleted

#### Scenario: Last member removed
- GIVEN a list with one remaining member
- WHEN that member is removed
- THEN the list and everything under it is deleted

### Requirement: Delete List

Any member MUST be able to delete the list after confirmation; deletion MUST remove the list, its copies, its invite codes and all member entries for all members, and MUST NOT affect any member's personal books.

#### Scenario: Confirmed
- GIVEN a list with members A and B
- WHEN B confirms deletion
- THEN A no longer sees the list and A's personal books remain

#### Scenario: Cascade removes everything
- GIVEN a list with copies, invite codes and two member entries
- WHEN a member confirms deletion
- THEN no copy, invite code or member entry of that list remains

#### Scenario: Cancelled
- GIVEN the delete confirmation is open
- WHEN the user cancels
- THEN the list remains

### Requirement: Offline Behavior of Lists

List book reads and edits MUST follow the offline and last-write-wins semantics of `cloud-book-sync`.

#### Scenario: Offline list read
- GIVEN a list was synced earlier and the device is offline
- WHEN a member opens it
- THEN cached books are shown

## Out of Scope

- Invite-code revoke UI: no way to revoke a code in this change; expiry (maximum 7 days) suffices.
- Member cap, roles and ownership.

## Open Questions

- The concurrent-leave case (two last members leaving at the same instant) may leave an inaccessible orphan list; the design accepts this best-effort cascade and the spec does not require atomic cascade across clients.
