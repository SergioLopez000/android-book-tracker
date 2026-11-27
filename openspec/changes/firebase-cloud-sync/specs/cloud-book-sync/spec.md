# Cloud Book Sync Specification

## Purpose

Per-user cloud storage of books in Firestore with offline persistence and last-write-wins, replacing the Room database.

## Requirements

### Requirement: Per-User Book Storage

The system MUST store each signed-in user's books in cloud storage scoped to that user, and MUST NOT expose one user's books to another user.

#### Scenario: Own books only
- GIVEN users A and B each have books
- WHEN A opens the library
- THEN only A's books are shown

### Requirement: Client-Generated String Identifiers

The system MUST assign each new book a UUID string identifier on the client, without network access.

#### Scenario: Offline create
- GIVEN the device is offline
- WHEN the user saves a new book
- THEN the book gets a non-empty UUID string id and appears in the library

#### Scenario: Ids are unique
- GIVEN two books are saved
- WHEN their ids are compared
- THEN they differ

### Requirement: Multi-Device Sync

The system MUST make a book saved, edited or deleted on one device appear on another device signed in to the same account.

#### Scenario: Save propagates
- GIVEN the same account on devices 1 and 2, both online
- WHEN device 1 saves a book
- THEN device 2 shows it without manual refresh

#### Scenario: Delete propagates
- GIVEN both devices show a book
- WHEN device 1 deletes it
- THEN device 2 no longer shows it

### Requirement: Offline Read and Non-Blocking Write

The system MUST serve reads from the local cache when offline, and a save or delete MUST complete for the caller without waiting for server acknowledgement. Pending writes MUST be sent when connectivity returns.

#### Scenario: Offline read
- GIVEN books were synced earlier and the device is offline
- WHEN the user opens the library
- THEN the cached books are shown

#### Scenario: Offline save does not hang
- GIVEN the device is offline
- WHEN the user saves a book
- THEN save returns promptly and the UI leaves the edit screen

#### Scenario: Reconnect uploads
- GIVEN a book was saved offline
- WHEN connectivity returns
- THEN the book appears on the user's other devices

### Requirement: Last-Write-Wins

When the same book is changed concurrently, the system MUST resolve the conflict by keeping the write the server applies last, replacing the whole book entry, with no merge and no conflict prompt.

#### Scenario: Concurrent edits
- GIVEN two devices edit the same book offline, device 1 then device 2 reconnects after
- WHEN both writes are applied
- THEN all devices show the book exactly as written by the last-applied write

#### Scenario: Edit versus delete
- GIVEN device 1 deletes a book and device 2 edits it, applied in that order
- WHEN both are applied
- THEN the book exists as device 2 wrote it

### Requirement: Client-Side Filter and Sort

The system MUST apply minimum-rating, date-range filters and date/rating sorts on the client over the synced data, with the same results as the previous local implementation.

The sort MUST be deterministic: ties on the primary key are broken by title ascending, then id ascending, so equal books keep a stable order across devices and reloads.

#### Scenario: Filter and sort combined
- GIVEN books with ratings 2.0, 3.5, 5.0 and varied dates
- WHEN minimum rating 3.5 and sort by date are applied
- THEN only the books rated 3.5 and 5.0 appear, ordered by `dateFinished`

#### Scenario: Tie-break by title then id
- GIVEN three books with the same `dateFinished`, where two share the same title
- WHEN the list is sorted by date
- THEN they appear by title ascending and, for equal titles, by id ascending, identically on every device

#### Scenario: Tie-break under rating sort
- GIVEN books with equal rating
- WHEN the list is sorted by rating
- THEN equal-rated books appear by title ascending, then id ascending

### Requirement: Room Removed Without Import

The system MUST NOT depend on Room and MUST NOT import or migrate books from any previous local database.

#### Scenario: Upgrade
- GIVEN a device holding a legacy local database
- WHEN the new version is installed and the user signs in
- THEN the library starts empty and no import is offered

### Requirement: Storage Behind a Port

The domain MUST access books only through the `BookRepository` port, with a Firestore adapter and a JVM-testable thin remote interface, and MUST NOT depend on Android or Firebase types.

#### Scenario: Domain isolation
- GIVEN the `domain` package
- WHEN its imports are inspected
- THEN none reference `android.*`, `com.google.firebase.*` or `data.*`

## Open Questions

- Behavior when a first-ever sign-in on a device is offline is undefined.
