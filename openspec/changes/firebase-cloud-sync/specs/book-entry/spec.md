# Delta for Book Entry

## MODIFIED Requirements

### Requirement: Add Book Manually

The system MUST allow saving a new book with only manually entered fields, without any lookup and without network connectivity. The save MUST complete without waiting for server acknowledgement and MUST assign the book a String (UUID) id.
(Previously: persisted to the local database with a numeric id.)

#### Scenario: Manual entry, no network
- GIVEN the device has no network connectivity
- WHEN the user fills title, author, rating and date finished manually and saves
- THEN the book is saved, shown in the library and synced when connectivity returns

#### Scenario: Id assigned
- GIVEN a manual save
- WHEN it completes
- THEN the book has a non-empty String id

### Requirement: Edit Existing Book

The system MUST allow updating rating, comment and other details of a book identified by its String id, replacing the stored entry (last write wins on concurrent edits).
(Previously: updated a persisted book by numeric id.)

#### Scenario: Edit rating and comment
- GIVEN an existing book
- WHEN the user changes rating and review text and saves
- THEN the stored entry reflects the new values on next load

#### Scenario: Edit keeps id
- GIVEN an existing book
- WHEN the user saves an edit
- THEN the book keeps the same id and no duplicate entry is created

### Requirement: Allow Duplicate Titles

The system MUST NOT block saving a book sharing title/author with an existing entry, since re-reads are valid; each entry MUST have its own distinct id.
(Previously: entries were persisted independently without an explicit id statement.)

#### Scenario: Same title saved twice
- GIVEN a book titled "Dune" already exists
- WHEN the user adds another entry titled "Dune" with a different date finished
- THEN both entries are persisted independently with distinct ids
