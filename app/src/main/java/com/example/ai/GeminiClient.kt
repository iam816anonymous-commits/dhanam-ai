package com.example.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Result type retained for the existing assistant call sites.
 * No implementation in this module is permitted to access a network service.
 */
sealed class GeminiCallResult {
    data class Success(val text: String) : GeminiCallResult()
    data object NoKey : GeminiCallResult()
    data class NetworkError(val message: String) : GeminiCallResult()
    data class HttpError(val code: Int, val message: String) : GeminiCallResult()
    data class ParseError(val message: String) : GeminiCallResult()
}

interface FinancialAiClient {
    suspend fun generateContent(
        prompt: String,
        model: String = GeminiService.DEFAULT_MODEL
    ): GeminiCallResult
}

/**
 * Safe offline placeholder. Common expense, loan, budget and navigation commands are handled
 * by ExpenseAgentEngine's local parsers. Unrecognized input must fail closed instead of sending
 * financial text to a cloud provider. A future local model can implement this interface.
 */
class DefaultFinancialAiClient : FinancialAiClient {
    override suspend fun generateContent(prompt: String, model: String): GeminiCallResult =
        withContext(Dispatchers.Default) {
            if (prompt.isBlank()) {
                GeminiCallResult.ParseError("Prompt must not be blank")
            } else {
                GeminiCallResult.NetworkError(
                    "This request could not be understood by the on-device rules. No data was sent. " +
                        "Try a specific command such as 'tea 30' or 'set budget to 20000'."
                )
            }
        }
}

object GeminiService {
    const val DEFAULT_MODEL = "local-rules-v1"
    val ALLOWED_MODELS = setOf(DEFAULT_MODEL)

    @Volatile
    var currentClient: FinancialAiClient = DefaultFinancialAiClient()
        private set

    @Volatile
    private var nanoClient: MlKitGeminiNanoClient? = null

    @Volatile
    private var activeModel: String = DEFAULT_MODEL

    private val providerLock = Any()
    @Volatile
    private var providerGeneration: Long = 0L

    fun getActiveModel(): String = activeModel

    /**
     * Probes the device and activates Gemini Nano only when the runtime confirms AVAILABLE.
     * No model download and no cloud fallback are initiated here.
     */
    suspend fun enableOnDeviceGeminiNano(): NanoAvailability {
        val (candidate, requestGeneration) = synchronized(providerLock) {
            providerGeneration += 1
            val client = nanoClient ?: MlKitGeminiNanoClient().also { nanoClient = it }
            client to providerGeneration
        }
        val availability = candidate.probeAvailability()
        synchronized(providerLock) {
            // A later explicit choice (especially "Use local rules only") wins over stale async work.
            if (providerGeneration != requestGeneration || nanoClient !== candidate) {
                return NanoAvailability.Unknown
            }
            if (availability == NanoAvailability.Available) {
                currentClient = candidate
                activeModel = "gemini-nano-on-device"
            } else if (currentClient === candidate) {
                currentClient = DefaultFinancialAiClient()
                activeModel = DEFAULT_MODEL
            }
        }
        return availability
    }

    /**
     * Download Gemini Nano only after an explicit user action. The provider becomes active only
     * when the runtime confirms that the model is AVAILABLE after the download flow completes.
     */
    suspend fun downloadOnDeviceGeminiNano(): NanoAvailability {
        val (candidate, requestGeneration) = synchronized(providerLock) {
            providerGeneration += 1
            val client = nanoClient ?: MlKitGeminiNanoClient().also { nanoClient = it }
            client to providerGeneration
        }
        val availability = candidate.downloadModel()
        synchronized(providerLock) {
            if (providerGeneration != requestGeneration || nanoClient !== candidate) {
                return NanoAvailability.Unknown
            }
            if (availability == NanoAvailability.Available) {
                currentClient = candidate
                activeModel = "gemini-nano-on-device"
            }
        }
        return availability
    }

    /** Return to deterministic local rules and release the optional on-device model client. */
    fun useLocalRulesOnly() {
        val previous = synchronized(providerLock) {
            providerGeneration += 1
            currentClient = DefaultFinancialAiClient()
            activeModel = DEFAULT_MODEL
            nanoClient.also { nanoClient = null }
        }
        // Close outside the lock; an in-flight availability/download result cannot reactivate it
        // because its generation token is now stale.
        previous?.close()
    }

    fun validateModelName(model: String): String = DEFAULT_MODEL

    internal suspend fun generateOnDevice(
        prompt: String,
        model: String = DEFAULT_MODEL
    ): GeminiCallResult = currentClient.generateContent(prompt, DEFAULT_MODEL)

    suspend fun callGeminiDetailed(
        prompt: String,
        model: String = DEFAULT_MODEL
    ): GeminiCallResult = generateOnDevice(prompt, model)

    suspend fun callGemini(prompt: String): String? =
        when (val result = currentClient.generateContent(prompt, DEFAULT_MODEL)) {
            is GeminiCallResult.Success -> result.text
            else -> null
        }
}
