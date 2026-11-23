import { $ } from "@wdio/globals";
import { BasePage, type Element } from "./base.page.ts";
import { bookListTags, LIST_ITEM_PATTERN, LIST_ITEM_SELECTOR_REGEX } from "./tags.ts";

const ITEM_TIMEOUT_MS = 20_000;

/** Escapes a value for use inside a double-quoted UiSelector string. */
function quote(value: string): string {
  return value.replaceAll("\\", "\\\\").replaceAll('"', '\\"');
}

/** Book list screen and its delete confirmation dialog. */
class BookListPage extends BasePage {
  private get addButton(): Element {
    return this.byTag(bookListTags.addButton);
  }

  private get emptyState(): Element {
    return this.byTag(bookListTags.emptyState);
  }

  private get deleteDialog(): Element {
    return this.byTag(bookListTags.deleteDialog);
  }

  private get confirmButton(): Element {
    return this.byTag(bookListTags.deleteConfirmButton);
  }

  private get cancelButton(): Element {
    return this.byTag(bookListTags.deleteCancelButton);
  }

  /** The list item card whose title is `title`. The title is on a child node of the tagged item (SP2). */
  private itemTitle(title: string): Element {
    return $(
      `android=new UiSelector().resourceIdMatches("${LIST_ITEM_SELECTOR_REGEX}")` +
        `.childSelector(new UiSelector().text("${quote(title)}"))`,
    );
  }

  /** Id of the book shown with `title`: the numeric suffix of its `book_list_item_<id>` resource id. */
  async itemIdByTitle(title: string): Promise<number> {
    await this.waitForVisible(this.itemTitle(title), ITEM_TIMEOUT_MS);
    for (const item of await this.byTagPattern(LIST_ITEM_SELECTOR_REGEX)) {
      const hasTitle = await item.$(`android=new UiSelector().text("${quote(title)}")`).isExisting();
      if (hasTitle) {
        const match = LIST_ITEM_PATTERN.exec((await item.getAttribute("resource-id")) ?? "");
        if (match?.[1] !== undefined) return Number(match[1]);
      }
    }
    throw new Error(`No list item with a numeric id shows the title "${title}"`);
  }

  async tapAdd(): Promise<void> {
    await this.tap(this.addButton);
  }

  async openBook(title: string): Promise<void> {
    await this.tap(this.itemTitle(title));
  }

  async requestRemove(title: string): Promise<void> {
    const id = await this.itemIdByTitle(title);
    await this.tap(this.byTag(bookListTags.removeButton(id)));
  }

  async confirmDelete(): Promise<void> {
    await this.tap(this.confirmButton);
  }

  async cancelDelete(): Promise<void> {
    await this.tap(this.cancelButton);
  }

  async assertEmptyState(): Promise<void> {
    await this.waitForVisible(this.emptyState);
  }

  async assertBookShown(title: string): Promise<void> {
    await this.waitForVisible(this.itemTitle(title));
  }

  async assertBookAbsent(title: string): Promise<void> {
    await this.itemTitle(title).waitForExist({ timeout: ITEM_TIMEOUT_MS, reverse: true });
  }

  /** The list shows the saved rating through the content description "Rating <value> out of 5". */
  async assertBookRating(title: string, rating: number): Promise<void> {
    const id = await this.itemIdByTitle(title);
    const description = `Rating ${rating.toFixed(1)} out of 5`;
    const shown = this.byTag(bookListTags.item(id)).$(
      `android=new UiSelector().description("${description}")`,
    );
    await expect(shown).toExist();
  }

  async assertDeleteDialogOpen(): Promise<void> {
    await this.waitForVisible(this.deleteDialog);
    await this.waitForVisible(this.confirmButton);
    await this.waitForVisible(this.cancelButton);
  }

  async assertDeleteDialogClosed(): Promise<void> {
    await this.deleteDialog.waitForExist({ timeout: ITEM_TIMEOUT_MS, reverse: true });
  }
}

export const bookList = new BookListPage();
