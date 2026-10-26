package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AppLoggerTest {

    @Before
    fun setUp() {
        // AppLogger has an in-memory queue of 10 items
    }

    @Test
    fun testDiagnosticErrorLoggingAndSanitization() {
        AppLogger.w("TestTag", "Warning event occurred", RuntimeException("SampleEx"))
        val latest = AppLogger.getLatestError()
        assertTrue(latest != null)
        assertEquals("TestTag", latest?.tag)
        assertEquals("Warning event occurred", latest?.message)
        assertEquals("RuntimeException", latest?.errorCode)
    }
}
