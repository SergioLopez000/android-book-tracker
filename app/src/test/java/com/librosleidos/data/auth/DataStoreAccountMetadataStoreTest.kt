package com.librosleidos.data.auth

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.librosleidos.domain.model.Account
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Spec: "Metadata persisted", "Token absent from persistent storage", "Sign-out clears memory and storage". */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DataStoreAccountMetadataStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val scope = CoroutineScope(Dispatchers.IO)
    private val file =
        File(context.filesDir, "datastore/${DataStoreAccountMetadataStore.FILE_NAME}")
    private val dataStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
    private val store = DataStoreAccountMetadataStore(dataStore)
    private val account = Account("Ada Lovelace", "ada@example.com")

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `save persists the metadata and read returns it`() = runTest {
        store.save(account)

        assertEquals(account, store.read())
        val bytes = String(file.readBytes(), Charsets.ISO_8859_1)
        assertTrue(bytes.contains("Ada Lovelace"))
        assertTrue(bytes.contains("ada@example.com"))
    }

    @Test
    fun `no app file contains a token after a full sign-in flow`() = runTest {
        val token = com.librosleidos.domain.model.IdToken("distinctive-token-value-123")
        InMemorySessionStore(store).save(token, account)

        val appFiles = context.filesDir.parentFile!!.walkTopDown().filter { it.isFile }.toList()
        assertTrue(appFiles.any { it == file })
        appFiles.forEach {
            assertFalse(it.path, String(it.readBytes(), Charsets.ISO_8859_1).contains(token.value))
        }
    }

    @Test
    fun `clear leaves the store empty`() = runTest {
        store.save(account)

        store.clear()

        assertNull(store.read())
        assertFalse(String(file.readBytes(), Charsets.ISO_8859_1).contains("ada@example.com"))
    }
}
