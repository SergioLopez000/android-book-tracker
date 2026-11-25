package com.librosleidos.data.auth

import app.cash.turbine.test
import com.librosleidos.domain.model.Account
import com.librosleidos.domain.model.IdToken
import com.librosleidos.domain.model.Session
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FakeAccountMetadataStore(var stored: Account? = null) : AccountMetadataStore {
    override suspend fun read(): Account? = stored
    override suspend fun save(account: Account) {
        stored = account
    }
    override suspend fun clear() {
        stored = null
    }
}

/** Spec: "Token read through the session", "Metadata alone is not a session". */
class InMemorySessionStoreTest {

    private val account = Account("Ada", "ada@example.com")
    private val token = IdToken("secret-token")

    @Test
    fun `starts signed out`() {
        val store = InMemorySessionStore(FakeAccountMetadataStore())

        assertEquals(Session.SignedOut, store.session.value)
        assertNull(store.currentToken())
    }

    @Test
    fun `save then clear emits signed-out, signed-in, signed-out`() = runTest {
        val store = InMemorySessionStore(FakeAccountMetadataStore())

        store.session.test {
            assertEquals(Session.SignedOut, awaitItem())
            store.save(token, account)
            assertEquals(Session.SignedIn(account), awaitItem())
            assertEquals(token, store.currentToken())
            store.clear()
            assertEquals(Session.SignedOut, awaitItem())
        }
    }

    @Test
    fun `currentToken is null once cleared`() = runTest {
        val store = InMemorySessionStore(FakeAccountMetadataStore())
        store.save(token, account)

        store.clear()

        assertNull(store.currentToken())
    }

    @Test
    fun `pre-existing metadata without a token is signed out`() {
        val store = InMemorySessionStore(FakeAccountMetadataStore(stored = account))

        assertEquals(Session.SignedOut, store.session.value)
        assertNull(store.currentToken())
    }

    @Test
    fun `writes metadata through on save and clears it on clear`() = runTest {
        val metadata = FakeAccountMetadataStore()
        val store = InMemorySessionStore(metadata)

        store.save(token, account)
        assertEquals(account, metadata.stored)

        store.clear()
        assertNull(metadata.stored)
    }
}
