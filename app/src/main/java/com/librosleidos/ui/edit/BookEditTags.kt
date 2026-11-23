package com.librosleidos.ui.edit

/**
 * Stable test tags for the book edit screen (`<screen>_<element>[_<id>]`,
 * snake_case). Shared by the screen and, through `e2e/src/pages/tags.ts`, the
 * Appium suite. Renaming a tag is a breaking change for the e2e suite.
 */
object BookEditTags {
    const val SEARCH_FIELD = "book_edit_search_field"
    const val SEARCH_BUTTON = "book_edit_search_button"
    const val SEARCH_STATUS = "book_edit_search_status"
    const val TITLE_FIELD = "book_edit_title_field"
    const val AUTHOR_FIELD = "book_edit_author_field"
    const val REVIEW_FIELD = "book_edit_review_field"
    const val RATING_PREFIX = "book_edit_rating"
    const val CANCEL_BUTTON = "book_edit_cancel_button"
    const val SAVE_BUTTON = "book_edit_save_button"

    fun searchResult(index: Int): String = "book_edit_search_result_$index"

    fun error(field: BookEditField): String = "book_edit_error_${field.name.lowercase()}"
}
