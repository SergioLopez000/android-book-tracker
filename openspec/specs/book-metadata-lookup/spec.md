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
