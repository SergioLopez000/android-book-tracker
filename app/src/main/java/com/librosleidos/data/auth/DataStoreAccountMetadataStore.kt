package com.librosleidos.data.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.librosleidos.domain.model.Account
import kotlinx.coroutines.flow.first

/** Display name and email only; see [AccountMetadataStore]. */
class DataStoreAccountMetadataStore(private val dataStore: DataStore<Preferences>) :
    AccountMetadataStore {

    override suspend fun read(): Account? {
        val prefs = dataStore.data.first()
        val name = prefs[DISPLAY_NAME]
        val email = prefs[EMAIL]
        return if (name != null &&
            email != null
        ) {
            Account(displayName = name, email = email)
        } else {
            null
        }
    }

    override suspend fun save(account: Account) {
        dataStore.edit {
            it[DISPLAY_NAME] = account.displayName
            it[EMAIL] = account.email
        }
    }

    override suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    companion object {
        /** Stored under `files/datastore/`; the backup rules exclude this exact path. */
        const val FILE_NAME = "account_metadata.preferences_pb"
        private val DISPLAY_NAME = stringPreferencesKey("display_name")
        private val EMAIL = stringPreferencesKey("email")
    }
}
