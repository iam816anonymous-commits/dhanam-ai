package com.example.ai

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FinancialAiClientTest {

    class MockFinancialAiClient(private val mockResult: GeminiCallResult) : FinancialAiClient {
        override suspend fun generateContent(prompt: String, model: String): GeminiCallResult {
            return mockResult
        }
    }

    @Test
    fun testFinancialAiClientAbstractionNoKey() = runTest {
        val client = MockFinancialAiClient(GeminiCallResult.NoKey)
        val result = client.generateContent("test prompt")
        assertTrue(result is GeminiCallResult.NoKey)
    }

    @Test
    fun testFinancialAiClientAbstractionSuccess() = runTest {
        val client = MockFinancialAiClient(GeminiCallResult.Success("{\"type\": \"EXPENSE\"}"))
        val result = client.generateContent("test prompt")
        assertTrue(result is GeminiCallResult.Success)
        assertEquals("{\"type\": \"EXPENSE\"}", (result as GeminiCallResult.Success).text)
    }

    @Test
    fun testModelValidationAllowlistAndRemoteConfig() {
        // Reset remote model
        GeminiService.setRemoteModelConfig(null)
        assertEquals("gemini-2.5-flash", GeminiService.getActiveModel())

        // Valid remote model config
        GeminiService.setRemoteModelConfig("gemini-2.0-flash")
        assertEquals("gemini-2.0-flash", GeminiService.getActiveModel())

        // Invalid remote model config should be rejected and fall back to last-known good model
        GeminiService.setRemoteModelConfig("unauthorized-malicious-model")
        assertEquals("gemini-2.0-flash", GeminiService.getActiveModel())

        assertEquals("gemini-2.0-flash", GeminiService.validateModelName("invalid-model-name"))
    }
    @Test
    fun blankPromptIsRejectedBeforeFirebaseInitialization() = runTest {
        val result = GeminiService.generateWithFirebaseAi("   ")
        assertTrue(result is GeminiCallResult.ParseError)
    }

}
