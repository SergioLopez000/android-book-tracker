# Delta Spec: IMDb-Style UI and Date-Range Filters

## Capability: app-theme (NEW)

### Purpose
App-wide dark Material3 theme applied at the composition root.

### Requirement: Apply Custom Dark Color Scheme
The system MUST apply a custom dark Material3 `ColorScheme` and `Typography` at the composition root; no screen MUST fall back to stock `MaterialTheme` defaults or a light scheme.

#### Scenario: App launches under dark theme
- GIVEN the app is launched
- WHEN the library screen renders
- THEN every surface, text and control uses the custom dark color scheme

#### Scenario: Theme consistent across screens
- GIVEN the user navigates from list to edit screen
- WHEN the edit screen renders
- THEN it uses the same dark color scheme, not Compose defaults

---

## Capability: star-rating (NEW)

### Purpose
Graphical, half-star-precision rendering and input for `Book.rating`.

### Requirement: Render Graphical Star Rating
The system MUST render `rating` (0.5–5.0, 0.5 increments) as filled/half-filled/empty stars, not numeric text, in both list and edit contexts.

#### Scenario: Whole-star rating
- GIVEN a book with `rating == 4.0f`
- WHEN displayed
- THEN 4 filled stars and 1 empty star show, with no numeric text

#### Scenario: Half-star rating
- GIVEN a book with `rating == 3.5f`
- WHEN displayed
- THEN 3 filled, 1 half-filled and 1 empty star show

### Requirement: Set Rating via Graphical Star Input
The system MUST let the user set `rating` by interacting with the star component in the edit screen, in 0.5 increments; it replaces the numeric `RatingInput`.

#### Scenario: User taps to set half-star value
- GIVEN the edit screen's star input
- WHEN the user selects the 3.5-star position
- THEN the form value updates to `rating == 3.5f`

#### Scenario: Input stays within valid bounds
- GIVEN the star input
- WHEN the user interacts with it
- THEN the resulting value is always 0.5–5.0 inclusive, in 0.5 steps

---

## Capability: book-library

### MODIFIED Requirements

### Requirement: List Read Books
The system MUST display all persisted `Book` entries, rendering each entry's cover via the shared cover-image component and its rating via the graphical star-rating component.
(Previously: displayed cover/rating without specifying rendering mechanism.)

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

### Requirement: Filter by Minimum Rating
The system MUST filter the list to books with `rating >= selectedMinimum`; when a date-range filter is also active, both constraints MUST apply simultaneously (AND).
(Previously: only filter axis was minimum rating.)

#### Scenario: Filter narrows results
- GIVEN books with ratings 2.0, 3.5 and 5.0
- WHEN the user sets minimum rating to 3.5
- THEN only books rated 3.5 and 5.0 are shown

#### Scenario: Filter yields no matches
- GIVEN no book meets the selected minimum rating
- WHEN the filter is applied
- THEN an empty-result message is shown, not an error

### ADDED Requirements

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
- THEN "último mes" is deselect and only the "último año" bound applies

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

---

## Capability: book-entry

### MODIFIED Requirements

### Requirement: Validate Half-Star Rating
The system MUST accept only `Float` ratings between 0.5 and 5.0 inclusive, in 0.5 increments, set via the graphical star-rating input component.
(Previously: same range/step, input mechanism unspecified.)

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

### ADDED Requirements

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
