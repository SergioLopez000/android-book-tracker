import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";

type PackageJson = {
  engines: { node: string };
  dependencies?: Record<string, string>;
  devDependencies?: Record<string, string>;
};

const e2eDir = new URL("../../", import.meta.url);
const pkg: PackageJson = JSON.parse(readFileSync(new URL("package.json", e2eDir), "utf8"));
const declared: Record<string, string> = { ...pkg.dependencies, ...pkg.devDependencies };

const EXACT_VERSION = /^\d+\.\d+\.\d+$/;

test("every dependency version is exact", () => {
  const entries = Object.entries(declared);
  assert.ok(entries.length > 0, "package.json declares no dependencies");
  for (const [name, version] of entries) {
    assert.match(version, EXACT_VERSION, `${name}@${version} is not an exact version`);
  }
});

test("Appium, driver and WebdriverIO pins match the spec", () => {
  assert.equal(declared["appium"], "3.9.1");
  assert.equal(declared["appium-uiautomator2-driver"], "8.7.3");
  assert.equal(declared["webdriverio"], "10.2.0");
});

test("every @wdio package is pinned to 10.2.0", () => {
  const wdioPackages = Object.entries(declared).filter(([name]) => name.startsWith("@wdio/"));
  const names = wdioPackages.map(([name]) => name);
  for (const required of [
    "@wdio/cli",
    "@wdio/local-runner",
    "@wdio/mocha-framework",
    "@wdio/spec-reporter",
    "@wdio/allure-reporter",
    "@wdio/appium-service",
  ]) {
    assert.ok(names.includes(required), `${required} is missing`);
  }
  for (const [name, version] of wdioPackages) {
    assert.equal(version, "10.2.0", `${name} is not 10.2.0`);
  }
});

test(".nvmrc is 24.22.3 and satisfies engines.node", () => {
  const nvmrc = readFileSync(new URL("../.nvmrc", e2eDir), "utf8").trim();
  assert.equal(nvmrc, "24.22.3");
  const [major, minor, patch] = nvmrc.split(".").map(Number);
  assert.equal(pkg.engines.node, ">=24.22.1 <25");
  assert.ok(major === 24 && ((minor ?? 0) > 22 || ((minor ?? 0) === 22 && (patch ?? 0) >= 1)));
});
