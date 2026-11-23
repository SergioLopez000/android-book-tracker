import { $, $$, browser } from "@wdio/globals";

export type Element = ReturnType<typeof $>;
export type Elements = ReturnType<typeof $$>;

const DEFAULT_TIMEOUT_MS = 20_000;
const TYPE_ATTEMPTS = 3;
const TYPE_SETTLE_MS = 3_000;

/** Shared locator and interaction helpers. Selector strings live only in Page Objects and support code. */
export abstract class BasePage {
  /** Element whose Compose test tag (exposed as resource id) is exactly `tag`. */
  protected byTag(tag: string): Element {
    return $(`android=new UiSelector().resourceId("${tag}")`);
  }

  /** Elements whose resource id matches the regex source `pattern`. */
  protected byTagPattern(pattern: string): Elements {
    return $$(`android=new UiSelector().resourceIdMatches("${pattern}")`);
  }

  protected async waitForVisible(element: Element, timeout = DEFAULT_TIMEOUT_MS): Promise<void> {
    await element.waitForDisplayed({ timeout });
  }

  protected async tap(element: Element, timeout = DEFAULT_TIMEOUT_MS): Promise<void> {
    await element.waitForDisplayed({ timeout });
    await element.waitForEnabled({ timeout });
    await element.click();
  }

  /**
   * Replaces the content of a text field with `text`. Compose fields sometimes keep the previous text when
   * cleared through the accessibility API (the new text was appended), and an element handle keeps reporting
   * the old text, so the field is looked up again for every check and the replacement is retried.
   */
  protected async typeText(locate: () => Element, text: string, timeout = DEFAULT_TIMEOUT_MS): Promise<void> {
    await locate().waitForDisplayed({ timeout });
    for (let attempt = 1; attempt <= TYPE_ATTEMPTS; attempt++) {
      const field = locate();
      await field.click();
      await field.clearValue();
      await field.setValue(text);
      const typed = await browser
        .waitUntil(async () => (await locate().getText()) === text, { timeout: TYPE_SETTLE_MS, interval: 250 })
        .catch(() => false);
      if (typed) return;
    }
    throw new Error(`The field did not end up with the typed text after ${TYPE_ATTEMPTS} attempts`);
  }
}
