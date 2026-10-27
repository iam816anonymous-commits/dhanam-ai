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
    fun defaultClientFailsClosedWithoutSendingInputToCloud() = runTest {
        val result = DefaultFinancialAiClient().generateContent("my salary is 50000")
        assertTrue(result is GeminiCallResult.NetworkError)
        assertTrue((result as GeminiCallResult.NetworkError).message.contains("No data was sent"))
    }

    @Test
    fun blankPromptIsRejected() = runTest {
        val result = DefaultFinancialAiClient().generateContent("   ")
        assertTrue(result is GeminiCallResult.ParseError)
    }
}
