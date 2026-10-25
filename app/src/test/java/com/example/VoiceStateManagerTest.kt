package com.example

import android.content.Context
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.test.core.app.ApplicationProvider
import com.example.voice.RecognitionEngineMode
import com.example.voice.VoiceLocale
import com.example.voice.VoiceStateManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VoiceStateManagerTest {

    private lateinit var context: Context
    private lateinit var voiceStateManager: VoiceStateManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        voiceStateManager = VoiceStateManager(context)
    }

    @After
    fun tearDown() {
        voiceStateManager.destroy()
    }

    @Test
    fun testInitialLocaleIsTelugu() {
        assertEquals(VoiceLocale.TELUGU, voiceStateManager.activeLocale.value)
        assertEquals("te-IN", voiceStateManager.activeLocale.value.languageTag)
    }

    @Test
    fun testSwitchingBetweenTeluguAndEnglishLocales() {
        // Switch to English
        voiceStateManager.setLocale(VoiceLocale.ENGLISH)
        assertEquals(VoiceLocale.ENGLISH, voiceStateManager.activeLocale.value)
        assertEquals("en-IN", voiceStateManager.activeLocale.value.languageTag)

        // Switch to Telugu via code
        voiceStateManager.setLocaleByCode("te")
        assertEquals(VoiceLocale.TELUGU, voiceStateManager.activeLocale.value)
        assertEquals("te-IN", voiceStateManager.activeLocale.value.languageTag)

        // Switch back to English via code
        voiceStateManager.setLocaleByCode("en")
        assertEquals(VoiceLocale.ENGLISH, voiceStateManager.activeLocale.value)
    }

    @Test
    fun testOnlineSpeechIntentOmitsPreferOffline() {
        val intentTe = voiceStateManager.buildSpeechIntent(
            locale = VoiceLocale.TELUGU,
            preferOfflineOverride = false
        )
        assertNotNull(intentTe)
        assertEquals("te-IN", intentTe.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))
        assertFalse(
            "Online cloud speech recognition should NOT set EXTRA_PREFER_OFFLINE",
            intentTe.getBooleanExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
        )

        val intentEn = voiceStateManager.buildSpeechIntent(
            locale = VoiceLocale.ENGLISH,
            preferOfflineOverride = false
        )
        assertNotNull(intentEn)
        assertEquals("en-IN", intentEn.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))
        assertFalse(
            "Online cloud English speech recognition should NOT set EXTRA_PREFER_OFFLINE",
            intentEn.getBooleanExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
        )
    }

    @Test
    fun testOfflineSpeechIntentSetsPreferOffline() {
        val intentEn = voiceStateManager.buildSpeechIntent(
            locale = VoiceLocale.ENGLISH,
            preferOfflineOverride = true
        )
        assertNotNull(intentEn)
        assertEquals("en-IN", intentEn.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))
        assertTrue(
            "Offline speech must set EXTRA_PREFER_OFFLINE to true",
            intentEn.getBooleanExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
        )

        val intentTe = voiceStateManager.buildSpeechIntent(
            locale = VoiceLocale.TELUGU,
            preferOfflineOverride = true
        )
        assertNotNull(intentTe)
        assertEquals("te-IN", intentTe.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))
        assertTrue(
            "Offline speech must set EXTRA_PREFER_OFFLINE to true",
            intentTe.getBooleanExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
        )
    }

    @Test
    fun testFriendlyErrorMessagesForTeluguAndEnglish() {
        val msgTeAudio = voiceStateManager.getFriendlyErrorMessage(
            errorCode = SpeechRecognizer.ERROR_AUDIO,
            locale = VoiceLocale.TELUGU,
            isOnline = true
        )
        assertTrue(msgTeAudio.contains("మైక్") || msgTeAudio.contains("ఆడియో"))

        val msgEnAudio = voiceStateManager.getFriendlyErrorMessage(
            errorCode = SpeechRecognizer.ERROR_AUDIO,
            locale = VoiceLocale.ENGLISH,
            isOnline = true
        )
        assertTrue(msgEnAudio.contains("microphone") || msgEnAudio.contains("Audio"))

        // Test offline error paths specifically
        val msgTeOfflineNet = voiceStateManager.getFriendlyErrorMessage(
            errorCode = SpeechRecognizer.ERROR_NETWORK,
            locale = VoiceLocale.TELUGU,
            isOnline = false
        )
        assertTrue(msgTeOfflineNet.contains("ఆఫ్‌లైన్"))

        val msgEnOfflineNet = voiceStateManager.getFriendlyErrorMessage(
            errorCode = SpeechRecognizer.ERROR_NETWORK,
            locale = VoiceLocale.ENGLISH,
            isOnline = false
        )
        assertTrue(msgEnOfflineNet.contains("Offline"))
    }
}
