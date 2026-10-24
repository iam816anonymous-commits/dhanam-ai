package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Dedicated VoiceManager that initializes Android SpeechRecognizer and sets EXTRA_PREFER_OFFLINE = true.
 * Specifically configured to handle both English ("en-IN" / "en-US") and Telugu ("te-IN") locales
 * seamlessly when offline.
 */
class VoiceManager(private val context: Context) {

    enum class SupportedLocale(val tag: String, val displayName: String, val locale: Locale) {
        TELUGU("te-IN", "తెలుగు", Locale.forLanguageTag("te-IN")),
        ENGLISH("en-IN", "English (India)", Locale.forLanguageTag("en-IN")),
        HINDI("hi-IN", "हिन्दी (Hindi)", Locale.forLanguageTag("hi-IN"));

        companion object {
            fun fromTag(tag: String): SupportedLocale {
                return when {
                    tag.startsWith("te", ignoreCase = true) -> TELUGU
                    tag.startsWith("hi", ignoreCase = true) -> HINDI
                    else -> ENGLISH
                }
            }
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private var speechRecognizer: SpeechRecognizer? = null
    private var currentLocale: SupportedLocale = SupportedLocale.TELUGU
    private var hasRetriedFallback = false

    // Centralized VoiceStateManager managing ConnectivityManager state & engine switching
    val voiceStateManager = VoiceStateManager(context)

    // State flows for UI observation
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _finalTranscript = MutableStateFlow("")
    val finalTranscript: StateFlow<String> = _finalTranscript.asStateFlow()

    private val _rmsDb = MutableStateFlow(0f)
    val rmsDb: StateFlow<Float> = _rmsDb.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _activeLocale = MutableStateFlow(SupportedLocale.TELUGU)
    val activeLocale: StateFlow<SupportedLocale> = _activeLocale.asStateFlow()

    // Callbacks
    private var onResultCallback: ((String) -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null

    init {
        initializeRecognizer()
    }

    /**
     * Initializes Android's SpeechRecognizer.
     * On Android 12+ (API 31+), prefers on-device recognition if available,
     * with graceful fallback to system speech recognition service.
     */
    fun createSpeechRecognizerSafely(): SpeechRecognizer? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                if (SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                    return SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                }
            } catch (_: Exception) {}
        }
        try {
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            if (recognizer != null) return recognizer
        } catch (_: Exception) {}

        // Android 8 (API 26/27) & OEM fallback: Explicit Google Recognition Service Component
        try {
            val googleComponent = android.content.ComponentName(
                "com.google.android.googlequicksearchbox",
                "com.google.android.voicesearch.serviceapi.GoogleRecognitionService"
            )
            return SpeechRecognizer.createSpeechRecognizer(context, googleComponent)
        } catch (_: Exception) {}

        return null
    }

