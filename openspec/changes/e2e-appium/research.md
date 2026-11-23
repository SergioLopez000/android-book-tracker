# Research: e2e-appium (#7)

```yaml
schema: gentle-ai.sdd-research/v1
revision: 2
request_id: e2e-appium-research-1
intent_revision: 1
change: e2e-appium
accessed_at: 2026-11-23
outcome: done
admission:
  documentation: granted [WebFetch]
  open-web: granted [WebSearch, WebFetch]
observed_use: >
  Revision 1 (sdd-research sub-agent): WebFetch x14, WebSearch x4, outcome partial.
  Revision 2 (orchestrator follow-up closing the revision 1 gaps): npm registry metadata
  (npm view) and inspection of the compiled Compose UI 1.12.1 artifact from the local
  Gradle cache (javap).
```

## Summary

- **Appium line.** WebdriverIO 10 documents Appium 3 only: `appium@^3`, and "WDIO 10 requires Appium 3". Appium 4.0.0 (2026-11-15) and appium-uiautomator2-driver 10.0.0 (2026-11-22) are very new and are not mentioned by WDIO. The evidence supports pinning **Appium 3.9.1 + driver 8.7.3 + WDIO 10.2.0**:
  - Appium 3.9.1 was released 2026-11-14, so the 3.x line is still being released.
  - Driver 8.7.3 (2026-10-28) declares the peer `appium ^3.0.0-rc.2`.
  - Driver 9.0.0 and later require Appium 4.
- **Allure.** The allure-js 3.x docs say their results render with either Allure Report 2 (`allure generate`) or Allure Report 3. `@wdio/allure-reporter` 10.2.0 depends on `allure-js-commons ^3.10.0`. Screenshot rendering on CLI 2.46.2 specifically is left to a smoke spike.
- **Compose.** `testTagsAsResourceId` has needed no opt-in since Compose 1.2.0. The compiled Compose UI 1.12.1 (the version this repo resolves) has no `ExperimentalComposeUiApi` reference on it. The official guidance is to set it once, high in the hierarchy. Dialogs are separate windows, and community evidence says the flag must be set again inside the dialog.
- **Emulator.** `android-emulator-runner` v2.38.0 is the latest release. The README gives the KVM udev step, `x86_64`, AVD caching and headless options.

## Sources

