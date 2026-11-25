package com.librosleidos.data.auth

import com.librosleidos.domain.model.Account

/**
 * Persistent, non-sensitive account metadata. It deliberately has no token
 * parameter anywhere: the ID token never reaches persistent storage.
 */
interface AccountMetadataStore {
    suspend fun read(): Account?
    suspend fun save(account: Account)
    suspend fun clear()
}
