package com.librosleidos.domain.usecase

import app.cash.turbine.test
import com.librosleidos.domain.model.Account
import com.librosleidos.domain.model.IdToken
import com.librosleidos.domain.model.Session
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Spec: "Session observed". */
class ObserveSessionUseCaseTest {

    @Test
    fun `emits signed-out, signed-in with metadata, then signed-out`() = runTest {
        val store = FakeSessionStore()
        val account = Account("Ada", "ada@example.com")

        ObserveSessionUseCase(store)().test {
            assertEquals(Session.SignedOut, awaitItem())
            store.save(IdToken("t"), account)
            assertEquals(Session.SignedIn(account), awaitItem())
            store.clear()
            assertEquals(Session.SignedOut, awaitItem())
        }
    }
}
