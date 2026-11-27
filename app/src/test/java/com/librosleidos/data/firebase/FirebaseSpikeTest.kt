package com.librosleidos.data.firebase

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Slice 0 build spike (design D10): proves the google-services plugin turns the
 * committed placeholder `app/google-services.json` into resources that
 * [FirebaseApp] reads under Robolectric, so CI builds and tests with no secret.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class FirebaseSpikeTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun app(): FirebaseApp = FirebaseApp.initializeApp(context) ?: FirebaseApp.getInstance()

    @Test
    fun initializeApp_readsThePlaceholderProjectId() {
        assertEquals("demo-libros", app().options.projectId)
    }

    @Test
    fun placeholderConfig_carriesNoRealLookingApiKey() {
        val options = app().options

        assertTrue(options.apiKey.isNotBlank())
        assertFalse(options.apiKey.startsWith("AIza"))
        assertEquals("com.sergiolopez.rincondelectura", context.packageName)
    }
}
