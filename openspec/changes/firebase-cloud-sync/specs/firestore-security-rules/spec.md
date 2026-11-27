# Firestore Security Rules Specification

## Purpose

Server-side access control, validation and abuse limits, verified by emulator tests that gate CI.

## Requirements

### Requirement: Default Deny and Authentication

`firestore.rules` MUST deny every read and write that no rule explicitly allows, and MUST deny all access to unauthenticated requests.

#### Scenario: Unauthenticated denied
- GIVEN an unauthenticated client
- WHEN it reads or writes any path
- THEN the request is denied

#### Scenario: Unknown path denied
- GIVEN a signed-in user
- WHEN it accesses a path with no rule
- THEN the request is denied

### Requirement: Personal Book Access

A user MUST be able to read, create, update and delete only their own personal books.

#### Scenario: Owner allowed
- GIVEN user A
- WHEN A writes and reads A's book
- THEN both are allowed

#### Scenario: Other user denied
- GIVEN user B
- WHEN B reads or writes A's book
- THEN both are denied

### Requirement: List Access by Membership

Only members MUST be able to read or write a list, its books and its member entries; any member MUST be able to update or delete the list and remove any member. A member entry MUST NOT be updatable. A user MUST be able to query, across all lists, the member entries whose `uid` is their own, and MUST NOT be able to read other users' entries through that query.

#### Scenario: Member allowed
- GIVEN B is a member
- WHEN B edits a list book and removes A
- THEN both are allowed

#### Scenario: Non-member denied
- GIVEN C is not a member
- WHEN C reads a list book
- THEN it is denied

#### Scenario: Removed member denied
- GIVEN B was removed
- WHEN B reads the list
- THEN it is denied

#### Scenario: Own memberships collection-group query
- GIVEN user B is a member of two lists
- WHEN B runs a collection-group query on `members` filtered by `uid == B`
- THEN both of B's member entries are returned

#### Scenario: Unfiltered collection-group query denied
- GIVEN user B
- WHEN B runs a collection-group query on `members` without the own-`uid` filter, or filtered by another user's `uid`
- THEN it is denied

#### Scenario: Member entry cannot be updated
- GIVEN B is a member
- WHEN B updates any field of a member entry
- THEN it is denied

### Requirement: List Creation Rules

Rules MUST allow a signed-in user to create a list only with a `name` of at most 100 characters and only fields `name` and `createdAt`, and MUST require the creator's own member entry to be created in the same batch (the list does not exist before and both exist after the batch). A list created without that member entry MUST be denied.

#### Scenario: Valid creation batch
- GIVEN a signed-in user
- WHEN they create a list named "Club" and their own member entry in one batch
- THEN the batch is allowed

#### Scenario: Name too long
- GIVEN a list name of 101 characters
- WHEN the creation batch is written
- THEN it is denied

#### Scenario: Missing creator member entry
- GIVEN a signed-in user
- WHEN they create a list document without a member entry in the batch
- THEN it is denied

#### Scenario: Creator entry for someone else
- GIVEN a signed-in user
- WHEN the batch creates the list with a member entry for a different `uid`
- THEN it is denied

### Requirement: Member Entry Validation

Rules MUST accept a member entry only when its document id equals its `uid` field and `uid` equals the requester, and its fields are exactly `uid`, `displayName`, `joinedAt` and `inviteCode` (`inviteCode` being a string or null) with the correct types. Any other field MUST be rejected.

#### Scenario: Valid member entry
- GIVEN a creator or joining user
- WHEN they write their own member entry with exactly the allowed fields
- THEN it is allowed (subject to the creation or join rule)

#### Scenario: Extra field rejected
- GIVEN a member entry with an undeclared field
- WHEN it is written
- THEN it is denied

#### Scenario: Id and uid mismatch
- GIVEN a member entry whose document id differs from its `uid`
- WHEN it is written
- THEN it is denied

### Requirement: Invite Code Enforcement

Rules MUST allow a user to add only themselves as a member and only by consuming a code that is unexpired and has remaining uses, incrementing the use count by exactly one in the same batch; they MUST deny any other membership self-add. A member entry created through a code MUST reference that code. A user already a member MUST NOT be able to consume a use again.

#### Scenario: Valid join
- GIVEN an unexpired code with uses remaining
- WHEN a user adds themselves and increments uses by 1
- THEN the write is allowed

#### Scenario: Expired or exhausted
- GIVEN an expired code, or one at its maximum uses
- WHEN a user attempts to join
- THEN it is denied

