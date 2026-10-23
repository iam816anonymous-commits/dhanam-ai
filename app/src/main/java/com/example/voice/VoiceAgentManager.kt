package com.example.voice

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import kotlin.math.abs

/**
 * In-Built Voice Manager for Dhanam AI:
 * - Low-memory audio pipeline with Android 8.1+ AudioFocus isolation
 * - Throttled RMS dB stream to eliminate CPU recomposition overhead on low-spec MTK chips
 * - Direct on-device speech recognition via android.speech.SpeechRecognizer
 * - Prioritizes downloaded on-device offline language packages (Telugu, English, Hindi, Kannada)
 * - Fine-tuned human-like Text-to-Speech cadence for Telugu & Indian languages
 * - Natural Telugu number spelling (e.g. 450 -> నాలుగు వందల యాభై) for crystal-clear offline pronunciation
 * - Full Android 8.1 (API 27) through 16/17 compatibility
 */
class VoiceAgentManager(private val context: Context) : TextToSpeech.OnInitListener {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var lastRmsUpdateTime = 0L
    private var lastRmsDb = 0f

    private val mainHandler = Handler(Looper.getMainLooper())

    // --- State Streams ---
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _rmsDb = MutableStateFlow(0f)
    val rmsDb: StateFlow<Float> = _rmsDb.asStateFlow()

    private val _speechError = MutableStateFlow<String?>(null)
    val speechError: StateFlow<String?> = _speechError.asStateFlow()

    private val _isTtsReady = MutableStateFlow(false)
    val isTtsReady: StateFlow<Boolean> = _isTtsReady.asStateFlow()

    private val _isTeluguTtsAvailable = MutableStateFlow(false)
    val isTeluguTtsAvailable: StateFlow<Boolean> = _isTeluguTtsAvailable.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    // --- Core Engines ---
    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null

    // Cached Offline Voices
    private var cachedTeluguVoice: Voice? = null
    private var cachedEnglishVoice: Voice? = null
    private var cachedHindiVoice: Voice? = null
    private var cachedKannadaVoice: Voice? = null

    private var currentResultCallback: ((String) -> Unit)? = null
    private var currentErrorCallback: ((String) -> Unit)? = null
    private var activeLanguageIsTelugu = false
    private var hasRetriedOnlineFallback = false
    private var hasRetriedClientFallback = false

    init {
        mainHandler.post {
            initializeTts()
        }
    }

    private fun initializeTts() {
        try {
            // Disconnected Google TTS lock - uses standard Android platform TextToSpeech
            tts = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            e.printStackTrace()
            _isTtsReady.value = false
        }
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            _isTtsReady.value = true

            // Fine-tune natural conversational cadence (0.88f prevents fast robotic distortion in Telugu)
            tts?.setSpeechRate(0.88f)
            tts?.setPitch(1.0f)

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                }

