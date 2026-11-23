import assert from "node:assert/strict";
import { existsSync, readdirSync, readFileSync } from "node:fs";
import { join, relative, sep } from "node:path";
import { test } from "node:test";

const e2eDir = new URL("../", import.meta.url).pathname;
const srcDir = join(e2eDir, "src");

function listTsFiles(dir: string): string[] {
  return readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const path = join(dir, entry.name);
    if (entry.isDirectory()) return listTsFiles(path);
    return entry.name.endsWith(".ts") ? [path] : [];
  });
}

const SELECTOR_MARKERS = ["UiSelector", "resourceId(", "book_list_", "book_edit_"];
const ALLOWED_DIRS = [join(srcDir, "pages"), join(srcDir, "support")];

test("both Page Objects exist", () => {
  assert.ok(existsSync(join(srcDir, "pages", "bookList.page.ts")), "bookList.page.ts is missing");
  assert.ok(existsSync(join(srcDir, "pages", "bookEdit.page.ts")), "bookEdit.page.ts is missing");
});

test("selector strings appear only under src/pages and src/support", () => {
  const files = [...listTsFiles(srcDir), join(e2eDir, "wdio.conf.ts")].filter(existsSync);
  const outside = files.filter(
    (file) =>
      !file.endsWith(".test.ts") && !ALLOWED_DIRS.some((dir) => file.startsWith(dir + sep)),
  );
  assert.ok(outside.length > 0, "no files outside pages/support were scanned");
  for (const file of outside) {
    const content = readFileSync(file, "utf8");
    for (const marker of SELECTOR_MARKERS) {
      assert.ok(
        !content.includes(marker),
        `${relative(e2eDir, file)} contains selector marker "${marker}"`,
      );
    }
  }
});

test("e2e test files exist and are scanned", () => {
  const specs = listTsFiles(join(srcDir, "tests"));
  assert.ok(specs.length > 0, "no e2e spec files found under src/tests");
});
