package com.example.voice

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * RecognitionEngineMode:
 * Represents the active speech recognition pipeline:
 * - ONLINE_CLOUD: Network-connected Google speech recognition servers (high regional accuracy).
 * - OFFLINE_ON_DEVICE: Android RecognizerIntent on-device speech packs / offline handling.
 */
enum class RecognitionEngineMode {
    ONLINE_CLOUD,
    OFFLINE_ON_DEVICE
}

/**
 * VoiceLocale:
 * First-class locale definitions specifically tuned for Telugu and English financial transactions.
 */
enum class VoiceLocale(
    val code: String,
    val languageTag: String,
    val displayNameTe: String,
    val displayNameEn: String,
    val locale: Locale,
    val promptTe: String,
    val promptEn: String
) {
    TELUGU(
        code = "te",
        languageTag = "te-IN",
        displayNameTe = "తెలుగు",
        displayNameEn = "Telugu",
        locale = Locale.forLanguageTag("te-IN"),
        promptTe = "మీ ఖర్చు లేదా అప్పు వివరాలు చెప్పండి (ఉదా: కూరగాయలు 250)...",
        promptEn = "Speak your expense or loan details in Telugu..."
    ),
    ENGLISH(
        code = "en",
        languageTag = "en-IN",
        displayNameTe = "ఇంగ్లీష్",
        displayNameEn = "English (India)",
        locale = Locale.forLanguageTag("en-IN"),
        promptTe = "ఖర్చు వివరాలు చెప్పండి...",
        promptEn = "Speak your expense or loan details (e.g. Groceries 250)..."
    );

    companion object {
        fun fromCode(code: String): VoiceLocale {
            return if (code.startsWith("te", ignoreCase = true)) TELUGU else ENGLISH
        }
    }
}

/**
 * VoiceStateManager:
 * Centralized state manager for speech recognition that dynamically monitors
 * Android's ConnectivityManager to switch between Online and Offline speech recognition,
 * specifically optimized for Telugu (te-IN) and English (en-IN) locales.
 */
class VoiceStateManager(private val context: Context) {

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    // --- State Streams ---
    private val _isOnline = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _engineMode = MutableStateFlow(RecognitionEngineMode.OFFLINE_ON_DEVICE)
    val engineMode: StateFlow<RecognitionEngineMode> = _engineMode.asStateFlow()

    private val _activeLocale = MutableStateFlow(VoiceLocale.TELUGU)
    val activeLocale: StateFlow<VoiceLocale> = _activeLocale.asStateFlow()

    private val _statusDescriptionTe = MutableStateFlow("ఆఫ్‌లైన్")
    val statusDescriptionTe: StateFlow<String> = _statusDescriptionTe.asStateFlow()

    private val _statusDescriptionEn = MutableStateFlow("Offline")
    val statusDescriptionEn: StateFlow<String> = _statusDescriptionEn.asStateFlow()

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var isCallbackRegistered = false

    init {
        refreshNetworkState()
        registerNetworkCallbackSafely()
    }

    /**
     * Re-evaluates active network capabilities and synchronizes engine mode.
     */
    fun refreshNetworkState() {
        val online = checkIsOnline()
        _isOnline.value = online

        val mode = if (online) RecognitionEngineMode.ONLINE_CLOUD else RecognitionEngineMode.OFFLINE_ON_DEVICE
        _engineMode.value = mode

        updateStatusDescriptions(mode)
    }

