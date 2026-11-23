import assert from "node:assert/strict";
import { test } from "node:test";
import {
  bookEditTags,
  bookListTags,
  LIST_ITEM_PATTERN,
  LIST_ITEM_SELECTOR_REGEX,
  ratingTagSuffix,
} from "./tags.ts";

test("per-item list tags embed the numeric id", () => {
  assert.equal(bookListTags.item(7), "book_list_item_7");
  assert.equal(bookListTags.removeButton(7), "book_list_remove_button_7");
});

test("ids 0 and 12345 build distinct tags", () => {
  assert.equal(bookListTags.item(0), "book_list_item_0");
  assert.equal(bookListTags.item(12345), "book_list_item_12345");
  assert.equal(bookListTags.removeButton(12345), "book_list_remove_button_12345");
});

test("rating tags use the locale-free suffix", () => {
  assert.equal(ratingTagSuffix(4.5), "4_5");
  assert.equal(ratingTagSuffix(5), "5_0");
  assert.equal(ratingTagSuffix(0.5), "0_5");
  assert.equal(bookEditTags.rating(4.5), "book_edit_rating_4_5");
  assert.equal(bookEditTags.rating(5), "book_edit_rating_5_0");
});

test("static tags mirror the Kotlin constants", () => {
  assert.equal(bookListTags.addButton, "book_list_add_button");
  assert.equal(bookListTags.emptyState, "book_list_empty_state");
  assert.equal(bookListTags.deleteConfirmButton, "book_list_delete_confirm_button");
  assert.equal(bookListTags.deleteCancelButton, "book_list_delete_cancel_button");
  assert.equal(bookEditTags.titleField, "book_edit_title_field");
  assert.equal(bookEditTags.saveButton, "book_edit_save_button");
});

test("the list item pattern captures numeric ids only", () => {
  assert.equal(LIST_ITEM_PATTERN.exec("book_list_item_42")?.[1], "42");
  assert.equal(LIST_ITEM_PATTERN.exec("book_list_item_0")?.[1], "0");
  assert.equal(LIST_ITEM_PATTERN.test("book_list_item_abc"), false);
  assert.equal(LIST_ITEM_PATTERN.test("book_list_item_"), false);
  assert.equal(LIST_ITEM_PATTERN.test("book_list_remove_button_7"), false);
  assert.equal(LIST_ITEM_PATTERN.test("xbook_list_item_7"), false);
});

test("the selector regex source is the pattern source with a single backslash", () => {
  assert.equal(LIST_ITEM_SELECTOR_REGEX, "^book_list_item_\\d+$");
});