#### Scenario: Adding another user
- GIVEN a non-member
- WHEN they add a different user id as member
- THEN it is denied

#### Scenario: Tampered counter
- GIVEN a code
- WHEN a client decrements uses or raises its maximum without being a member
- THEN it is denied

#### Scenario: Replay by existing member
- GIVEN a user who is already a member of the list
- WHEN they increment the code's uses again
- THEN it is denied

### Requirement: Invite Field Validation

Rules MUST accept an invite only when it has exactly the fields `listId`, `expiresAt`, `maxUses` and `uses`, with `uses` equal to 0 on creation, `maxUses` between 1 and 50 inclusive, and `expiresAt` no later than 7 days after the request time. An invite MUST NOT carry a `createdBy` field. Only a member of the referenced list MUST be able to create or delete an invite for it.

#### Scenario: Valid invite
- GIVEN a member
- WHEN they create an invite with `uses` 0, `maxUses` 10 and expiry in 7 days
- THEN it is allowed

#### Scenario: Default boundaries accepted
- GIVEN a member
- WHEN they create invites with `maxUses` 1 and 50 and expiry exactly 7 days out
- THEN each is allowed

#### Scenario: Too many uses
- GIVEN a member
- WHEN they create an invite with `maxUses` 51 or 0
- THEN it is denied

#### Scenario: Expiry too far
- GIVEN a member
- WHEN they create an invite expiring more than 7 days from now
- THEN it is denied

#### Scenario: Creator field rejected
- GIVEN a member
- WHEN they create an invite containing `createdBy`
- THEN it is denied

#### Scenario: Non-member creates invite
- GIVEN a user who is not a member of the list
- WHEN they create an invite for it
- THEN it is denied

### Requirement: Data Validation and Size Limits

Rules MUST reject book writes whose rating is outside 0.5 to 5.0 or not a 0.5 multiple, whose fields have the wrong type, whose `title` or `author` exceed 300 characters, whose `review` exceeds 5000 characters, or that contain unexpected fields. These limits MUST apply identically to personal books and list copies.

#### Scenario: Valid book
- GIVEN a book with allowed fields and rating 3.5
- WHEN it is written by its owner
- THEN it is allowed

#### Scenario: Invalid rating
- GIVEN a book with rating 4.3 or 6.0
- WHEN it is written
- THEN it is denied

#### Scenario: Oversized review
- GIVEN a review of 5001 characters
- WHEN it is written
- THEN it is denied

#### Scenario: Review at the limit
- GIVEN a review of exactly 5000 characters
- WHEN it is written
- THEN it is allowed

#### Scenario: Oversized title or author
- GIVEN a title or author of 301 characters
- WHEN it is written
- THEN it is denied

#### Scenario: Unexpected field
- GIVEN a book with an undeclared field
- WHEN it is written
- THEN it is denied

#### Scenario: List copy validated alike
- GIVEN a member writing a list copy with an oversized review
- WHEN it is written
- THEN it is denied

### Requirement: App Check Enforcement

Firestore and Authentication requests MUST be accepted only with a valid App Check token (Play Integrity in release); requests without one MUST be rejected. Enforcement is a Firebase console setting, not part of `firestore.rules`, and MUST therefore be covered by a manual checklist in the repository documentation.

#### Scenario: Missing token
- GIVEN enforcement is enabled in the Firebase project
- WHEN a request lacks an App Check token
- THEN it is rejected

#### Scenario: Debug builds
- GIVEN a debug build
- WHEN it uses the debug provider
- THEN requests are accepted and release builds never register the debug provider

#### Scenario: Manual checklist exists
- GIVEN the repository documentation
- WHEN it is read
- THEN it contains a checklist step to enable App Check enforcement for Firestore and Authentication in the Firebase console and to verify it

### Requirement: Emulator Rules Tests

The repository MUST contain a rules test suite run against the Firebase emulator that asserts at least one allow and one deny case for every requirement above except App Check, and it MUST run with no production credentials. It runs in the CI job `rules-tests`.

#### Scenario: Suite passes
- GIVEN the emulator is started
- WHEN the suite runs
- THEN it exits 0

#### Scenario: Weakened rule fails
- GIVEN a rule is relaxed to allow non-members
- WHEN the suite runs
- THEN it exits non-zero

## Open Questions

- Resolved by design (revision 2): member entry `displayName` is a string of 1–100 characters; `coverUrl` is null or a string of at most 2048 characters.
