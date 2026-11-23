import { $, $$, browser } from "@wdio/globals";

export type Element = ReturnType<typeof $>;
export type Elements = ReturnType<typeof $$>;

const DEFAULT_TIMEOUT_MS = 20_000;
const TYPE_ATTEMPTS = 3;
const TYPE_SETTLE_MS = 3_000;
const REVEAL_SWIPES = 5;
const REVEAL_PERCENT = 0.6;

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

  /**
   * Scrolls the screen's scrollable container until `locate()` is on screen. Small screens (the CI emulator is
   * 320x640) leave lower fields off screen, and Compose only exposes on-screen nodes, so waiting alone times out.
   * Tries downwards first, then upwards; does nothing when the element is already shown or nothing scrolls.
   */
  protected async reveal(locate: () => Element): Promise<void> {
    if (await locate().isDisplayed()) return;
    // The soft keyboard covers the lower half of the container, so a swipe there would reach the keyboard
    // window instead of the form (SP4, run 45177627167). Close it before scrolling.
    await this.hideKeyboard();
    if (await locate().isDisplayed()) return;
    const container = $("android=new UiSelector().scrollable(true)");
    if (!(await container.isExisting())) return;
    for (const direction of ["down", "up"] as const) {
      for (let swipe = 0; swipe < REVEAL_SWIPES; swipe++) {
        const canScrollMore = await browser.execute("mobile: scrollGesture", {
          elementId: await container.elementId,
          direction,
          percent: REVEAL_PERCENT,
        });
        if (await locate().isDisplayed()) return;
        if (!canScrollMore) break;
      }
    }
  }

  protected async hideKeyboard(): Promise<void> {
    if (await browser.isKeyboardShown()) await browser.hideKeyboard();
  }

  protected async tap(element: Element, timeout = DEFAULT_TIMEOUT_MS): Promise<void> {
    await this.reveal(() => element);
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
    await this.reveal(locate);
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
