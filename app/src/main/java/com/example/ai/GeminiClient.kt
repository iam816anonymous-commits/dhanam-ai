package com.example.ai

import com.example.BuildConfig
import com.example.util.AppLogger
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import java.io.IOException
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    val contents: List<ContentPartContainer>,
    val generationConfig: GenerationConfig? = null
)

@JsonClass(generateAdapter = true)
data class ContentPartContainer(
    val parts: List<TextPart>
)

@JsonClass(generateAdapter = true)
data class TextPart(
    val text: String
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    val temperature: Float = 0.2f,
    val topP: Float = 0.9f,
    val responseMimeType: String? = "application/json"
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    val candidates: List<Candidate>? = null
)

@JsonClass(generateAdapter = true)
data class Candidate(
    val content: ResponseContent? = null
)

@JsonClass(generateAdapter = true)
data class ResponseContent(
    val parts: List<TextPart>? = null
)

sealed class GeminiCallResult {
    data class Success(val text: String) : GeminiCallResult()
    data object NoKey : GeminiCallResult()
    data class NetworkError(val message: String) : GeminiCallResult()
    data class HttpError(val code: Int, val message: String) : GeminiCallResult()
    data class ParseError(val message: String) : GeminiCallResult()
}

interface GeminiApi {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Header("x-goog-api-key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

object GeminiService {
    private const val TAG = "GeminiService"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    // Single configurable model ID constant. Stable fast flash model.
    const val DEFAULT_MODEL = "gemini-2.5-flash"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        })
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val api: GeminiApi = retrofit.create(GeminiApi::class.java)

    /**
     * Executes Gemini inference with typed results, timeout, and exponential backoff retry.
     */
    suspend fun callGeminiDetailed(
        prompt: String,
        model: String = DEFAULT_MODEL
    ): GeminiCallResult = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext GeminiCallResult.NoKey
        }

        val request = GeminiRequest(
            contents = listOf(
                ContentPartContainer(
                    parts = listOf(TextPart(text = prompt))
                )
            ),
            generationConfig = GenerationConfig(
                temperature = 0.1f,
                responseMimeType = "application/json"
            )
        )

        var lastException: Exception? = null
        val maxRetries = 2
        var currentDelayMs = 500L

        for (attempt in 0..maxRetries) {
            try {
                val response = api.generateContent(
                    model = model,
                    apiKey = apiKey,
                    request = request
                )
                val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                return@withContext if (!text.isNullOrBlank()) {
                    GeminiCallResult.Success(text)
                } else {
                    GeminiCallResult.ParseError("Received empty candidate text from Gemini response")
                }
            } catch (httpEx: HttpException) {
                lastException = httpEx
                val code = httpEx.code()
                AppLogger.w(TAG, "Gemini HTTP error $code on attempt ${attempt + 1}", httpEx)
                // Retry only on 429 (Rate Limit) or 5xx (Server Error)
                if ((code == 429 || code in 500..599) && attempt < maxRetries) {
                    delay(currentDelayMs)
                    currentDelayMs *= 2
                    continue
                }
                return@withContext GeminiCallResult.HttpError(code, httpEx.message ?: "HTTP $code")
            } catch (ioEx: IOException) {
                lastException = ioEx
                AppLogger.w(TAG, "Gemini Network/IO error on attempt ${attempt + 1}", ioEx)
                if (attempt < maxRetries) {
                    delay(currentDelayMs)
                    currentDelayMs *= 2
                    continue
                }
                return@withContext GeminiCallResult.NetworkError(ioEx.message ?: "Network timeout or unreachable")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Gemini unexpected failure", e)
                return@withContext GeminiCallResult.ParseError(e.message ?: "Unexpected parsing error")
            }
        }

        return@withContext GeminiCallResult.NetworkError(lastException?.message ?: "Max retries exceeded")
    }

    suspend fun callGemini(prompt: String): String? {
        return when (val result = callGeminiDetailed(prompt)) {
            is GeminiCallResult.Success -> result.text
            else -> null
        }
    }
}