    private fun registerNetworkCallbackSafely() {
        if (connectivityManager == null) return
        try {
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    refreshNetworkState()
                }

                override fun onLost(network: Network) {
                    refreshNetworkState()
                }

                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    refreshNetworkState()
                }
            }
            networkCallback = callback

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                connectivityManager.registerDefaultNetworkCallback(callback)
            } else {
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                connectivityManager.registerNetworkCallback(request, callback)
            }
            isCallbackRegistered = true
        } catch (e: Exception) {
            com.example.util.AppLogger.e("VoiceStateManager", "Failed registering network callback", e)
        }
    }

    /**
     * Checks whether active network has validated internet capability.
     */
    fun checkIsOnline(): Boolean {
        return try {
            val cm = connectivityManager ?: return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val activeNet = cm.activeNetwork ?: return false
                val caps = cm.getNetworkCapabilities(activeNet) ?: return false
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            } else {
                @Suppress("DEPRECATION")
                val netInfo = cm.activeNetworkInfo
                netInfo != null && netInfo.isConnected
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Switch active locale between Telugu and English.
     */
    fun setLocale(locale: VoiceLocale) {
        _activeLocale.value = locale
        updateStatusDescriptions(_engineMode.value)
    }

    fun setLocaleByCode(code: String) {
        setLocale(VoiceLocale.fromCode(code))
    }

    private fun updateStatusDescriptions(mode: RecognitionEngineMode) {
        if (mode == RecognitionEngineMode.ONLINE_CLOUD) {
            _statusDescriptionTe.value = "ఆన్‌లైన్"
            _statusDescriptionEn.value = "Online"
        } else {
            _statusDescriptionTe.value = "ఆఫ్‌లైన్"
            _statusDescriptionEn.value = "Offline"
        }
    }

    /**
     * Builds a pre-configured SpeechRecognizer Intent according to active ConnectivityManager state:
     * - Online: Cloud speech intent for highest regional accuracy.
     * - Offline English: Sets EXTRA_PREFER_OFFLINE = true to leverage downloaded on-device pack.
     * - Offline Telugu: Uses on-device recognition intent without crashing.
     */
    fun buildSpeechIntent(
        locale: VoiceLocale = _activeLocale.value,
        preferOfflineOverride: Boolean? = null
    ): Intent {
        val online = preferOfflineOverride?.let { !it } ?: _isOnline.value

        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)

            // Online vs Offline policy
            if (!online) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }

            val primaryLang = locale.languageTag
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, primaryLang)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, primaryLang)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val alternateLangs = when (locale) {
                    VoiceLocale.TELUGU -> arrayOf(VoiceLocale.ENGLISH.languageTag)
                    VoiceLocale.ENGLISH -> arrayOf(VoiceLocale.TELUGU.languageTag)
                }
                putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", alternateLangs)
            }

            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L)

            val prompt = if (locale == VoiceLocale.TELUGU) locale.promptTe else locale.promptEn
            putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
        }
    }

    /**
     * Provides clear, gentle, user-facing error message based on language and online status.
     * Never leaves the user with silence or confusion.
     */
    fun getFriendlyErrorMessage(errorCode: Int, locale: VoiceLocale = _activeLocale.value, isOnline: Boolean = _isOnline.value): String {
        val isTelugu = locale == VoiceLocale.TELUGU
        return when (errorCode) {
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                if (isTelugu) "వాయిస్ వినపడలేదు. మైక్ దగ్గరగా మాట్లాడండి."
                else "No speech detected. Please speak closer to microphone."

            SpeechRecognizer.ERROR_AUDIO ->
                if (isTelugu) "ఆడియో సమస్య. మైక్ అనుమతి తనిఖీ చేయండి."
                else "Audio recording error. Please check microphone."

            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                if (isTelugu) "మైక్రోఫోన్ అనుమతి అవసరం."
                else "Microphone permission is required."

            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                if (!isOnline) {
                    if (isTelugu) "ఆఫ్‌లైన్: ఆన్-డివైస్ వాయిస్ ప్యాక్ లేదు లేదా నెట్‌వర్క్ అవసరం."
                    else "Offline: On-device speech pack not found or network required."
                } else {
                    if (isTelugu) "నెట్‌వర్క్ కనెక్షన్ లేదా గూగుల్ వాయిస్ సేవ అవసరం."
                    else "Network connection or Google Speech service required."
                }

            SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                if (isTelugu) "వాయిస్ సేవ బిజీగా ఉంది. దయచేసి మళ్లీ ప్రయత్నించండి."
                else "Speech recognizer is busy. Please try again."

            SpeechRecognizer.ERROR_CLIENT ->
                if (!isOnline) {
                    if (isTelugu) "ఆఫ్‌లైన్ వాయిస్ గుర్తించలేకపోయాను. దయచేసి మళ్లీ మాట్లాడండి."
                    else "Offline speech not recognized. Please try speaking again."
                } else {
                    if (isTelugu) "వాయిస్ సేవలో సమస్య. మళ్లీ మైక్ నొక్కండి."
                    else "Speech service issue. Please tap mic to retry."
                }

            else ->
                if (isTelugu) "వాయిస్ గుర్తించలేకపోయాను. మళ్లీ ప్రయత్నించండి."
                else "Could not recognize speech. Please retry."
        }
    }

    /**
     * Releases ConnectivityManager network callback when destroyed.
     */
    fun destroy() {
        if (isCallbackRegistered && networkCallback != null && connectivityManager != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback!!)
            } catch (e: Exception) {
                com.example.util.AppLogger.e("VoiceStateManager", "Failed unregistering network callback", e)
            } finally {
                isCallbackRegistered = false
                networkCallback = null
            }
        }
    }
}
