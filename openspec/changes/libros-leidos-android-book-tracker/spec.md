# Spec: Libros Leidos — Android Book Tracker (MVP)

Full specs (no prior specs exist — greenfield). Covers `book-library`, `book-entry`, `book-metadata-lookup`.

---

## Capability: book-library

### Purpose
List, sort, filter and delete persisted read books.

### Requirement: List Read Books
The system MUST display all persisted `Book` entries in the library screen.

#### Scenario: Books present
- GIVEN one or more books are persisted
- WHEN the user opens the library screen
- THEN each book's title, author, cover, rating and date finished are shown

#### Scenario: Empty library
- GIVEN no books are persisted
- WHEN the user opens the library screen
- THEN an empty-state message is shown instead of a list
- AND no crash or loading spinner persists indefinitely

### Requirement: Sort Book List
The system MUST allow sorting the list by date finished and by rating, ascending or descending.

#### Scenario: Sort by date finished
- GIVEN multiple books with different `dateFinished` values
- WHEN the user selects "sort by date"
- THEN books are ordered by `dateFinished`

#### Scenario: Sort by rating
- GIVEN multiple books with different ratings
- WHEN the user selects "sort by rating"
- THEN books are ordered by `rating`

### Requirement: Filter by Minimum Rating
The system MUST filter the list to books with `rating >= selectedMinimum`.

#### Scenario: Filter narrows results
- GIVEN books with ratings 2.0, 3.5 and 5.0
- WHEN the user sets minimum rating to 3.5
- THEN only books rated 3.5 and 5.0 are shown

#### Scenario: Filter yields no matches
- GIVEN no book meets the selected minimum rating
- WHEN the filter is applied
- THEN an empty-result message is shown, not an error

### Requirement: Delete Book Entry
The system MUST permanently remove a selected book from persistence on confirmed delete.

#### Scenario: Delete confirmed
- GIVEN a persisted book is selected for deletion
- WHEN the user confirms delete
- THEN the book is removed from Room and no longer appears in the list

#### Scenario: Delete cancelled
- GIVEN a delete action is requested
- WHEN the user cancels the confirmation
- THEN the book remains unchanged in the list

---

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
The system MUST accept only `Float` ratings between 0.5 and 5.0 inclusive, in 0.5 increments.

#### Scenario: Valid half-star value
- GIVEN the user sets rating to 3.5
- WHEN the form is saved
- THEN the book is persisted with `rating == 3.5f`

#### Scenario: Boundary values accepted
- GIVEN the user sets rating to 0.5 or 5.0
- WHEN the form is saved
- THEN the save succeeds

#### Scenario: Non-step value rejected
- GIVEN the user sets rating to 3.7 (not a 0.5 multiple)
- WHEN the user attempts to save
- THEN validation MUST reject the value and the book is not persisted

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

---

## Capability: book-metadata-lookup

### Purpose
Search Open Library, falling back to Google Books, to autofill entry fields; degrade gracefully to manual entry.

### Requirement: Search Open Library First
The system MUST query Open Library as the primary metadata source for a title search.

#### Scenario: Open Library returns matches
- GIVEN the user searches a title with network available
- WHEN Open Library returns one or more results
- THEN those results are shown for selection and Google Books is not queried

### Requirement: Fallback to Google Books
The system MUST query Google Books when Open Library returns no results or fails.

#### Scenario: Open Library empty, Google Books has match
- GIVEN Open Library returns zero results for a search
- WHEN the fallback triggers
- THEN Google Books results are shown for selection

### Requirement: Autofill Editable Fields
The system MUST map a selected result's title, author and cover URL into the form without locking them.

#### Scenario: Selection prefills form
- GIVEN the user picks a search result
- WHEN the form loads
- THEN title/author/cover are prefilled and the user can still edit any field before saving

### Requirement: Graceful Degradation
The system MUST fall back to manual entry, never blocking the form, when lookup fails or has no match.

#### Scenario: No results from either source
- GIVEN both Open Library and Google Books return zero results
- WHEN the search completes
- THEN a "no matches" message is shown AND the manual entry form remains usable

#### Scenario: Network failure during lookup
- GIVEN the device loses connectivity mid-search
- WHEN the search request fails
- THEN an error message is shown AND the user can proceed with manual entry without the app blocking or crashing
