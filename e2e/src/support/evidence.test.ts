import assert from "node:assert/strict";
import { test } from "node:test";
import { attachFailureScreenshot, type EvidenceDeps } from "./evidence.ts";

const PNG_BASE64 = Buffer.from("fake-png-bytes").toString("base64");

type Attachment = { name: string; content: Buffer; type: string };

function fakeDeps(takeScreenshot: () => Promise<string>): { deps: EvidenceDeps; attachments: Attachment[] } {
  const attachments: Attachment[] = [];
  const deps: EvidenceDeps = {
    takeScreenshot,
    attach: (name, content, type) => {
      attachments.push({ name, content, type });
    },
  };
  return { deps, attachments };
}

test("a failed test attaches exactly one PNG screenshot", async () => {
  const { deps, attachments } = fakeDeps(async () => PNG_BASE64);
  await attachFailureScreenshot(false, deps);
  assert.equal(attachments.length, 1);
  assert.equal(attachments[0]?.type, "image/png");
  assert.equal(attachments[0]?.content.toString(), "fake-png-bytes");
});

test("a passed test attaches nothing and never takes a screenshot", async () => {
  let taken = 0;
  const { deps, attachments } = fakeDeps(async () => {
    taken += 1;
    return PNG_BASE64;
  });
  await attachFailureScreenshot(true, deps);
  assert.equal(attachments.length, 0);
  assert.equal(taken, 0);
});

test("a screenshot failure is swallowed and attaches nothing", async () => {
  const { deps, attachments } = fakeDeps(async () => {
    throw new Error("device gone");
  });
  await assert.doesNotReject(attachFailureScreenshot(false, deps));
  assert.equal(attachments.length, 0);
});
