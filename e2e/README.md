# Libros Leidos end-to-end suite

Appium (UiAutomator2) and WebdriverIO tests that drive the debug APK on an Android emulator through Page Objects. Test data is synthetic and the suite needs no network.

## Setup

1. Use the pinned Node version: `nvm install && nvm use` (reads `/.nvmrc`, currently 24.22.3).
2. Install the exact locked dependencies: `cd e2e && npm ci`. The Appium driver is a local dependency; `wdio.conf.ts` sets `APPIUM_HOME` so Appium finds it. Nothing is downloaded at run time.
3. Build the debug APK from the repository root: `./gradlew :app:assembleDebug`. Only debug builds expose the test tags as resource ids.
4. Start an emulator (or connect a device) with `ANDROID_HOME` set, for example `emulator -avd <name> -no-window -no-audio`.

## Run

```
cd e2e
npm run e2e          # happy path and cancel flow
npm run test:unit    # fast node:test suites (pins, config, tags, structure, evidence)
npm run typecheck    # tsc --noEmit
```

`E2E_APK_PATH=/path/to/app.apk npm run e2e` installs that APK instead of `app/build/outputs/apk/debug/app-debug.apk`. The app data is cleared before every test. To prove the suite works offline, run `adb shell cmd connectivity airplane-mode enable` first.

Results go to `e2e/allure-results` (JSON). A screenshot is attached only to failed tests. Render them with Allure 2.46.2: `allure generate e2e/allure-results -o e2e/allure-report --clean`.

## Layout

- `src/pages`: Page Objects and `tags.ts`, the mirror of the Kotlin test tags. Selectors live only here and in `src/support`.
- `src/tests`: specs (`*.e2e.ts`), written against Page Objects only.
- `src/support`, `src/data`: config, failure evidence and synthetic fixtures.

## Notes

- The rating is set with a coordinate tap computed from the control geometry, never `click()` on a `book_edit_rating_*` tag: uiautomator reports half-star bounds out of line with where they receive touches.
- Pre-commit: lefthook runs `tsc --noEmit` when staged files match `e2e/**/*.ts`. If it reports missing dependencies, run `cd e2e && npm ci`.
