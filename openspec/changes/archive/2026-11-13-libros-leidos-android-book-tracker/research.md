# sdd/libros-leidos-android-book-tracker/research

**What**: External research for Android book-tracker app (metadata API + stack validation).
**Why**: User selected "Hacer research" during SDD pre-proposal gate; product decisions confirmed: local-only (Room), 5-star rating, metadata autofill via external API.

**Recommendation**: Open Library API as primary metadata source (no API key, JSON with title/author/covers in 3 sizes, courtesy rate limit 1 req/s anon / 3 req/s with descriptive User-Agent — plenty for single-user lookups). Google Books API as optional fallback only (requires API key, ~1000 queries/day estimated quota, unconfirmed officially).

**Stack validation (2026)**: Kotlin + Jetpack Compose + Room (KSP, not KAPT) + Coroutines/Flow + MVVM remains current best practice. Room 2.8.0+ raised its own minSdk floor to API 23 (consider minSdk 26 for broader modern API coverage). room-ktx merged into room-runtime. targetSdk 36 recommended (Play policy deadline Aug 31 2026, only relevant if published — app is local-only for now).

**Testing**: Robolectric now runs Compose UI tests on the JVM via the same ComposeTestRule APIs as instrumented tests, and can exercise Room/SQLite too — enables fast JVM-only tests for both domain (Room DAO/ViewModel) and Compose UI layers, matching Strict TDD Mode without needing an emulator for most cases. Reserve true instrumented tests for rendering/animation timing Robolectric can't emulate.

**Gaps/risks**: Google Books daily quota not officially confirmed (community sources only). Open Library formal license/attribution page not verified (courtesy User-Agent header requested). Robolectric+Compose test artifact versions must match to avoid flaky JVM UI tests — worth a small spike in design phase.

**Sources**: Open Library API docs (openlibrary.org/dev/docs/api/books, /covers), Google Books API v1 docs (developers.google.com/books/docs/v1/using), Android Room release notes (developer.android.com/jetpack/androidx/releases/room), Google Play target API requirements (support.google.com/googleplay), Robolectric blog on JVM local-testing (robolectric.org/blog/2026/07/03), Open Library rate limits (apis.io).

**Next**: sdd-propose using Open Library primary + Google Books fallback, Kotlin/Compose/Room(KSP)/MVVM, minSdk 26/targetSdk 36, Robolectric-based JVM test strategy.
