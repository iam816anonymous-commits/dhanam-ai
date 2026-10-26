package com.example.ai

import androidx.test.core.app.ApplicationProvider
import com.example.data.preferences.PreferenceManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CloudAiConsentBoundaryTest {

    private lateinit var preferenceManager: PreferenceManager

    @Before
    fun setUp() {
        preferenceManager = PreferenceManager(ApplicationProvider.getApplicationContext())
        preferenceManager.setCloudAiOptIn(false)
    }

    @Test
    fun testCloudAiConsentOptOut() = runTest {
        assertFalse(preferenceManager.isCloudAiOptIn())
    }

    @Test
    fun testCloudAiConsentOptInToggle() = runTest {
        preferenceManager.setCloudAiOptIn(true)
        assertTrue(preferenceManager.isCloudAiOptIn())

        preferenceManager.setCloudAiOptIn(false)
        assertFalse(preferenceManager.isCloudAiOptIn())
    }
}
