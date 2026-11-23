import { driver } from "@wdio/globals";
import type { BookFixture } from "../data/books.ts";
import { BasePage, type Element } from "./base.page.ts";
import { bookEditTags } from "./tags.ts";

/** Width of one half-star tap target in dp (`StarRating`: 32 dp star, two halves). */
const RATING_TARGET_WIDTH_DP = 16;
const ANDROID_BASE_DPI = 160;

/** Book edit screen (add and edit share it). */
class BookEditPage extends BasePage {
  private get titleField(): Element {
    return this.byTag(bookEditTags.titleField);
  }

  private get authorField(): Element {
    return this.byTag(bookEditTags.authorField);
  }

  private get reviewField(): Element {
    return this.byTag(bookEditTags.reviewField);
  }

  private get saveButton(): Element {
    return this.byTag(bookEditTags.saveButton);
  }

  async typeTitle(title: string): Promise<void> {
    await this.typeText(() => this.titleField, title);
  }

  async typeAuthor(author: string): Promise<void> {
    await this.typeText(() => this.authorField, author);
  }

  async typeReview(review: string): Promise<void> {
    await this.typeText(() => this.reviewField, review);
  }

  /**
   * Sets the rating with a coordinate tap computed from the control geometry, never `element.click()` on a
   * rating tag: uiautomator reports the half-star bounds out of line with where they receive touches (SP2).
   * The row starts where the title field starts; each of the ten targets is 16 dp wide.
   */
  async setRating(value: number): Promise<void> {
    const targetIndex = Math.round(value * 2) - 1;
    if (targetIndex < 0 || targetIndex > 9) throw new Error(`Rating ${value} is outside 0.5..5.0`);
    await this.hideKeyboard();
    const dpi = Number(await driver.getDisplayDensity());
    const targetWidth = (RATING_TARGET_WIDTH_DP * dpi) / ANDROID_BASE_DPI;
    const rowLeft = (await this.titleField.getLocation()).x;
    const reference = this.byTag(bookEditTags.rating(value));
    const { y } = await reference.getLocation();
    const { height } = await reference.getSize();
    await driver.execute("mobile: clickGesture", {
      x: Math.round(rowLeft + (targetIndex + 0.5) * targetWidth),
      y: Math.round(y + height / 2),
    });
  }

  async save(): Promise<void> {
    await this.hideKeyboard();
    await this.tap(this.saveButton);
  }

  /** Fills every field (typing replaces the current text, so it also edits an opened book) and saves. */
  async fillAndSave(book: BookFixture): Promise<void> {
    await this.typeTitle(book.title);
    await this.typeAuthor(book.author);
    await this.typeReview(book.review);
    await this.setRating(book.rating);
    await this.save();
  }

  private async hideKeyboard(): Promise<void> {
    if (await driver.isKeyboardShown()) await driver.hideKeyboard();
  }
}

export const bookEdit = new BookEditPage();
