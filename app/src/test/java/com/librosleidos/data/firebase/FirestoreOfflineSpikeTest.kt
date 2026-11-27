package com.librosleidos.data.firebase

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.Source
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Slice 0 build spike (design D4): against the Firestore emulator, an offline
 * `set()` returns its Task without waiting for a server acknowledgement and the
 * write is readable from the local cache straight away.
 *
 * Skipped unless `FIRESTORE_EMULATOR_HOST` is set, which `firebase emulators:exec`
 * does, so the default `:app:testDebugUnitTest` stays emulator-free.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class FirestoreOfflineSpikeTest {
    private val emulatorHost: String? = System.getenv("FIRESTORE_EMULATOR_HOST")

    private fun firestore(): FirebaseFirestore {
        val context: Context = ApplicationProvider.getApplicationContext()
        val options = (FirebaseApp.initializeApp(context) ?: FirebaseApp.getInstance()).options
        // A named app per test: Firestore settings and useEmulator are fixed once an instance is used.
        val app = FirebaseApp.initializeApp(context, options, UUID.randomUUID().toString())
        val (host, port) = checkNotNull(emulatorHost).split(":")
        return FirebaseFirestore.getInstance(app).apply {
            useEmulator(host, port.toInt())
            firestoreSettings =
                FirebaseFirestoreSettings
                    .Builder()
                    .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                    .build()
        }
    }

    /**
     * Firestore completes user-facing Tasks on the main looper, which Robolectric
     * pauses, so the test thread drains it while it waits instead of blocking.
     */
    private fun <T> await(task: Task<T>): T {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS)
        while (!task.isComplete) {
            check(System.nanoTime() < deadline) { "Timed out waiting for Task" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(POLL_MILLIS)
        }
        shadowOf(Looper.getMainLooper()).idle()
        return task.result
    }

    @Test
    fun offlineSet_returnsWithoutAckAndReadsBackFromCache() {
        assumeTrue("needs the Firestore emulator", emulatorHost != null)
        val db = firestore()
        await(db.disableNetwork())
        val doc = db.collection("spike").document(UUID.randomUUID().toString())

        val write = doc.set(mapOf("title" to "Dune", "rating" to 4.5))

        assertFalse("set() must not be acknowledged while offline", write.isComplete)
        val cached = await(doc.get(Source.CACHE))
        assertEquals("Dune", cached.getString("title"))
        assertEquals(4.5, cached.getDouble("rating")!!, 0.0)
        assertFalse(write.isComplete)
    }

    @Test
    fun offlineSet_isAcknowledgedOnceTheNetworkReturns() {
        assumeTrue("needs the Firestore emulator", emulatorHost != null)
        val db = firestore()
        await(db.disableNetwork())
        val doc = db.collection("spike").document(UUID.randomUUID().toString())
        val write = doc.set(mapOf("title" to "Emma"))

        await(db.enableNetwork())
        await(write)

        assertEquals("Emma", await(doc.get(Source.SERVER)).getString("title"))
    }

    private companion object {
        const val TIMEOUT_SECONDS = 20L
        const val POLL_MILLIS = 10L
    }
}
