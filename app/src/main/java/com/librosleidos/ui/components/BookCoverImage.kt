package com.librosleidos.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import coil3.compose.AsyncImage

/**
 * Task 3.5 (design D11). Renders a book's cover via Coil's [AsyncImage].
 * `coverUrl` is validated locally (blank or non-https) *before* it ever
 * reaches [AsyncImage] — Coil is not consulted at all for an invalid URL,
 * carrying forward the MVP's https-only threat-matrix rule
 * ([com.librosleidos.data.remote.GoogleBooksMetadataSource]'s
 * `toSecureCoverUrl`) as a second line of defense at render time. `null`
 * or an invalid `coverUrl` shows [CoverPlaceholder] instead.
 *
 * Spec: "Missing or failed cover image" (list), "Cover shown while
 * editing", "Missing cover in edit form" (edit form).
 */
@Composable
fun BookCoverImage(
    coverUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val secureCoverUrl = coverUrl.asSecureHttpsUrl()
    if (secureCoverUrl == null) {
        CoverPlaceholder(modifier = modifier)
    } else {
        AsyncImage(
            model = secureCoverUrl,
            contentDescription = contentDescription,
            modifier = modifier,
        )
    }
}

/** Pure logic: `null`, blank, or non-https becomes `null` (short-circuit before Coil). */
private fun String?.asSecureHttpsUrl(): String? =
    this?.takeIf { it.isNotBlank() && it.startsWith("https://") }

@Composable
private fun CoverPlaceholder(modifier: Modifier = Modifier) {
    Box(modifier = modifier.semantics { contentDescription = "No cover available" })
}
