package com.suguru.expensetracker.data.ai.gemini

import com.suguru.expensetracker.data.security.SecureKeyStorage
import com.suguru.expensetracker.domain.ai.AiConfiguration
import com.suguru.expensetracker.domain.ai.AiModelDefaults
import com.suguru.expensetracker.domain.ai.AiResult
import com.suguru.expensetracker.domain.ai.ExtractedFinancialPayload
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test

class GeminiAiProviderTest {

    @Test
    fun validationAndExtractionUseTheConfiguredGeminiModel() = runTest {
        val requestPaths = mutableListOf<String>()
        val httpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                requestPaths += request.url.encodedPath
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(
                        """{"candidates":[{"content":{"parts":[{"text":"{\"items\":[]}"}]}}]}"""
                            .toResponseBody("application/json".toMediaType())
                    )
                    .build()
            }
            .build()
        val keyStorage = object : SecureKeyStorage {
            override fun saveKey(key: String) = Unit
            override fun getKey(): String = "fixture-value"
            override fun deleteKey() = Unit
            override fun hasKey(): Boolean = true
        }
        val provider = GeminiAiProvider(keyStorage, GeminiApiClient(client = httpClient))

        assertEquals("gemini-3.5-flash-lite", AiModelDefaults.GEMINI_MODEL_ID)
        assertEquals(AiResult.Success(true), provider.validateKey("fixture-value"))
        assertEquals(
            AiResult.Success(ExtractedFinancialPayload(emptyList())),
            provider.extractFinancialTransactions("test transaction", AiConfiguration())
        )
        assertEquals(
            listOf(
                "/v1beta/models/${AiModelDefaults.GEMINI_MODEL_ID}:generateContent",
                "/v1beta/models/${AiModelDefaults.GEMINI_MODEL_ID}:generateContent"
            ),
            requestPaths
        )
    }
}