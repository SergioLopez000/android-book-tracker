## Exploration: Android app to track read books (rating + comment per book)

### Current State
Greenfield project. Working directory `/Users/sergio.lopezm/Desktop/libros-leidos` is empty and git-initialized with no source, no Gradle files, no `.codegraph/` index (confirmed no codebase to explore — this exploration is purely domain/tech-stack research, not code investigation). No prior SDD artifacts exist for this project in Engram.

### Domain Model (MVP-focused, single-user, personal use — not social)

Minimal `Book` entity for a v1 MVP:
- `id` (local primary key, e.g. UUID or Room autoGenerate Long)
- `title` (String, required)
- `author` (String, required — single free-text field is enough for MVP; splitting into structured author records is over-engineering for a single-user app)
- `coverUrl` or `coverImagePath` (String, optional — either a remote URL from a metadata API or a locally cached image path)
- `dateFinished` (LocalDate, required — the core "I read this" timestamp; app is framed around books already finished, not a reading-in-progress tracker)
- `rating` (Int or Float, required — see open question on scale below)
- `comment` / `review` (String, optional, free text — the personal note/review)
- `genre` (String, optional — nice-to-have, useful for filtering later but not MVP-blocking)
- `pageCount` (Int, optional — nice-to-have, mostly cosmetic/stat value)
- `createdAt` / `updatedAt` (Instant, for local record-keeping and future sync conflict resolution if ever added)

Deliberately excluded from MVP to avoid scope creep: reading status/progress tracking (currently-reading, want-to-read shelves), multi-user accounts, social sharing, tags/collections beyond genre, ISBN as a separate lookup key (useful only if metadata API integration is adopted).

### Tech Stack Options

1. **Kotlin + Jetpack Compose + Room + MVVM (Clean/Hexagonal-ish layering)** — recommended
   - Pros: Compose is Google's default UI toolkit for new apps as of 2026 (View/Fragment/RecyclerView stack is in maintenance mode per official guidance); Room is the standard local persistence layer with first-class Kotlin Flow/Coroutines support; MVVM + StateFlow + `collectAsStateWithLifecycle()` is the documented official pattern (ViewModel owns state and business logic, Compose is a pure rendering layer); a domain layer (use cases) plus repository interfaces mapped onto Room DAOs gives clean separation matching the user's stated Clean/Hexagonal Architecture expertise — domain layer has zero Android/Room/Compose dependencies, testable in pure JVM.
   - Cons: Compose has a steeper initial learning curve than Views if the user is unfamiliar with declarative UI; slightly more boilerplate for full Clean Architecture layering (domain models, mappers, use cases) than a flat MVVM would need for an app this small — layering must stay proportional to the app's actual complexity.
   - Effort: Low-Medium (Compose + Room + MVVM is now the well-trodden, well-documented path with strong tooling support)

