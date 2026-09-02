package com.librosleidos.domain.model

/**
 * A candidate title/author/cover result returned by a [BookMetadataSource]
 * (design: `book-metadata-lookup`). Used to prefill, never lock, the entry
 * form's editable fields.
 */
data class BookMetadata(
    val title: String,
    val author: String,
    val coverUrl: String?,
)
