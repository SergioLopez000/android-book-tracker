package com.librosleidos.domain.port

import com.librosleidos.domain.model.SignInResult

/** Obtains a Google ID token; adapters live in `data/auth`. */
interface AuthGateway {
    suspend fun signIn(): SignInResult
    suspend fun signOut()
}
