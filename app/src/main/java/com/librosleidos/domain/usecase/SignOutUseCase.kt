package com.librosleidos.domain.usecase

import com.librosleidos.domain.port.AuthGateway
import com.librosleidos.domain.port.SessionStore
import kotlin.coroutines.cancellation.CancellationException

/**
 * Spec: "Sign-out clears memory and storage", "Sign-out is idempotent".
 * Clears the store first; the gateway sign-out is best effort.
 */
class SignOutUseCase(private val gateway: AuthGateway, private val store: SessionStore) {

    suspend operator fun invoke() {
        store.clear()
        try {
            gateway.signOut()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (@Suppress("TooGenericExceptionCaught") ignored: Exception) {
            // Best effort: local state is already cleared.
        }
    }
}
