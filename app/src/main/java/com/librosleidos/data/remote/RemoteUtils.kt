package com.librosleidos.data.remote

import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

/**
 * Shared plumbing for the Phase 4 adapters (REFACTOR: extracted from
 * duplicated code in [OpenLibraryMetadataSource] and [GoogleBooksMetadataSource]).
 */

/** Maps every element of a possibly-null [JSONArray] of objects; `null` maps to an empty list. */
internal inline fun <T> JSONArray?.mapObjects(transform: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    return (0 until length()).map { index -> transform(getJSONObject(index)) }
}

/** URL-encodes a search query for use in a query string. */
internal fun encodeQueryParam(query: String): String = URLEncoder.encode(query, "UTF-8")