    fun isSystemSpeechActivityAvailable(): Boolean {
        return try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            val activities = context.packageManager.queryIntentActivities(intent, 0)
            activities.isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    private fun initializeRecognizer() {
        mainHandler.post {
            try {
                destroyRecognizer()
                speechRecognizer = createSpeechRecognizerSafely()
                speechRecognizer?.setRecognitionListener(createListener())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Starts listening with EXTRA_PREFER_OFFLINE set to true.
     * Supports Telugu ("te-IN") and English ("en-IN") locales with cross-locale fallback when offline.
     */
    fun startListening(
        locale: SupportedLocale = currentLocale,
        onResult: (String) -> Unit = {},
        onError: ((String) -> Unit)? = null
    ) {
        mainHandler.post {
            currentLocale = locale
            _activeLocale.value = locale
            onResultCallback = onResult
            onErrorCallback = onError
            hasRetriedFallback = false

            _error.value = null
            _partialTranscript.value = ""
            _isListening.value = true

            if (speechRecognizer == null) {
                speechRecognizer = createSpeechRecognizerSafely()
                speechRecognizer?.setRecognitionListener(createListener())
            }

            if (speechRecognizer == null) {
                _isListening.value = false
                val msg = if (locale == SupportedLocale.TELUGU) {
                    "వాయిస్ సేవ గుర్తించబడలేదు. దయచేసి మైక్ నొక్కి మళ్లీ ప్రయత్నించండి."
                } else {
                    "Speech recognition service unavailable. Please tap mic to try again."
                }
                _error.value = msg
                onError?.invoke(msg)
                return@post
            }

            startListeningInternal(locale)
        }
    }

    /**
     * Convenience method to start listening using a boolean flag for Telugu.
     */
    fun startListening(
        isTelugu: Boolean,
        onResult: (String) -> Unit = {},
        onError: ((String) -> Unit)? = null
    ) {
        startListening(
            locale = if (isTelugu) SupportedLocale.TELUGU else SupportedLocale.ENGLISH,
            onResult = onResult,
            onError = onError
        )
    }

    fun isOnline(): Boolean {
        return voiceStateManager.checkIsOnline()
    }

    private fun startListeningInternal(locale: SupportedLocale) {
        try {
            val online = isOnline()
            val preferOffline = !online
            val intent = createSpeechIntent(locale, preferOffline = preferOffline)
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            if (!hasRetriedFallback) {
                hasRetriedFallback = true
                try {
                    destroyRecognizer()
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                        setRecognitionListener(createListener())
                        val online = isOnline()
                        startListening(createSpeechIntent(locale, preferOffline = !online))
                    }
                    return
                } catch (fallbackEx: Exception) {
                    fallbackEx.printStackTrace()
                }
            }
            _isListening.value = false
            val err = "Failed to start speech recognition: ${e.localizedMessage}"
            _error.value = err
            onErrorCallback?.invoke(err)
        }
    }

    /**
     * Constructs the speech recognizer intent configured explicitly for offline recognition.
     * Always sets EXTRA_PREFER_OFFLINE = true.
     */
    fun createOfflineSpeechIntent(locale: SupportedLocale): Intent {
        return createSpeechIntent(locale, preferOffline = true)
    }

    /**
     * Constructs speech intent with configurable preferOffline parameter.
     */
    fun createSpeechIntent(locale: SupportedLocale, preferOffline: Boolean): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)

            if (preferOffline) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }

            val primaryTag = locale.tag
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, primaryTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, primaryTag)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val alternateTags = when (locale) {
                    SupportedLocale.TELUGU -> arrayOf(SupportedLocale.ENGLISH.tag, SupportedLocale.HINDI.tag)
                    SupportedLocale.HINDI -> arrayOf(SupportedLocale.ENGLISH.tag, SupportedLocale.TELUGU.tag)
                    SupportedLocale.ENGLISH -> arrayOf(SupportedLocale.TELUGU.tag, SupportedLocale.HINDI.tag)
                }
                putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", alternateTags)
            }

            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L)

            val promptMsg = when (locale) {
                SupportedLocale.TELUGU -> "మీ ఖర్చు లేదా అప్పు వివరాలు చెప్పండి (ఉదా: కూరగాయలు 250)..."
                SupportedLocale.HINDI -> "खर्च या उधारी का विवरण बोलें (जैसे: राशन 250)..."
                SupportedLocale.ENGLISH -> "Speak your expense or loan details (e.g. Groceries 250)..."
            }
            putExtra(RecognizerIntent.EXTRA_PROMPT, promptMsg)
        }
    }

    /**
     * RecognitionListener with custom offline error handling and locale processing.
     */
    private fun createListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _isListening.value = true
                _error.value = null
            }

            override fun onBeginningOfSpeech() {
                _isListening.value = true
            }

            override fun onRmsChanged(rmsdB: Float) {
                _rmsDb.value = rmsdB.coerceAtLeast(0f)
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                // Speech ended, recognizer is processing onResults
            }

            override fun onError(errorCode: Int) {
                if (!hasRetriedFallback && (errorCode == SpeechRecognizer.ERROR_CLIENT || errorCode == SpeechRecognizer.ERROR_SERVER)) {
                    hasRetriedFallback = true
                    val listener = this
                    try {
                        destroyRecognizer()
                        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                            setRecognitionListener(listener)
                            val online = isOnline()
                            startListening(createSpeechIntent(currentLocale, preferOffline = !online))
                        }
                        return
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                _isListening.value = false

                if (!isOnline()) {
                    _error.value = null
                    return
                }

                val isTelugu = currentLocale == SupportedLocale.TELUGU
                val isHindi = currentLocale == SupportedLocale.HINDI
                val errorMsg = when (errorCode) {
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {
                        if (!isOnline()) {
                            if (isTelugu) {
                                "వాయిస్ వినపడలేదు. మైక్ దగ్గరగా మాట్లాడండి లేదా కింద ఉన్న చిప్స్ వాడండి."
                            } else if (isHindi) {
                                "आवाज़ नहीं सुनी गई। कृपया माइक के पास बोलें या नीचे दिए गए चिप्स का उपयोग करें।"
                            } else {
                                "No speech detected. Please speak closer to microphone or use chips below."
                            }
                        } else {
                            if (isTelugu) "నెట్‌వర్క్ సమస్య లేదా గూగుల్ వాయిస్ అందుబాటులో లేదు."
                            else if (isHindi) "नेटवर्क समस्या या Google वॉइस उपलब्ध नहीं है।"
                            else "Network connection or Google Speech service needed."
                        }
                    }

                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                        if (isTelugu) "వాయిస్ వినపడలేదు. మైక్ దగ్గరగా మాట్లాడండి."
                        else if (isHindi) "कोई आवाज़ नहीं सुनी गई। कृपया माइक के पास बोलें।"
                        else "No speech match detected. Please speak closer to microphone."
                    }

                    SpeechRecognizer.ERROR_AUDIO -> {
                        if (isTelugu) "ఆడియో రికార్డింగ్ సమస్య. మైక్ అనుమతి తనిఖీ చేయండి."
                        else if (isHindi) "ऑडियो रिकॉर्डिंग त्रुटि। माइक अनुमति जांचें।"
                        else "Audio recording error. Please check microphone."
                    }

                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                        if (isTelugu) "మైక్రోఫోన్ అనుమతి అవసరం."
                        else if (isHindi) "माइक्रोफ़ोन अनुमति आवश्यक है।"
                        else "Microphone permission is required."
                    }

                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                        cancelListening()
                        if (isTelugu) "వాయిస్ సేవ బిజీగా ఉంది. దయచేసి మళ్లీ ప్రయత్నించండి."
                        else if (isHindi) "वॉइस सेवा व्यस्त है। कृपया पुनः प्रयास करें।"
                        else "Speech recognizer is busy. Please try again."
                    }

                    SpeechRecognizer.ERROR_CLIENT -> {
                        if (isTelugu) "వాయిస్ ప్రాసెస్ చేయడంలో లోపం. మళ్లీ మైక్ నొక్కండి."
                        else if (isHindi) "वॉइस पहचान में त्रुटि। पुनः माइक दबाएं।"
                        else "Speech client error. Please tap the mic again."
                    }

                    else -> {
                        if (isTelugu) "వాయిస్ గుర్తించలేకపోయాను ($errorCode). మళ్లీ ప్రయత్నించండి."
                        else if (isHindi) "वॉइस नहीं पहचान पाए ($errorCode)। पुनः प्रयास करें।"
                        else "Could not recognize speech (Error $errorCode). Please retry."
                    }
                }

                _error.value = errorMsg
                onErrorCallback?.invoke(errorMsg)
            }

            override fun onResults(results: Bundle?) {
                _isListening.value = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val raw = matches?.firstOrNull()?.trim()

                if (!raw.isNullOrBlank()) {
                    // Process recognized text using phonetic and digit filtering
                    val isTelugu = currentLocale == SupportedLocale.TELUGU
                    val processed = VoicePostProcessor.filterTranscript(raw, isTelugu)
                    _finalTranscript.value = processed
                    _partialTranscript.value = processed
                    onResultCallback?.invoke(processed)
                } else {
                    val msg = if (currentLocale == SupportedLocale.TELUGU) {
                        "మాటలు గుర్తించబడలేదు."
                    } else {
                        "No words recognized."
                    }
                    _error.value = msg
                    onErrorCallback?.invoke(msg)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val raw = matches?.firstOrNull()?.trim()
                if (!raw.isNullOrBlank()) {
                    val isTelugu = currentLocale == SupportedLocale.TELUGU
                    val processed = VoicePostProcessor.filterTranscript(raw, isTelugu)
                    _partialTranscript.value = processed
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    /**
     * Immediately stops listening and triggers final processing of partial text if available.
     */
    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            _isListening.value = false
        }
    }

    /**
     * Cancels current recognition session.
     */
    fun cancelListening() {
        mainHandler.post {
            try {
                speechRecognizer?.cancel()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            _isListening.value = false
            _partialTranscript.value = ""
        }
    }

    /**
     * Releases recognizer resources.
     */
    fun destroy() {
        mainHandler.post {
            destroyRecognizer()
            voiceStateManager.destroy()
        }
    }

    private fun destroyRecognizer() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            speechRecognizer = null
        }
    }

    /**
     * Checks if on-device offline recognition is supported by the Android OS.
     */
    fun isOnDeviceRecognitionSupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
    }
}
