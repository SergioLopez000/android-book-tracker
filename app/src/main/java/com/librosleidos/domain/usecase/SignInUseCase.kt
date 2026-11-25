package com.librosleidos.domain.usecase

import com.librosleidos.domain.model.SignInResult
import com.librosleidos.domain.port.AuthGateway
import com.librosleidos.domain.port.SessionStore
import kotlin.coroutines.cancellation.CancellationException

/**
 * Spec: "SignIn stores the session", "SignIn failure stores nothing". An
 * unexpected gateway exception collapses to [SignInResult.Failure] so no
 * exception detail can reach the UI.
 */
class SignInUseCase(private val gateway: AuthGateway, private val store: SessionStore) {

    suspend operator fun invoke(): SignInResult {
        val result = try {
            gateway.signIn()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (
            @Suppress("TooGenericExceptionCaught", "SwallowedException") unexpected: Exception
        ) {
            SignInResult.Failure
        }
        if (result is SignInResult.Success) store.save(result.token, result.account)
        return result
    }
}
