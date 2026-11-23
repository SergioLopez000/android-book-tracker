/**
 * Mirror of the Kotlin test tags (`BookListTags.kt`, `BookEditTags.kt`).
 * Robolectric pins the Kotlin side; the e2e run pins this side. Renaming a tag
 * in the app is a breaking change for this suite.
 */

/** Locale-free rating suffix: 4.5 -> "4_5", 5 -> "5_0" (mirror of `ratingTagSuffix`). */
export function ratingTagSuffix(value: number): string {
  return value.toFixed(1).replace(".", "_");
}

/** Matches a list item tag and captures its numeric id. */
export const LIST_ITEM_PATTERN = /^book_list_item_(\d+)$/;

/** The same pattern as a UiAutomator `resourceIdMatches` argument (UiSelector takes the regex source as is: one backslash). */
export const LIST_ITEM_SELECTOR_REGEX = String.raw`^book_list_item_\d+$`;

export const bookListTags = {
  addButton: "book_list_add_button",
  loading: "book_list_loading",
  emptyState: "book_list_empty_state",
  list: "book_list",
  deleteDialog: "book_list_delete_dialog",
  deleteConfirmButton: "book_list_delete_confirm_button",
  deleteCancelButton: "book_list_delete_cancel_button",
  item: (id: number): string => `book_list_item_${id}`,
  removeButton: (id: number): string => `book_list_remove_button_${id}`,
} as const;

export const bookEditTags = {
  searchField: "book_edit_search_field",
  searchButton: "book_edit_search_button",
  titleField: "book_edit_title_field",
  authorField: "book_edit_author_field",
  reviewField: "book_edit_review_field",
  cancelButton: "book_edit_cancel_button",
  saveButton: "book_edit_save_button",
  rating: (value: number): string => `book_edit_rating_${ratingTagSuffix(value)}`,
} as const;