| ID | Class | Title | Publisher | URL | Accessed | Excerpt |
|---|---|---|---|---|---|---|
| S1 | documentation | Appium releases; "Migrating to Appium 4" | appium (GitHub, appium.io) | https://github.com/appium/appium/releases ; https://appium.io/docs/en/latest/guides/migrating-3-to-4/ | 2026-11-23 | Node `^22.22.2 \|\| ^24.15.0 \|\| >=26.0.0`; ESM-only; JSONWP/MJSONWP removed; default log level info |
| S2 | documentation | appium-uiautomator2-driver releases | appium (GitHub) | https://github.com/appium/appium-uiautomator2-driver/releases | 2026-11-23 | 10.0.0 (22 Nov): ChromeDriver SHA-256 allowlist (breaking). 9.0.0 (1 Nov): "requires Appium 4.0.0-beta.0 or later and drops Node 20" |
| S3 | documentation | WebdriverIO releases | webdriverio (GitHub) | https://github.com/webdriverio/webdriverio/releases | 2026-11-23 | v10.0.0 "require Appium 3 and drop Appium 1/2 HTTP fallbacks" (#15709); v10.2.0 Node 22.23.4 / 24.22.1 / 26.12.1 (#16211) |
| S4 | documentation | WebdriverIO Appium Service | webdriver.io | https://webdriver.io/docs/appium-service/ | 2026-11-23 | "WebdriverIO 10 requires Appium 3 ... refuses to launch Appium 1.x or 2.x"; `npm install @wdio/appium-service appium@^3 --save-dev` |
| S5 | open-web | testTagsAsResourceId and Popup/AlertDialog | Kotlin Slack archive; Codeberg | https://slack-chats.kotlinlang.org/t/15970128/is-it-expected-that-testtagsasresourceid-semantics-does-not-?t=v2 ; https://codeberg.org/FlashProtestFoundation/FlashProtestApp/issues/2 | 2026-11-23 | AlertDialog's separate window does not inherit `testTagsAsResourceId`; fix by applying it in the dialog (community) |
| S6 | documentation | allure-js-commons README; Allure WebdriverIO docs | allure-framework; allurereport.org | https://unpkg.com/allure-js-commons@3.10.2/README.md ; https://allurereport.org/docs/webdriverio/ | 2026-11-23 | Results in `./allure-results` render "with either report generator" (Allure 2 `allure generate`, Allure 3 `npx allure`); `addAttachment()` supports PNG |
| S7 | documentation | Compose testing interoperability (UiAutomator); Compose UI releases | developer.android.com | https://developer.android.com/develop/ui/compose/testing/interoperability ; https://developer.android.com/jetpack/androidx/releases/compose-ui | 2026-11-23 | Available since 1.2.0-alpha08; enable "only once high in your composables hierarchy"; `By.res(resourceName)`; latest stable ui 1.13.0 (2026-11-18) |
| S8 | documentation | android-emulator-runner README and releases | ReactiveCircus (GitHub) | https://github.com/ReactiveCircus/android-emulator-runner | 2026-11-23 | v2.38.0 latest (build-tools 37.0.0); KVM udev step; `arch: x86_64`; AVD cache `~/.android/avd/*`, `~/.android/adb*`; `-no-window -gpu swiftshader_indirect -noaudio -no-boot-anim -camera-back none` |
| S9 | open-web | npm registry metadata (`npm view`) | npmjs.com | https://registry.npmjs.org/appium ; https://registry.npmjs.org/appium-uiautomator2-driver ; https://registry.npmjs.org/@wdio/appium-service ; https://registry.npmjs.org/@wdio/allure-reporter | 2026-11-23 | appium dist-tags latest 4.0.0; 3.9.0 2026-10-27, 3.9.1 2026-11-14. Driver@8 latest 8.7.3 (2026-10-28), peer `appium ^3.0.0-rc.2`, node `^20.19.0 \|\| ^22.12.0 \|\| >=24.0.0`. @wdio/appium-service 10.2.0 peer `appium >=3` (optional), node `>=22.23.4 <23 \|\| >=24.22.1 <25 \|\| >=26.12.1`. @wdio/allure-reporter 10.2.0 depends on `allure-js-commons ^3.10.0` |
| S10 | documentation | Compiled androidx.compose.ui:ui-android 1.12.1 (`classes.jar`), inspected with `javap -v` | Google (AndroidX, Maven) | https://maven.google.com/web/index.html#androidx.compose.ui:ui-android:1.12.1 | 2026-11-23 | `SemanticsPropertiesAndroid` and `SemanticsProperties_androidKt` (the classes that define and set `TestTagsAsResourceId`) contain 0 references to `ExperimentalComposeUiApi` |

## Validated Claims

**Q1. Appium, driver and WDIO**

- C1. Appium 4.0.0 was released 2026-11-15. Its Node requirement is `^22.22.2 || ^24.15.0 || >=26.0.0`, and Node 20 is dropped. [S1]
- C2. Appium 4 breaking changes:
  - ESM-only packages.
  - JSONWP/MJSONWP and 12 obsolete endpoints removed.
  - `--allow-cors` replaced by the `cors` insecure feature.
  - Default log level `info`; output split between stdout and stderr.
  - `Origin` requests get 403 unless CORS is enabled.
  - `@appium/types` 2.0.0 removes capabilities such as `appium:autoLaunch`. [S1]
- C3. Driver releases [S2, S9]:
  - 9.0.0 (2026-11-01) requires Appium `>=4.0.0-beta.0` and drops Node 20.
  - 10.0.0 (2026-11-22) adds a breaking ChromeDriver/Edge SHA-256 allowlist.
  - 8.7.3 (2026-10-28) is the latest 8.x, with peer `appium ^3.0.0-rc.2`.
- C4. WDIO 10 requires Appium 3, and its Appium service docs install `appium@^3`. [S3, S4] `@wdio/appium-service` 10.2.0 declares an optional peer `appium >=3`, which does not exclude 4, but no WDIO doc mentions Appium 4. [S9]
- C5. The Appium 3 line is still being released: 3.9.0 came out 2026-10-27 and 3.9.1 on 2026-11-14. [S9]
- C6. These Node versions satisfy both stacks [S1, S3, S9]:
  - Node 22 >= 22.23.4 or Node 24 >= 24.22.1 satisfies WDIO 10.2.0, Appium 4 and driver 8.7.3.
  - The driver 8.7.3 engine range `^22.12.0 || >=24.0.0` is also satisfied.
