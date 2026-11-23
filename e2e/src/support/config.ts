import { fileURLToPath } from "node:url";

/** Application id of the app under test (debug builds keep the release id). */
export const APP_ID = "com.sergiolopez.rincondelectura";

/** Environment variable that overrides the APK installed by the suite. */
export const APK_PATH_ENV = "E2E_APK_PATH";

/** Default APK: the debug build produced by `./gradlew :app:assembleDebug`, resolved from the repository root. */
export const DEFAULT_APK_PATH = fileURLToPath(
  new URL("../../../app/build/outputs/apk/debug/app-debug.apk", import.meta.url),
);

/** Returns the APK to install: `E2E_APK_PATH` when set and non-empty, the default debug APK otherwise. */
export function resolveApkPath(env: Record<string, string | undefined>): string {
  const override = env[APK_PATH_ENV];
  return override !== undefined && override !== "" ? override : DEFAULT_APK_PATH;
}
