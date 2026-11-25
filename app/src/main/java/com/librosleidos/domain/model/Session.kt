package com.librosleidos.domain.model

sealed interface Session {
    data object SignedOut : Session
    data class SignedIn(val account: Account) : Session
}

sealed interface SignInResult {
    data class Success(val token: IdToken, val account: Account) : SignInResult
    data object Cancelled : SignInResult
    data object NoCredential : SignInResult
    data object NotConfigured : SignInResult
    data object Failure : SignInResult
}
