package com.librosleidos.domain.usecase

import com.librosleidos.domain.model.Account
import com.librosleidos.domain.model.IdToken
import com.librosleidos.domain.model.Session
import com.librosleidos.domain.model.SignInResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Spec: "SignIn stores the session", "SignIn failure stores nothing". */
class SignInUseCaseTest {

    private val account = Account(displayName = "Ada", email = "ada@example.com")
    private val gateway = FakeAuthGateway()
    private val store = FakeSessionStore()
    private val useCase = SignInUseCase(gateway, store)

    @Test
    fun `success stores the token and account`() = runTest {
        val token = IdToken("secret-token")
        gateway.result = SignInResult.Success(token, account)

        val result = useCase()

        assertEquals(SignInResult.Success(token, account), result)
        assertEquals(token, store.currentToken())
        assertEquals(Session.SignedIn(account), store.session.value)
    }

    @Test
    fun `every failure result leaves the store unchanged`() = runTest {
        listOf(
            SignInResult.Cancelled,
            SignInResult.NoCredential,
            SignInResult.NotConfigured,
            SignInResult.Failure
        ).forEach { failure ->
            gateway.result = failure

            assertEquals(failure, useCase())
            assertNull(store.currentToken())
            assertEquals(Session.SignedOut, store.session.value)
            assertEquals(0, store.saveCalls)
        }
    }

    @Test
    fun `a throwing gateway is reported as Failure and stores nothing`() = runTest {
        val throwing = object : com.librosleidos.domain.port.AuthGateway {
            override suspend fun signIn(): SignInResult = error("boom with details")
            override suspend fun signOut() = Unit
        }

        val result = SignInUseCase(throwing, store)()

        assertEquals(SignInResult.Failure, result)
        assertEquals(Session.SignedOut, store.session.value)
    }
}