2. **Kotlin + Views (XML layouts) + Room + MVVM** — not recommended
   - Pros: Slightly more legacy tutorials/StackOverflow content; marginally lower learning curve for someone coming from traditional imperative UI.
   - Cons: Google has moved the View/Fragment/RecyclerView/ViewBinding ecosystem into maintenance mode; worse long-term maintainability and hiring/tooling trajectory; more boilerplate for lists, animations, and state-driven UI (which is exactly this app's UI: a list of rated books).
   - Effort: Medium

3. **Persistence: Room vs. plain SQLite / DataStore-only** — Room recommended
   - Room is the standard abstraction over SQLite for structured relational data with type-safe queries, Flow support, and migrations; DataStore is appropriate only for small key-value preferences (e.g. sort order, theme), not for the book records themselves. No real tradeoff here — Room is the clear default for this data shape (a single, moderately-attributed entity, not schemaless data).

### Architecture Layering Recommendation

Given the user's stated Clean/Hexagonal Architecture expertise (per their global CLAUDE.md), a right-sized layering for this app:
- **Domain layer**: `Book` domain model, repository *interface* (port), use cases (`SaveBookUseCase`, `GetBooksUseCase`, `DeleteBookUseCase`, `RateBookUseCase` or similar) — pure Kotlin, no Android/Room/Compose imports, fully unit-testable on plain JVM.
- **Data layer**: Room `@Entity`/`@Dao`, a repository *implementation* (adapter) mapping Room entities to domain models, and — if a metadata API is adopted — a remote data source adapter.
- **UI layer**: Compose screens + ViewModels holding `StateFlow<UiState>`, consuming use cases only (never touching Room or a remote API directly).

This is a hexagonal "ports and adapters" shape scaled down to app size: the domain layer defines the repository port; Room and (optionally) a remote API are two interchangeable adapters behind it. This keeps the architecture honest without over-engineering a single-user personal app.

### Book Metadata Lookup — Open Question, Not Decided

Whether to integrate a book-metadata API (Google Books API or Open Library API) to auto-fill title/author/cover from an ISBN or title search, versus fully manual entry, is a genuine tradeoff to resolve during proposal, not here:
- **Manual entry only**: Zero external dependency, works fully offline, no API quota/rate-limit concerns, simplest MVP. Con: more typing for the user, no cover art unless manually supplied.
- **Google Books API**: Good coverage and cover images; requires an API key (free tier has daily quotas) and network access at add-time; introduces a network dependency into what would otherwise be a fully offline-first app.
- **Open Library API**: No API key required, fully free; data completeness/consistency is more variable than Google Books; also requires network access at add-time.
- A middle path is manual entry for MVP with metadata lookup as an explicit post-MVP enhancement, keeping the domain layer's repository port stable either way (metadata lookup would be a separate port from persistence).

### Testing Approach (Strict TDD Mode is globally enabled for this user)

Implications for an Android project under Strict TDD:
- **Domain layer (use cases, mappers)**: Pure JVM unit tests (JUnit4/5 or Kotlin Test), no Android dependency, fast, run via `./gradlew test`. This is where TDD is cheapest and most valuable — write the use case test first, then the use case.
- **Data layer (Room DAOs)**: Instrumented tests using Room's in-memory database (`Room.inMemoryDatabaseBuilder`) — these can run as fast JVM tests without a device/emulator if using Robolectric, or as connected Android tests (`./gradlew connectedAndroidTest`) against an emulator/device otherwise.
- **ViewModel layer**: Pure JVM unit tests using `kotlinx-coroutines-test` (`runTest`, `TestDispatcher`) to drive `StateFlow` emissions — no Android framework dependency needed if the ViewModel is written against `Dispatchers` injected rather than hardcoded.
- **UI layer (Compose)**: Compose UI testing via `createComposeRule()` / `createAndroidComposeRule()` — semantics-tree-based assertions, run as instrumented tests. These are slower and more brittle; TDD discipline here typically means testing critical user flows (add book, rate book, edit/delete) rather than exhaustively testing every composable.
- Net effect: Strict TDD is very achievable for the domain and ViewModel layers (fast, JVM-only, red-green-refactor cycle is cheap) and requires either Robolectric or an emulator/device for Room and Compose UI tests — this should be flagged in the proposal so the task breakdown accounts for setting up that test infrastructure (Robolectric config, `testInstrumentationRunner`, potentially a CI emulator) before implementation tasks that touch Room or Compose UI begin.

### Open Questions to Surface Before/During Proposal

1. **Local-only vs. cloud sync/backup**: Is this purely on-device storage (Room only, no account), or should there be a backup/export mechanism (e.g. Android's own cloud backup, or an export-to-file feature) so data survives device loss? This materially affects the repository port design and whether a remote adapter is ever needed.
2. **Offline-first**: Assuming local-only is intended (given no cloud sync request), confirm the app has zero network dependency for its core function — the only network use, if any, would be for the metadata-lookup feature above.
3. **Minimum SDK target**: No target Android version was specified. A reasonable modern default is minSdk 24-26 (covers the vast majority of active devices as of 2026) but this should be confirmed rather than assumed.
4. **Editing and deleting entries**: The request only mentions "save" — confirm whether editing an existing entry's rating/comment and deleting an entry are in-scope for MVP (very likely yes, since typos and re-rating are normal, but not explicitly stated).
5. **Rating scale**: Not specified — options include a 5-star scale (common convention, e.g. Goodreads), a 1-10 numeric scale, or half-star granularity. This is a small but real product decision that affects the `rating` field type (Int vs Float) and UI component choice.
6. **Sorting/filtering**: Should the main book list support sorting (by date finished, by rating) or filtering (by rating threshold, by genre)? Not explicitly requested but a very likely near-term expectation for a "list of read books" app.
7. **Single free-text author field vs. structured author list**: Confirmed as MVP-sufficient above, but worth a one-line confirmation since some books have multiple authors.
