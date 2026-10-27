package com.example.ai

import com.google.mlkit.genai.prompt.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Optional Gemini Nano provider backed by Android AICore through ML Kit Prompt API.
 *
 * This provider never uses an API key and does not call a cloud AI endpoint directly.
 * Model availability is queried at runtime; inference is allowed only when the model is AVAILABLE.
 * It deliberately does not start a model download automatically.
 */
class MlKitGeminiNanoClient(
    private val model: GenerativeModel = Generation.getClient()
) : FinancialAiClient, AutoCloseable {

    suspend fun probeAvailability(): NanoAvailability = withContext(Dispatchers.IO) {
        runCatching { model.checkStatus() }
            .fold(
                onSuccess = { status ->
                    when (status) {
                        FeatureStatus.AVAILABLE -> NanoAvailability.Available
                        FeatureStatus.DOWNLOADABLE -> NanoAvailability.DownloadRequired
                        FeatureStatus.DOWNLOADING -> NanoAvailability.Downloading
                        FeatureStatus.UNAVAILABLE -> NanoAvailability.Unsupported
                        else -> NanoAvailability.Unknown
                    }
                },
                onFailure = { NanoAvailability.Error(it.message ?: "Unable to check Gemini Nano availability") }
            )
    }

    override suspend fun generateContent(
        prompt: String,
        model: String
    ): GeminiCallResult = withContext(Dispatchers.IO) {
        if (prompt.isBlank()) {
            return@withContext GeminiCallResult.ParseError("Prompt must not be blank")
        }

        when (val availability = probeAvailability()) {
            NanoAvailability.Available -> runCatching {
                this@MlKitGeminiNanoClient.model.generateContent(prompt).text
            }.fold(
                onSuccess = { text ->
                    if (text.isNullOrBlank()) {
                        GeminiCallResult.ParseError("On-device model returned an empty response")
                    } else {
                        GeminiCallResult.Success(text)
                    }
                },
                onFailure = {
                    GeminiCallResult.NetworkError(
                        "On-device Gemini Nano inference failed. No cloud fallback was attempted."
                    )
                }
            )

            NanoAvailability.DownloadRequired ->
                GeminiCallResult.NetworkError(
                    "Gemini Nano is supported but its model is not ready. Model download requires explicit user action."
                )

            NanoAvailability.Downloading ->
                GeminiCallResult.NetworkError("Gemini Nano is still preparing. Try again when it is available.")

            NanoAvailability.Unsupported ->
                GeminiCallResult.NetworkError(
                    "Gemini Nano is not available on this device. Existing local rules remain available."
                )

            is NanoAvailability.Error ->
                GeminiCallResult.NetworkError(availability.message)

            NanoAvailability.Unknown ->
                GeminiCallResult.NetworkError("Gemini Nano availability could not be determined.")
        }
    }

    override fun close() {
        model.close()
    }
}

sealed interface NanoAvailability {
    data object Available : NanoAvailability
    data object DownloadRequired : NanoAvailability
    data object Downloading : NanoAvailability
    data object Unsupported : NanoAvailability
    data object Unknown : NanoAvailability
    data class Error(val message: String) : NanoAvailability
}
