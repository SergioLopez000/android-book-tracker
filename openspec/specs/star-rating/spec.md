## Capability: star-rating

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