- C7. The only documented pairing is WDIO 10.x + Appium 3.x + driver 8.x. [S2, S4, S9]

**Q2. Allure**

- C8. Results produced by allure-js 3.x integrations render with Allure Report 2 (`allure generate ./allure-results`) or Allure Report 3. [S6]
- C9. `@wdio/allure-reporter` 10.2.0 depends on `allure-js-commons ^3.10.0`, so it is one of those integrations. Attachments, including PNG, are supported through `addAttachment`. [S6, S9]

**Q3. Compose `testTagsAsResourceId`**

- C10. UiAutomator sees `Modifier.testTag` as a resource-id only when an ancestor sets `testTagsAsResourceId = true`. Set it once, high in the hierarchy. It has existed since 1.2.0. [S7]
- C11. With UiAutomator2, the resource-id is the bare tag: use `By.res(tag)`, not `By.res(pkg, id)`. [S7]
- C12. It needs no opt-in. Compose UI 1.12.1, which BOM 2026.09.00 in this repo resolves, has 0 `ExperimentalComposeUiApi` references in the classes that define and set it. [S10] The latest stable is 1.13.0 (2026-11-18). [S7]
- C13. An `AlertDialog` or `Popup` is a separate window whose semantics do not inherit the flag, so it must be set inside the dialog content. This is community evidence. [S5]

**Q4. Emulator runner**

- C14. v2.38.0 is the latest release, adding build-tools 37.0.0. Earlier v2.33.0 fixes covered ubuntu-24.04 SDK/AVD paths. [S8]
- C15. The README's recommended setup [S8]:
  - A KVM udev step before the action (`99-kvm4all.rules`, `udevadm control --reload-rules`, `udevadm trigger --name-match=kvm`).
  - `arch: x86_64`.
  - AVD caching on `~/.android/avd/*` and `~/.android/adb*`, keyed by API level. On a cache miss, generate a snapshot; otherwise use `no-snapshot-save`.
  - Headless `emulator-options`.

## Contradictions

- The npm `latest` tag (Appium 4.0.0, driver 10.0.0) is not the combination WDIO documents (Appium 3). [S3, S4] vs [S1, S2, S9]
- `@wdio/appium-service` 10.2.0 accepts `appium >=3` as a peer [S9], while its docs install `appium@^3` [S4]. The package does not block Appium 4, but its docs do not support it.
- GitHub release pages omit the year. Dates were confirmed against changelog headers and npm `time` metadata. [S1, S2, S9]

## Uncertainty and Freshness

- Appium 3 has no published end-of-support date. The 3.9.1 release on 2026-11-14 shows it is still maintained today. [S9]
- Allure CLI 2.46.2 rendering `@wdio/allure-reporter` 10.2.0 screenshots is documented in general [S6] but not tested here. A local or CI smoke run is planned as a design spike.
- Dialog propagation rests on community sources only [S5]. Verify it with a UiAutomator page-source dump in the spike.
- API 34/35/36 system-image availability and boot time on `ubuntu-24.04` were not measured. [S8]
- Freshness: all sources accessed 2026-11-23. The Appium 4 and driver 9/10 lines are under 4 weeks old and may move fast.

## Product Choices (separate, non-authoritative)

- **Option A (evidence-backed):** Appium 3.9.1 + appium-uiautomator2-driver 8.7.3 + WDIO 10.2.0, pinned exactly. This is the documented pairing. Revisit Appium 4 when WDIO documents it.
- **Option B:** Appium 4.0.0 + driver 10.0.0 + WDIO 10.2.0. These are the latest versions but off the documented path, and they need a local proof run.
- **Allure:** keep CI Allure CLI 2.46.2 if the smoke spike renders screenshots; otherwise move the report job to Allure 3 (spec escape hatch).
- **Compose:** set `testTagsAsResourceId` at the `MainActivity` root and inside the delete dialog content, both through the debug-only modifier.
- **CI:** KVM step, `x86_64`, and an AVD cache built on `dev`; the API level is chosen in design.
