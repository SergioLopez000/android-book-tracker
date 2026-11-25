package com.librosleidos.domain.usecase

import com.librosleidos.domain.model.Account
import com.librosleidos.domain.model.IdToken
import com.librosleidos.domain.model.Session
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec: "Sign-out clears memory and storage", "Sign-out is idempotent". */
class SignOutUseCaseTest {

    private val gateway = FakeAuthGateway()
    private val store = FakeSessionStore().also { gateway.storeProbe = it }
    private val useCase = SignOutUseCase(gateway, store)

    private suspend fun signIn() = store.save(IdToken("t"), Account("Ada", "ada@example.com"))

    @Test
    fun `clears the store before calling the gateway`() = runTest {
        signIn()

        useCase()

        assertNull(store.currentToken())
        assertEquals(Session.SignedOut, store.session.value)
        assertEquals(1, gateway.signOutCalls)
        assertEquals(true, gateway.storeWasClearedAtSignOut)
    }

    @Test
    fun `is idempotent when already signed out`() = runTest {
        useCase()
        useCase()

        assertEquals(Session.SignedOut, store.session.value)
        assertEquals(2, gateway.signOutCalls)
    }

    @Test
    fun `still clears the store when the gateway throws`() = runTest {
        signIn()
        gateway.signOutFailure = IllegalStateException("gateway down")

        useCase()

        assertTrue(store.session.value is Session.SignedOut)
        assertNull(store.currentToken())
    }
}