                override fun onDone(utteranceId: String?) {
                    _isSpeaking.value = false
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                }
            })

            // Cache available offline voices for South Asian languages
            cacheOfflineVoices()
        }
    }

    private fun cacheOfflineVoices() {
        try {
            val teLocale = Locale.forLanguageTag("te-IN")
            val teAvail = tts?.isLanguageAvailable(teLocale) ?: TextToSpeech.LANG_NOT_SUPPORTED
            _isTeluguTtsAvailable.value = (teAvail >= TextToSpeech.LANG_AVAILABLE)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val voices = tts?.voices
                if (voices != null) {
                    // Telugu: Find offline downloaded voice first, or highest quality voice
                    cachedTeluguVoice = voices.firstOrNull { voice ->
                        voice.locale.language.equals("te", ignoreCase = true) && !voice.isNetworkConnectionRequired
                    } ?: voices.firstOrNull { voice ->
                        voice.locale.language.equals("te", ignoreCase = true)
                    }

                    // English: Indian English offline voice
                    cachedEnglishVoice = voices.firstOrNull { voice ->
                        voice.locale.language.equals("en", ignoreCase = true) &&
                                voice.locale.country.equals("IN", ignoreCase = true) &&
                                !voice.isNetworkConnectionRequired
                    } ?: voices.firstOrNull { voice ->
                        voice.locale.language.equals("en", ignoreCase = true) && !voice.isNetworkConnectionRequired
                    } ?: voices.firstOrNull { voice ->
                        voice.locale.language.equals("en", ignoreCase = true)
                    }

                    // Hindi: Offline voice
                    cachedHindiVoice = voices.firstOrNull { voice ->
                        voice.locale.language.equals("hi", ignoreCase = true) && !voice.isNetworkConnectionRequired
                    } ?: voices.firstOrNull { voice ->
                        voice.locale.language.equals("hi", ignoreCase = true)
                    }

                    // Kannada: Offline voice
                    cachedKannadaVoice = voices.firstOrNull { voice ->
                        voice.locale.language.equals("kn", ignoreCase = true) && !voice.isNetworkConnectionRequired
                    } ?: voices.firstOrNull { voice ->
                        voice.locale.language.equals("kn", ignoreCase = true)
                    }

                    if (cachedTeluguVoice != null) {
                        _isTeluguTtsAvailable.value = true
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Speaks text using the fine-tuned in-built TTS engine.
     * Guaranteed clean audio with natural cadence, offline voice prioritization,
     * and human Telugu number pronunciation.
     */
    fun speak(textTe: String, textEn: String, isTelugu: Boolean) {
        if (!_isTtsReady.value || tts == null) return

        try {
            stopSpeaking()

            if (isTelugu && (_isTeluguTtsAvailable.value || cachedTeluguVoice != null)) {
                val naturalTelugu = convertNumbersToTeluguWords(textTe)
                val cleanTe = sanitizeForSpeech(naturalTelugu)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && cachedTeluguVoice != null) {
                    tts?.voice = cachedTeluguVoice
                } else {
                    tts?.setLanguage(Locale.forLanguageTag("te-IN"))
                }
                tts?.setSpeechRate(0.88f) // Relaxed human pace for Telugu
                tts?.setPitch(1.0f)
                tts?.speak(cleanTe, TextToSpeech.QUEUE_FLUSH, null, "DhanamTtsMsgTe")
            } else {
                val cleanEn = sanitizeForSpeech(textEn)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && cachedEnglishVoice != null) {
                    tts?.voice = cachedEnglishVoice
                } else {
                    tts?.setLanguage(Locale.forLanguageTag("en-IN"))
                }
                tts?.setSpeechRate(0.92f)
                val toSpeak = if (cleanEn.isNotBlank()) cleanEn else sanitizeForSpeech(textTe)
                tts?.speak(toSpeak, TextToSpeech.QUEUE_FLUSH, null, "DhanamTtsMsgEn")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopSpeaking() {
        try {
            tts?.stop()
            _isSpeaking.value = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun acquireAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (audioFocusRequest == null) {
                    val attributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                    audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                        .setAudioAttributes(attributes)
                        .setAcceptsDelayedFocusGain(true)
                        .setOnAudioFocusChangeListener { focusChange ->
                            when (focusChange) {
                                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
                                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                                    // Transient notification alert - recognizer continues listening with ducked audio
                                }
                                AudioManager.AUDIOFOCUS_GAIN -> {
                                    // Regained full focus
                                }
                                AudioManager.AUDIOFOCUS_LOSS -> {
                                    stopSpeaking()
                                }
                            }
                        }
                        .build()
                }
                audioFocusRequest?.let { audioManager?.requestAudioFocus(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager?.requestAudioFocus(
                    null,
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun releaseAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager?.abandonAudioFocus(null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isEmulator(): Boolean {
        val fp = Build.FINGERPRINT.lowercase(Locale.ROOT)
        val model = Build.MODEL.lowercase(Locale.ROOT)
        val manufacturer = Build.MANUFACTURER.lowercase(Locale.ROOT)
        val brand = Build.BRAND.lowercase(Locale.ROOT)
        val device = Build.DEVICE.lowercase(Locale.ROOT)
        val product = Build.PRODUCT.lowercase(Locale.ROOT)
        val hardware = Build.HARDWARE.lowercase(Locale.ROOT)
        return fp.startsWith("generic") || fp.startsWith("unknown")
                || model.contains("google_sdk") || model.contains("emulator") || model.contains("android sdk built for")
                || manufacturer.contains("genymotion")
                || (brand.startsWith("generic") && device.startsWith("generic"))
                || product == "google_sdk" || product.contains("sdk_gphone") || product.contains("vbox")
                || hardware.contains("goldfish") || hardware.contains("ranchu")
    }

    /**
     * Feed direct speech transcript into the agent engine (essential for virtual devices / emulators
     * where host mic audio is not piped into Android container, as well as fast one-tap testing).
     */
    fun simulateVoiceInput(transcript: String) {
        mainHandler.post {
            stopSpeaking()
            val clean = VoicePostProcessor.filterTranscript(transcript, activeLanguageIsTelugu)
            _partialTranscript.value = clean
            _speechError.value = null
            _isListening.value = false
            releaseAudioFocus()
            currentResultCallback?.invoke(clean)
        }
    }

    /**
     * Start in-built Speech-to-Text recognition using Android's SpeechRecognizer.
     * When offline: configures EXTRA_PREFER_OFFLINE = true to prioritize downloaded packages.
     * When online: allows network recognition for high-accuracy Telugu & English transcription.
     */
    fun startListening(
        isTelugu: Boolean,
        onResult: (String) -> Unit,
        onError: ((String) -> Unit)? = null
    ) {
        mainHandler.post {
            stopSpeaking()
            activeLanguageIsTelugu = isTelugu
            currentResultCallback = onResult
            currentErrorCallback = onError
            _isListening.value = true
            _speechError.value = null
            _partialTranscript.value = ""
            hasRetriedOnlineFallback = false
            hasRetriedClientFallback = false

            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                _isListening.value = false
                val msg = if (isTelugu)
                    "ఈ పరికరంలో వాయిస్ రికగ్నిషన్ సేవ అందుబాటులో లేదు. వర్చువల్ ఎమ్యులేటర్‌లో కింద ఉన్న 'వాయిస్ టెస్ట్' చిప్స్ నొక్కండి."
                else
                    "Speech recognition service unavailable on this device. Use Quick Voice Test chips below in virtual device."
                _speechError.value = msg
                onError?.invoke(msg)
                return@post
            }

            try {
                try {
                    speechRecognizer?.cancel()
                    speechRecognizer?.destroy()
                } catch (e: Exception) {
                    // ignore
                }

                speechRecognizer = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                        SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                    } else {
                        SpeechRecognizer.createSpeechRecognizer(context)
                    }
                } catch (e: Exception) {
                    SpeechRecognizer.createSpeechRecognizer(context)
                }

                speechRecognizer?.setRecognitionListener(createRecognitionListener())
                startListeningInternal()
            } catch (e: Exception) {
                _isListening.value = false
                val err = "Speech recognizer error: ${e.localizedMessage}"
                _speechError.value = err
                onError?.invoke(err)
            }
        }
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _isListening.value = true
                _speechError.value = null
            }

            override fun onBeginningOfSpeech() {
                _isListening.value = true
            }

            override fun onRmsChanged(rmsdB: Float) {
                val now = System.currentTimeMillis()
                val clamped = rmsdB.coerceAtLeast(0f)
                if (now - lastRmsUpdateTime >= 65 || abs(clamped - lastRmsDb) > 1.2f) {
                    lastRmsUpdateTime = now
                    lastRmsDb = clamped
                    _rmsDb.value = clamped
                }
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                // User paused speaking. Keep listening state true while recognizer computes onResults
            }

            override fun onError(errorCode: Int) {
                // Handle retry fallback for on-device recognition failures
                if (!hasRetriedClientFallback && (errorCode == SpeechRecognizer.ERROR_CLIENT || errorCode == SpeechRecognizer.ERROR_SERVER)) {
                    hasRetriedClientFallback = true
                    try {
                        speechRecognizer?.cancel()
                        speechRecognizer?.destroy()
                        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                        speechRecognizer?.setRecognitionListener(this)
                        startListeningInternal()
                        return
                    } catch (_: Exception) {}
                }

                _isListening.value = false
                releaseAudioFocus()

                val errorMsg = when (errorCode) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                        if (isEmulator()) {
                            if (activeLanguageIsTelugu)
                                "వర్చువల్ ఎమ్యులేటర్‌లో మైక్ ఆడియో నేరుగా రాదు. కింద ఉన్న 'వాయిస్ టెస్ట్ (Quick Voice)' చిప్స్ నొక్కండి!"
                            else
                                "Virtual device has no microphone stream. Tap any 'Quick Voice Test' chip below!"
                        } else {
                            if (activeLanguageIsTelugu) "వాయిస్ వినపడలేదు. మైక్ దగ్గరగా మాట్లాడండి."
                            else "No speech match detected. Please speak closer to mic."
                        }

                    SpeechRecognizer.ERROR_AUDIO ->
                        if (isEmulator()) {
                            if (activeLanguageIsTelugu) "ఎమ్యులేటర్‌లో ఆడియో ఇన్‌పుట్ లేదు. కింద ఉన్న వాయిస్ టెస్ట్ బటన్ ఉపయోగించండి."
                            else "Audio input unavailable in virtual device. Use Quick Voice Test chips below."
                        } else {
                            if (activeLanguageIsTelugu) "ఆడియో సమస్య. మైక్ అనుమతి తనిఖీ చేయండి."
                            else "Audio error. Check mic permission."
                        }

                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                        if (activeLanguageIsTelugu) "మైక్రోఫోన్ అనుమతి అవసరం."
                        else "Microphone permission is required."

                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                        if (!isOnline()) {
                            if (activeLanguageIsTelugu)
                                "ఆఫ్‌లైన్ వాయిస్ గుర్తించబడలేదు. గూగుల్ సెట్టింగ్స్‌లో 'ఆఫ్‌లైన్ స్పీచ్ రికగ్నిషన్' (తెలుగు / ఇంగ్లీష్) ప్యాక్ డౌన్‌లోడ్ చేసుకుంటే నెట్ లేకుండా మాట్లాడవచ్చు."
                            else
                                "Offline speech model not detected. Download 'Offline Speech Recognition' (English / Telugu) in phone settings to use voice without internet."
                        } else {
                            if (activeLanguageIsTelugu) "నెట్‌వర్క్ సమస్య లేదా గూగుల్ వాయిస్ అందుబాటులో లేదు."
                            else "Network connection or Google Voice service needed."
                        }

                    SpeechRecognizer.ERROR_CLIENT ->
                        if (isEmulator()) {
                            if (activeLanguageIsTelugu) "వర్చువల్ డివైస్: కింద ఉన్న 'వాయిస్ టెస్ట్' చిప్స్ నొక్కి వెంటనే పరీక్షించండి."
                            else "Virtual device: Tap any 'Quick Voice Test' chip below to test immediately."
                        } else {
                            if (activeLanguageIsTelugu) "వాయిస్ రికగ్నిషన్ సమస్య. కింద ఉన్న గూగుల్ వాయిస్ బటన్ నొక్కండి."
                            else "Speech service issue. Tap Google Voice below."
                        }

                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                        cancelListening()
                        try {
                            speechRecognizer?.destroy()
                            speechRecognizer = null
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        if (activeLanguageIsTelugu) "వాయిస్ సేవ బిజీగా ఉంది. మళ్లీ మైక్ నొక్కండి."
                        else "Speech service busy. Tap mic to retry."
                    }

                    else ->
                        if (activeLanguageIsTelugu) "వాయిస్ ప్రాసెస్ చేయలేకపోయాను. మళ్లీ మైక్ నొక్కండి."
                        else "Could not process voice. Tap mic to retry."
                }
                _speechError.value = errorMsg
                currentErrorCallback?.invoke(errorMsg)
            }

            override fun onResults(results: Bundle?) {
                _isListening.value = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val rawText = matches?.firstOrNull()?.trim()
                if (!rawText.isNullOrBlank()) {
                    val cleanText = VoicePostProcessor.filterTranscript(rawText, activeLanguageIsTelugu)
                    _partialTranscript.value = cleanText
                    currentResultCallback?.invoke(cleanText)
                } else {
                    val msg = if (activeLanguageIsTelugu) "మాటలు గుర్తించబడలేదు." else "No words recognized."
                    _speechError.value = msg
                    currentErrorCallback?.invoke(msg)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val rawText = matches?.firstOrNull()?.trim()
                if (!rawText.isNullOrBlank()) {
                    val cleanText = VoicePostProcessor.filterTranscript(rawText, activeLanguageIsTelugu)
                    _partialTranscript.value = cleanText
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    private fun startListeningInternal() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)

                val primaryLang = if (activeLanguageIsTelugu) "te-IN" else "en-IN"
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, primaryLang)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, primaryLang)

                // Only prefer offline when network is disconnected!
                // When online, do NOT force offline so Google Cloud Speech service works reliably on all devices/emulators!
                if (!isOnline()) {
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val secondaryLang = if (activeLanguageIsTelugu) "en-IN" else "te-IN"
                    putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf(secondaryLang))
                }

                // Pass silence limits as Long
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L)

                putExtra(RecognizerIntent.EXTRA_PROMPT, if (activeLanguageIsTelugu) "ఖర్చు లేదా అప్పు వివరాలు చెప్పండి..." else "Speak your expense or loan...")
            }

            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            if (!hasRetriedClientFallback) {
                hasRetriedClientFallback = true
                try {
                    speechRecognizer?.cancel()
                    speechRecognizer?.destroy()
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                    speechRecognizer?.setRecognitionListener(createRecognitionListener())
                    startListeningInternal()
                    return
                } catch (_: Exception) {}
            }
            _isListening.value = false
        }
    }

    /**
     * Submit voice input immediately when user taps Done or Stop button,
     * without waiting for the 5-second silence timeout.
     */
    fun submitListeningImmediately() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            // Give recognizer up to 450ms to fire onResults, otherwise finalize with partial transcript
            val partial = _partialTranscript.value.trim()
            if (partial.isNotBlank()) {
                mainHandler.postDelayed({
                    val cb = currentResultCallback
                    if (cb != null && _isListening.value) {
                        currentResultCallback = null
                        _isListening.value = false
                        releaseAudioFocus()
                        val cleanPartial = VoicePostProcessor.filterTranscript(partial, activeLanguageIsTelugu)
                        cb.invoke(cleanPartial)
                    }
                }, 450)
            }
        }
    }

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

    fun createFallbackRecognizerIntent(isTelugu: Boolean): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            if (!isOnline()) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
            val primaryLang = if (isTelugu) "te-IN" else "en-IN"
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, primaryLang)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, primaryLang)
            putExtra(
                RecognizerIntent.EXTRA_PROMPT,
                if (isTelugu) "మీ ఖర్చు లేదా అప్పు వివరాలు చెప్పండి (ఉదా: కూరగాయలు 250)..."
                else "Speak your expense or loan details (e.g. Groceries 250)..."
            )
        }
    }

    fun isOnline(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val net = cm?.activeNetwork ?: return false
                val caps = cm.getNetworkCapabilities(net) ?: return false
                caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } else {
                @Suppress("DEPRECATION")
                cm?.activeNetworkInfo?.isConnected == true
            }
        } catch (_: Exception) {
            false
        }
    }

    fun openOfflineSpeechSettings(context: Context) {
        try {
            val intent = Intent("android.settings.VOICE_INPUT_SETTINGS").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val intent = Intent("com.google.android.googlequicksearchbox.action.VOICE_SETTINGS").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                try {
                    val intent = Intent(android.provider.Settings.ACTION_LOCALE_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {}
            }
        }
    }

    private fun sanitizeForSpeech(text: String): String {
        return text
            .replace("₹", " ")
            .replace("✅", "")
            .replace("⚠️", "")
            .replace("🗣", "")
            .replace("💡", "")
            .replace("🔒", "")
            .replace("➕", "")
            .replace("📊", "")
            .replace("•", " ")
            .replace(Regex("""[^\p{L}\p{N}\p{P}\s]"""), "")
            .trim()
    }

    /**
     * Converts numbers in text to natural Telugu words (e.g. 450 -> నాలుగు వందల యాభై, 35000 -> ముప్పై ఐదు వేలు).
     * This radically improves Telugu Text-to-Speech naturalness and clarity across offline TTS engines.
     */
    private fun convertNumbersToTeluguWords(text: String): String {
        val numberRegex = Regex("""\b(\d+)\b""")
        return numberRegex.replace(text) { matchResult ->
            val numStr = matchResult.groupValues[1]
            val numLong = numStr.toLongOrNull() ?: return@replace numStr
            spellTeluguNumber(numLong)
        }
    }

    private fun spellTeluguNumber(num: Long): String {
        if (num == 0L) return "సున్నా"
        if (num < 0L) return "మైనస్ " + spellTeluguNumber(-num)

        val units = arrayOf(
            "", "ఒకటి", "రెండు", "మూడు", "నాలుగు", "ఐదు",
            "ఆరు", "ఏడు", "ఎనిమిది", "తొమ్మిది", "పది",
            "పదకొండు", "పన్నెండు", "పదమూడు", "పద్నాలుగు", "పదిహేను",
            "పదహారు", "పదిహేడు", "పద్దెనిమిది", "పందొమ్మిది"
        )

        val tens = arrayOf(
            "", "", "ఇరవై", "ముప్పై", "నలభై", "యాభై",
            "అరవై", "డెబ్బై", "ఎనభై", "తొంబై"
        )

        val hundredsPrefix = arrayOf(
            "", "వంద", "రెండు వందల", "మూడు వందల", "నాలుగు వందల",
            "ఐదు వందల", "ఆరు వందల", "ఏడు వందల", "ఎనిమిది వందల", "తొమ్మిది వందల"
        )

        return when {
            num < 20 -> units[num.toInt()]
            num < 100 -> {
                val t = (num / 10).toInt()
                val rem = (num % 10).toInt()
                if (rem == 0) tens[t] else "${tens[t]} ${units[rem]}"
            }
            num < 1000 -> {
                val h = (num / 100).toInt()
                val rem = num % 100
                val hStr = if (rem == 0L && h == 1) "వంద" else hundredsPrefix[h]
                if (rem == 0L) {
                    if (h == 1) "వంద" else "${units[h]} వందలు"
                } else {
                    "$hStr ${spellTeluguNumber(rem)}"
                }
            }
            num < 100000 -> {
                val th = num / 1000
                val rem = num % 1000
                val thStr = when (th) {
                    1L -> "వెయ్యి"
                    else -> "${spellTeluguNumber(th)} వేల"
                }
                if (rem == 0L) {
                    if (th == 1L) "వెయ్యి" else "${spellTeluguNumber(th)} వేలు"
                } else {
                    "$thStr ${spellTeluguNumber(rem)}"
                }
            }
            num < 10000000 -> {
                val lk = num / 100000
                val rem = num % 100000
                val lkStr = if (lk == 1L) "ఒక లక్ష" else "${spellTeluguNumber(lk)} లక్షల"
                if (rem == 0L) {
                    if (lk == 1L) "లక్ష" else "${spellTeluguNumber(lk)} లక్షలు"
                } else {
                    "$lkStr ${spellTeluguNumber(rem)}"
                }
            }
            else -> num.toString()
        }
    }

    fun shutdown() {
        mainHandler.post {
            try {
                stopSpeaking()
                tts?.shutdown()
                tts = null
            } catch (e: Exception) {
                e.printStackTrace()
            }

            try {
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
