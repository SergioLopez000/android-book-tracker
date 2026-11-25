package com.librosleidos.domain.port

import com.librosleidos.domain.model.Account
import com.librosleidos.domain.model.IdToken
import com.librosleidos.domain.model.Session
import kotlinx.coroutines.flow.StateFlow

/** Process-memory session; the token is never persisted. */
interface SessionStore {
    val session: StateFlow<Session>
    fun currentToken(): IdToken?
    suspend fun save(token: IdToken, account: Account)
    suspend fun clear()
}
