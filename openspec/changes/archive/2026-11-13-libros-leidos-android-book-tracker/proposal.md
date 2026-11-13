# Proposal: Libros Leidos — Android Book Tracker (MVP)

## Intent

Greenfield personal, single-user Android app to record books already read: title, author, cover, rating, free-text review, date finished. No code exists yet. Success = the user adds, edits, deletes and browses read books fully offline, with metadata autofilled from an online title search when available.

## Scope

### In Scope
- Room (KSP) local persistence, single `Book` entity
- Book list screen: sort by date finished / rating, filter by minimum rating
- Add/Edit form screen; manual entry always available as override
- Delete entry
- Open Library title search autofill (title/author/coverUrl); Google Books fallback
- Rating finalized as `Float`, 0.5–5.0 in half-star steps
- Domain layer: `Book`, `BookRepository` + `BookMetadataSource` ports, use cases
- Robolectric JVM tests for domain, DAO, ViewModel and Compose layers

### Out of Scope
- Cloud sync, accounts, multi-user, social features
- Export/import backup — documented future extension, not built now
- Reading progress / want-to-read shelves, tags, ISBN barcode scanning
- Play Store publishing

## Capabilities

### New Capabilities
- `book-library`: persist, list, sort and filter read books
- `book-entry`: add, edit, delete a book with rating and review
- `book-metadata-lookup`: search Open Library (Google Books fallback), map to domain

### Modified Capabilities
- None

## Approach

Hexagonal layering scaled to app size. Single Gradle module `:app`, packages under `com.librosleidos` :

| Package | Contents |
|---|---|
| `domain/` | `Book`, `BookRepository`, `BookMetadataSource`, use cases — pure Kotlin, no Android |
| `data/local/` | `BookEntity`, `BookDao`, `BookRepositoryImpl` adapter, mappers |
| `data/remote/` | `OpenLibraryMetadataSource`, `GoogleBooksMetadataSource`, DTOs |
| `ui/list/`, `ui/edit/` | Compose screens + ViewModels exposing `StateFlow<UiState>` |
| `di/` | dependency wiring |

Add-via-lookup flow: edit ViewModel → `SearchBookMetadataUseCase` → `BookMetadataSource` port → Open Library (fallback Google Books) → result list → user picks → fields prefill, still editable → `SaveBookUseCase` → `BookRepository` → Room. Network failure degrades to manual entry and never blocks saving.

## Affected Areas

| Area | Impact | Description |
|---|---|---|
| repo root | New | Gradle/Kotlin/Compose scaffold, minSdk 26, targetSdk 36 |
| `app/src/main/java/...` | New | domain, data, ui, di packages |
| `app/src/test/` | New | Robolectric + coroutines-test suites |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Robolectric/Compose version mismatch causing flaky JVM UI tests | Med | Version spike in design phase before UI tasks |
| Open Library sparse or missing matches | Med | Google Books fallback plus manual override |
| Over-layering a small app | Low | One module, only the named use cases |
| Half-star rating rounding | Low | Validate 0.5 steps at the domain boundary |

## Rollback Plan

Greenfield: `git reset --hard` to the initial commit and delete generated build output. No user data, no downstream consumers.

## Dependencies

- Open Library API (no key, descriptive User-Agent); Google Books API (key required, fallback only)
- Android SDK 36, JDK 17+, Room 2.8+ with KSP

## Success Criteria

- [ ] Add a book via Open Library search with autofilled, still-editable fields
- [ ] Add a book fully manually with no network
- [ ] Edit and delete an entry; changes survive app restart
- [ ] List sorts by date finished and rating, filters by minimum rating
- [ ] All domain, DAO, ViewModel and Compose tests pass on `./gradlew test` without an emulator
