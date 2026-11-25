package com.librosleidos.domain.usecase

import com.librosleidos.domain.model.Account
import com.librosleidos.domain.model.IdToken
import com.librosleidos.domain.model.Session
import com.librosleidos.domain.model.SignInResult
import com.librosleidos.domain.port.AuthGateway
import com.librosleidos.domain.port.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeAuthGateway(
    var result: SignInResult = SignInResult.Failure,
    var signOutFailure: Exception? = null
) : AuthGateway {
    var signOutCalls = 0
    var storeWasClearedAtSignOut: Boolean? = null
    var storeProbe: SessionStore? = null

    override suspend fun signIn(): SignInResult = result

    override suspend fun signOut() {
        signOutCalls++
        storeWasClearedAtSignOut = storeProbe?.currentToken() == null
        signOutFailure?.let { throw it }
    }
}

class FakeSessionStore : SessionStore {
    private val state = MutableStateFlow<Session>(Session.SignedOut)
    private var token: IdToken? = null
    var saveCalls = 0

    override val session: StateFlow<Session> = state

    override fun currentToken(): IdToken? = token

    override suspend fun save(token: IdToken, account: Account) {
        saveCalls++
        this.token = token
        state.value = Session.SignedIn(account)
    }

    override suspend fun clear() {
        token = null
        state.value = Session.SignedOut
    }
}
