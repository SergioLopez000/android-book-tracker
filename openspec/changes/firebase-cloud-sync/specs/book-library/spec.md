# Delta for Book Library

## ADDED Requirements

### Requirement: String Book Identifier

A `Book` MUST be identified by a non-empty String id (UUID) everywhere: domain, navigation arguments and UI element tags.

#### Scenario: Navigate by string id
- GIVEN a book with id "7f3c-example-uuid"
- WHEN the user opens it from the list
- THEN the edit screen loads that exact book

#### Scenario: Unknown id
- GIVEN navigation to an id that does not exist
- WHEN the edit screen loads
- THEN a not-found state is shown without crashing

## MODIFIED Requirements

### Requirement: List Read Books

The system MUST display all books of the signed-in user (from cloud storage or its offline cache), rendering each entry's cover via the shared cover-image component and its rating via the graphical star-rating component.
(Previously: displayed all books persisted in the local Room database.)

#### Scenario: Books present
- GIVEN one or more books belong to the signed-in user
- WHEN the user opens the library screen
- THEN each book's title, author, cover image, star rating and date finished are shown

#### Scenario: Empty library
- GIVEN the user has no books
- WHEN the user opens the library screen
- THEN an empty-state message is shown instead of a list
- AND no crash or indefinite loading spinner occurs

#### Scenario: Missing or failed cover image
- GIVEN a book has `coverUrl == null`, or a non-null `coverUrl` that fails to load
- WHEN rendered in the list
- THEN a placeholder/fallback cover is shown instead of a broken image or blank space

#### Scenario: Offline library
- GIVEN books were synced earlier and the device is offline
- WHEN the user opens the library screen
- THEN the cached books are shown and no indefinite loading spinner occurs

### Requirement: Delete Book Entry

The system MUST permanently remove a selected book from the user's cloud storage on confirmed delete, without waiting for server acknowledgement when offline.
(Previously: removed the book from Room.)

#### Scenario: Delete confirmed
- GIVEN a book is selected for deletion
- WHEN the user confirms delete
- THEN the book is removed from the user's storage and no longer appears in the list

#### Scenario: Delete cancelled
- GIVEN a delete action is requested
- WHEN the user cancels the confirmation
- THEN the book remains unchanged in the list

#### Scenario: Delete offline
- GIVEN the device is offline
- WHEN the user confirms delete
- THEN the book disappears from the list immediately and the deletion syncs on reconnect
