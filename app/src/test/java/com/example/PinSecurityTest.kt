package com.example.data.preferences

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PinSecurityTest {

    private lateinit var preferenceManager: PreferenceManager

    @Before
    fun setUp() {
        preferenceManager = PreferenceManager(ApplicationProvider.getApplicationContext())
        preferenceManager.setAppPin(null)
    }

    @Test
    fun testPinSetAndVerification() {
        assertFalse(preferenceManager.hasAppPin())
        preferenceManager.setAppPin("1234")
        assertTrue(preferenceManager.hasAppPin())

        assertTrue(preferenceManager.verifyPin("1234"))
        assertFalse(preferenceManager.verifyPin("9999"))
    }

    @Test
    fun testPinChangeAndRemoval() {
        preferenceManager.setAppPin("1234")
        assertTrue(preferenceManager.changeAppPin("1234", "5678"))
        assertTrue(preferenceManager.verifyPin("5678"))

        assertTrue(preferenceManager.removeAppPin("5678"))
        assertFalse(preferenceManager.hasAppPin())
    }

    @Test
    fun testPinLockoutThreshold() {
        preferenceManager.setAppPin("1234")
        for (i in 1..4) {
            val result = preferenceManager.verifyPinWithDetails("0000")
            assertTrue("Attempt $i should return Incorrect", result is PinVerifyResult.Incorrect)
        }

        // 5th failed attempt should trigger 30s lockout
        val result5 = preferenceManager.verifyPinWithDetails("0000")
        assertTrue("5th attempt should lock out user", result5 is PinVerifyResult.LockedOut)
    }
}
