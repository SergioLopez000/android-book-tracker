# Project UI conventions

## Structure (atomic design + container/presentational)

| Level | Location | Examples |
|-------|----------|----------|
| Tokens | `ui/theme/` (`Color.kt`, `Type.kt`, `Shape.kt`, `Spacing.kt`, `Theme.kt`) | colors, type scale, shapes, spacing scale |
| Atoms | `ui/components/` | `StarRating`, `BookCoverImage` |
| Molecules / organisms | `ui/components/` | list item, search result row, filter bar |
| Screens | `ui/<feature>/<Feature>Screen.kt` | stateless: `Screen(state, onEvent)` |
| Routes | `ui/<feature>/<Feature>Route.kt` | binds ViewModel, collects state and effects |

- Screens and components take state and lambdas only; no ViewModel references.
- Every component accepts a `modifier: Modifier = Modifier` as its first optional parameter.

## Required UI states

Each screen renders and previews all of: loading, empty, error, content. Provide one `@Preview` per state, in light and dark (`uiMode`), plus one with `fontScale = 2f`.

## Test tags (Appium and Compose tests)

- Set `Modifier.semantics { testTagsAsResourceId = true }` once on the root of each Route so Appium (UiAutomator2) sees tags as resource ids.
- Tag format: `<screen>_<element>[_<id>]` in snake_case, e.g. `book_list_item_42`, `book_edit_save_button`, `book_list_empty_state`.
- Every interactive element and every state container gets a tag. Keep tags stable across redesigns; a redesign must not rename them.

## Accessibility

- Contrast AA (4.5:1 body, 3:1 large text and icons) in light and dark.
- Touch targets at least 48dp.
- `contentDescription` on meaningful images (covers, posters); `null` on decorative ones.
- Rating announced as text ("4.5 of 5 stars").

## Screenshot tests

- Use Roborazzi on Robolectric (`@Config(sdk = [34])`, same setup as existing Compose tests).
- One test per screen state from the previews above; record baselines on intentional changes only.
