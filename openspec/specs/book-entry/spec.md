## Capability: book-entry

### Purpose
Add and edit a book's rating, review and details, with a manual entry path always available.

### Requirement: Add Book via Metadata Lookup
The system MUST let the user search for metadata and prefill title/author/cover into an editable form.

#### Scenario: Autofill then save
- GIVEN a metadata search returns a result the user picks
- WHEN the result is applied to the form
- THEN title, author and cover are prefilled AND remain editable
- AND the user can set rating/review and save

### Requirement: Add Book Manually
The system MUST allow saving a new book with only manually entered fields, without any lookup.

#### Scenario: Manual entry, no network
- GIVEN the device has no network connectivity
- WHEN the user fills title, author, rating and date finished manually and saves
- THEN the book is persisted successfully

### Requirement: Validate Half-Star Rating
The system MUST accept only `Float` ratings between 0.5 and 5.0 inclusive, in 0.5 increments, set via the graphical star-rating input component.

#### Scenario: Valid half-star value
- GIVEN the user sets rating to 3.5 via the star input
- WHEN the form is saved
- THEN the book is persisted with `rating == 3.5f`

#### Scenario: Boundary values accepted
- GIVEN the user sets rating to 0.5 or 5.0 via the star input
- WHEN the form is saved
- THEN the save succeeds

#### Scenario: Non-step value rejected
- GIVEN an attempt to set a non-0.5-multiple value
- WHEN the user attempts to save
- THEN validation MUST reject it and the book is not persisted

### Requirement: Require Rating on Save
The system MUST NOT persist a book without a rating.

#### Scenario: Missing rating blocks save
- GIVEN the rating field is empty
- WHEN the user attempts to save
- THEN save is blocked and a validation message is shown

### Requirement: Edit Existing Book
The system MUST allow updating rating, comment and other details of a persisted book.

#### Scenario: Edit rating and comment
- GIVEN a persisted book
- WHEN the user changes rating and review text and saves
- THEN the persisted entry reflects the new values on next load

### Requirement: Allow Duplicate Titles
The system MUST NOT block saving a book sharing title/author with an existing entry, since re-reads are valid.

#### Scenario: Same title saved twice
- GIVEN a book titled "Dune" already exists
- WHEN the user adds another entry titled "Dune" with a different date finished
- THEN both entries are persisted independently

### Requirement: Render Book Cover in Edit Form
The system MUST render the book's cover in the edit screen via the shared cover-image component, with the same placeholder/fallback behavior as the list screen when `coverUrl` is null or fails to load.

#### Scenario: Cover shown while editing
- GIVEN a persisted book with a valid `coverUrl`
- WHEN the user opens the edit screen
- THEN the cover image is rendered

#### Scenario: Missing cover in edit form
- GIVEN a persisted book with `coverUrl == null`
- WHEN the user opens the edit screen
- THEN the placeholder/fallback cover is shown instead of blank space
