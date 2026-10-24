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

        // Switch to Hindi via code
        voiceStateManager.setLocaleByCode("hi")
        assertEquals(VoiceLocale.HINDI, voiceStateManager.activeLocale.value)
        assertEquals("hi-IN", voiceStateManager.activeLocale.value.languageTag)
    }

    @Test
    fun testOnlineSpeechIntentOmitsPreferOffline() {
        // Force online intent simulation
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
    fun testOfflineSpeechIntentForEnglishSetsPreferOffline() {
        // Force offline intent simulation
        val intentEn = voiceStateManager.buildSpeechIntent(
            locale = VoiceLocale.ENGLISH,
            preferOfflineOverride = true
        )
        assertNotNull(intentEn)
        assertEquals("en-IN", intentEn.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))
        assertTrue(
            "Offline English speech must set EXTRA_PREFER_OFFLINE to true",
            intentEn.getBooleanExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
        )
    }

    @Test
    fun testOfflineSpeechIntentForTeluguHasCorrectLanguageTag() {
        val intentTe = voiceStateManager.buildSpeechIntent(
            locale = VoiceLocale.TELUGU,
            preferOfflineOverride = true
        )
        assertNotNull(intentTe)
        assertEquals("te-IN", intentTe.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))
    }

    @Test
    fun testSuppressOfflineErrorsPolicy() {
        // When offline, network and client errors should be suppressed to prevent harsh error toasts
        if (!voiceStateManager.isOnline.value) {
            assertTrue(voiceStateManager.shouldSuppressOfflineError(SpeechRecognizer.ERROR_NETWORK))
            assertTrue(voiceStateManager.shouldSuppressOfflineError(SpeechRecognizer.ERROR_NETWORK_TIMEOUT))
            assertTrue(voiceStateManager.shouldSuppressOfflineError(SpeechRecognizer.ERROR_CLIENT))
            assertTrue(voiceStateManager.shouldSuppressOfflineError(SpeechRecognizer.ERROR_NO_MATCH))
        }
    }

    @Test
    fun testFriendlyErrorMessagesForTeluguAndEnglish() {
        val msgTe = voiceStateManager.getFriendlyErrorMessage(
            errorCode = SpeechRecognizer.ERROR_AUDIO,
            locale = VoiceLocale.TELUGU,
            isOnline = true
        )
        assertTrue(msgTe.contains("మైక్") || msgTe.contains("ఆడియో"))

        val msgEn = voiceStateManager.getFriendlyErrorMessage(
            errorCode = SpeechRecognizer.ERROR_AUDIO,
            locale = VoiceLocale.ENGLISH,
            isOnline = true
        )
        assertTrue(msgEn.contains("microphone") || msgEn.contains("Audio"))
    }
}
