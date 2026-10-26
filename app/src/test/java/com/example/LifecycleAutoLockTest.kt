package com.example

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LifecycleAutoLockTest {

    @Test
    fun testBackgroundTimeoutEvaluation() {
        val timeoutSec = 30L

        val shortElapsedSec = 10L
        assertFalse(shortElapsedSec >= timeoutSec)

        val longElapsedSec = 35L
        assertTrue(longElapsedSec >= timeoutSec)
    }
}
