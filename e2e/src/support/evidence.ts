/** What the evidence policy needs from the session and the reporter (injectable for unit tests). */
export type EvidenceDeps = {
  /** Base64-encoded PNG of the current screen. */
  takeScreenshot: () => Promise<string>;
  attach: (name: string, content: Buffer, type: string) => void;
};

const SCREENSHOT_NAME = "Screenshot on failure";
const PNG_MIME_TYPE = "image/png";

/**
 * Failure evidence policy: a screenshot is attached only when the test failed (no page source, no
 * attachment for passing tests). Never throws, so it cannot hide the original test failure.
 */
export async function attachFailureScreenshot(passed: boolean, deps: EvidenceDeps): Promise<void> {
  if (passed) return;
  try {
    const screenshot = await deps.takeScreenshot();
    deps.attach(SCREENSHOT_NAME, Buffer.from(screenshot, "base64"), PNG_MIME_TYPE);
  } catch (error) {
    console.error("Could not attach the failure screenshot:", error);
  }
}
