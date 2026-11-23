package com.librosleidos.ui.list

import com.librosleidos.domain.model.DateRangeFilter

/**
 * Stable test tags for the book list screen and its delete dialog
 * (`<screen>_<element>[_<id>]`, snake_case). Shared by the screen, the
 * Robolectric tests and, through `e2e/src/pages/tags.ts`, the Appium suite.
 * Renaming a tag is a breaking change for the e2e suite.
 */
object BookListTags {
    const val ADD_BUTTON = "book_list_add_button"
    const val LOADING = "book_list_loading"
    const val EMPTY_STATE = "book_list_empty_state"
    const val LIST = "book_list"
    const val DELETE_DIALOG = "book_list_delete_dialog"
    const val DELETE_CONFIRM_BUTTON = "book_list_delete_confirm_button"
    const val DELETE_CANCEL_BUTTON = "book_list_delete_cancel_button"

    fun filter(filter: DateRangeFilter): String = "book_list_filter_${filter.name.lowercase()}"

    fun item(id: Long): String = "book_list_item_$id"

    fun removeButton(id: Long): String = "book_list_remove_button_$id"
}
