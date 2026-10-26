package com.example.ai

import com.example.BuildConfig
import com.example.util.AppLogger
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeBackend
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class GeminiCallResult {
    data class Success(val text: String) : GeminiCallResult()
    data object NoKey : GeminiCallResult()
    data class NetworkError(val message: String) : GeminiCallResult()
    data class HttpError(val code: Int, val message: String) : GeminiCallResult()
    data class ParseError(val message: String) : GeminiCallResult()
}

/**
 * Application-facing contract for financial AI. The implementation uses Firebase AI Logic;
 * no Gemini REST API key is read from BuildConfig or sent by the Android client.
 */
interface FinancialAiClient {
    suspend fun generateContent(
        prompt: String,
        model: String = GeminiService.DEFAULT_MODEL
    ): GeminiCallResult
}

class DefaultFinancialAiClient : FinancialAiClient {
    override suspend fun generateContent(prompt: String, model: String): GeminiCallResult =
        GeminiService.generateWithFirebaseAi(prompt, model)
}

object GeminiService {
    private const val TAG = "GeminiService"
    private const val REMOTE_MODEL_KEY = "financial_ai_model"

    const val DEFAULT_MODEL = "gemini-2.5-flash"
    val ALLOWED_MODELS = setOf("gemini-2.5-flash", "gemini-2.0-flash", "gemini-1.5-flash")

    @Volatile
    private var cachedRemoteModel: String? = null

    /**
     * Initializes real Firebase Remote Config. The app remains usable with a safe default if
     * Firebase is not configured or the fetch fails. Only allowlisted model IDs are accepted.
     */
    fun initializeRemoteConfig() {
        try {
            val remoteConfig = FirebaseRemoteConfig.getInstance()
            val settings = FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(if (BuildConfig.DEBUG) 0 else 3600)
                .build()
            remoteConfig.setConfigSettingsAsync(settings)
            remoteConfig.setDefaultsAsync(mapOf(REMOTE_MODEL_KEY to DEFAULT_MODEL))
                .addOnCompleteListener { defaultsTask ->
                    if (!defaultsTask.isSuccessful) {
                        AppLogger.w(TAG, "Remote Config defaults could not be applied; retaining safe model default")
                    }
                    remoteConfig.fetchAndActivate().addOnCompleteListener { fetchTask ->
                        if (fetchTask.isSuccessful) {
                            setRemoteModelConfig(remoteConfig.getString(REMOTE_MODEL_KEY))
                        } else {
                            AppLogger.w(TAG, "Remote Config fetch failed; retaining safe model default")
                        }
                    }
                }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Remote Config unavailable; retaining safe model default", e)
        }
    }

    /** Accepts a validated model value; retained as a unit-test seam. */
    fun setRemoteModelConfig(model: String?) {
        if (!model.isNullOrBlank() && ALLOWED_MODELS.contains(model)) {
            cachedRemoteModel = model
            AppLogger.i(TAG, "Accepted allowlisted AI model configuration")
        } else if (!model.isNullOrBlank()) {
            AppLogger.w(TAG, "Rejected model configuration outside allowlist")
        }
    }

    fun getActiveModel(): String = cachedRemoteModel ?: DEFAULT_MODEL

    fun validateModelName(model: String): String =
        if (ALLOWED_MODELS.contains(model)) model else getActiveModel()

    internal suspend fun generateWithFirebaseAi(
        prompt: String,
        model: String = getActiveModel()
    ): GeminiCallResult = withContext(Dispatchers.IO) {
        if (prompt.isBlank()) {
            return@withContext GeminiCallResult.ParseError("Prompt must not be blank")
        }

        val safeModel = validateModelName(model)
        try {
            val generativeModel = Firebase.ai(
                backend = GenerativeBackend.googleAI()
            ).generativeModel(
                modelName = safeModel,
                generationConfig = generationConfig {
                    temperature = 0.1f
                    responseMimeType = "application/json"
                }
            )
            val response = generativeModel.generateContent(prompt)
            val responseText = response.text
            if (responseText.isNullOrBlank()) {
                GeminiCallResult.ParseError("Received empty candidate text from Firebase AI")
            } else {
                GeminiCallResult.Success(responseText)
            }
        } catch (e: Exception) {
            // Do not log prompts, model output, or financial data.
            AppLogger.w(TAG, "Firebase AI request failed (" + e.javaClass.simpleName + ")")
            GeminiCallResult.NetworkError("AI service unavailable. Please try again.")
        }
    }

    suspend fun callGeminiDetailed(
        prompt: String,
        model: String = getActiveModel()
    ): GeminiCallResult = generateWithFirebaseAi(prompt, model)

    suspend fun callGemini(prompt: String): String? =
        when (val result = DefaultFinancialAiClient().generateContent(prompt, getActiveModel())) {
            is GeminiCallResult.Success -> result.text
            else -> null
        }
}
