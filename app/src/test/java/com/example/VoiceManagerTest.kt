package com.example

import android.content.Context
import android.speech.RecognizerIntent
import androidx.test.core.app.ApplicationProvider
import com.example.voice.VoiceManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VoiceManagerTest {

    @Test
    fun testOfflineIntentHasPreferOfflineTrueForTelugu() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val voiceManager = VoiceManager(context)

        val intent = voiceManager.createOfflineSpeechIntent(VoiceManager.SupportedLocale.TELUGU)
        assertNotNull(intent)

        // Verify EXTRA_PREFER_OFFLINE is set to true
        assertTrue(
            "EXTRA_PREFER_OFFLINE must be set to true",
            intent.getBooleanExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
        )

        // Verify Telugu language code
        assertEquals("te-IN", intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))
        assertEquals("te-IN", intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE))
    }

    @Test
    fun testOfflineIntentHasPreferOfflineTrueForEnglish() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val voiceManager = VoiceManager(context)

        val intent = voiceManager.createOfflineSpeechIntent(VoiceManager.SupportedLocale.ENGLISH)
        assertNotNull(intent)

        // Verify EXTRA_PREFER_OFFLINE is set to true
        assertTrue(
            "EXTRA_PREFER_OFFLINE must be set to true",
            intent.getBooleanExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
        )

        // Verify English language code
        assertEquals("en-IN", intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))
        assertEquals("en-IN", intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE))
    }

    @Test
    fun testOfflineIntentHasPreferOfflineTrueForHindi() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val voiceManager = VoiceManager(context)

        val intent = voiceManager.createOfflineSpeechIntent(VoiceManager.SupportedLocale.HINDI)
        assertNotNull(intent)

        assertTrue(
            "EXTRA_PREFER_OFFLINE must be set to true for Hindi",
            intent.getBooleanExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
        )
        assertEquals("hi-IN", intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))
        assertEquals("hi-IN", intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE))
    }

    @Test
    fun testAndroid8OfflineSpeechIntent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val voiceManager = VoiceManager(context)

        val intentTe = voiceManager.createOfflineSpeechIntent(VoiceManager.SupportedLocale.TELUGU)
        assertTrue(intentTe.getBooleanExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false))
        assertEquals("te-IN", intentTe.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))

        val intentEn = voiceManager.createOfflineSpeechIntent(VoiceManager.SupportedLocale.ENGLISH)
        assertTrue(intentEn.getBooleanExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false))
        assertEquals("en-IN", intentEn.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))

        val intentHi = voiceManager.createOfflineSpeechIntent(VoiceManager.SupportedLocale.HINDI)
        assertTrue(intentHi.getBooleanExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false))
        assertEquals("hi-IN", intentHi.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))
    }
}
