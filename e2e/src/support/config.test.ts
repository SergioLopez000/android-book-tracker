import assert from "node:assert/strict";
import { isAbsolute, join } from "node:path";
import { test } from "node:test";
import { APP_ID, DEFAULT_APK_PATH, resolveApkPath } from "./config.ts";

test("resolveApkPath falls back to the debug APK in the repository", () => {
  const apk = resolveApkPath({});
  assert.ok(isAbsolute(apk), `${apk} is not absolute`);
  assert.ok(
    apk.endsWith(join("app", "build", "outputs", "apk", "debug", "app-debug.apk")),
    `${apk} is not the default debug APK`,
  );
  assert.equal(apk, DEFAULT_APK_PATH);
});

test("E2E_APK_PATH overrides the default APK", () => {
  assert.equal(resolveApkPath({ E2E_APK_PATH: "/tmp/custom/app.apk" }), "/tmp/custom/app.apk");
});

test("an empty E2E_APK_PATH falls back to the default", () => {
  assert.equal(resolveApkPath({ E2E_APK_PATH: "" }), DEFAULT_APK_PATH);
});

test("APP_ID is the application id of the app", () => {
  assert.equal(APP_ID, "com.sergiolopez.rincondelectura");
});
