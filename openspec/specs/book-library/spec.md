## Capability: book-library

### Purpose
List, sort, filter and delete persisted read books.

### Requirement: List Read Books
The system MUST display all persisted `Book` entries, rendering each entry's cover via the shared cover-image component and its rating via the graphical star-rating component.

#### Scenario: Books present
- GIVEN one or more books are persisted
- WHEN the user opens the library screen
- THEN each book's title, author, cover image, star rating and date finished are shown

#### Scenario: Empty library
- GIVEN no books are persisted
- WHEN the user opens the library screen
- THEN an empty-state message is shown instead of a list
- AND no crash or indefinite loading spinner occurs

#### Scenario: Missing or failed cover image
- GIVEN a book has `coverUrl == null`, or a non-null `coverUrl` that fails to load
- WHEN rendered in the list
- THEN a placeholder/fallback cover is shown instead of a broken image or blank space

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
The system MUST filter the list to books with `rating >= selectedMinimum`; when a date-range filter is also active, both constraints MUST apply simultaneously (AND).

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

### Requirement: Filter by Date Range
The system MUST offer three mutually exclusive buckets over `dateFinished` — "último mes" (last 30 days, inclusive), "último año" (last 365 days, inclusive), "histórico" (no bound, all books) — with "histórico" as the default.

#### Scenario: Default is histórico
- GIVEN no bucket has been explicitly selected
- WHEN the library screen opens
- THEN "histórico" is active and books are not date-filtered

#### Scenario: Último mes narrows results
- GIVEN books finished 10, 40 and 400 days ago
- WHEN the user selects "último mes"
- THEN only the book finished 10 days ago is shown

#### Scenario: Buckets are mutually exclusive
- GIVEN "último mes" is active
- WHEN the user selects "último año"
- THEN "último mes" is deselected and only the "último año" bound applies

### Requirement: Combine Date-Range and Minimum-Rating Filters
The system MUST apply the date-range filter and minimum-rating filter as an AND combination when both are active.

#### Scenario: Both filters narrow results together
- GIVEN books with varying ratings and dates
- WHEN the user sets minimum rating to 4.0 AND selects "último mes"
- THEN only books rated >= 4.0 AND finished within 30 days are shown

#### Scenario: Combination yields zero results
- GIVEN no persisted book satisfies both active constraints
- WHEN both filters are applied
- THEN an empty-result message is shown, not an error
