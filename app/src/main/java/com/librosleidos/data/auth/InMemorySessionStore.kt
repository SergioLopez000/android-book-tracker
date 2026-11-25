package com.librosleidos.data.auth

import com.librosleidos.domain.model.Account
import com.librosleidos.domain.model.IdToken
import com.librosleidos.domain.model.Session
import com.librosleidos.domain.port.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Holds the ID token in process memory only and writes the account metadata
 * through to [metadata]. The session starts signed out regardless of what
 * [metadata] holds: a restored record is not a session (spec: "Metadata alone
 * is not a session", "Process restart requires sign-in again").
 */
class InMemorySessionStore(private val metadata: AccountMetadataStore) : SessionStore {

    private val state = MutableStateFlow<Session>(Session.SignedOut)

    @Volatile
    private var token: IdToken? = null

    override val session: StateFlow<Session> = state

    override fun currentToken(): IdToken? = token

    override suspend fun save(token: IdToken, account: Account) {
        metadata.save(account)
        this.token = token
        state.value = Session.SignedIn(account)
    }

    override suspend fun clear() {
        token = null
        state.value = Session.SignedOut
        metadata.clear()
    }
}
