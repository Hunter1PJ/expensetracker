package com.suguru.expensetracker.data.ai.gemini

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.suguru.expensetracker.domain.ai.AiConfiguration
import com.suguru.expensetracker.domain.ai.AiError
import com.suguru.expensetracker.domain.ai.AiResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Direct HTTPS client for Gemini generateContent endpoint supporting user-supplied API keys (BYOK).
 * Does not leak API keys into URLs, logs, or error exceptions.
 */
class GeminiApiClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build(),
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
) {

    private val requestAdapter = moshi.adapter(GeminiGenerateContentRequest::class.java)
    private val responseAdapter = moshi.adapter(GeminiGenerateContentResponse::class.java)

    /**
     * Executes generateContent for the given model and API key.
     * Passes the API key via `x-goog-api-key` header to prevent URL leak in logs or proxies.
     */
    suspend fun generateContent(
        apiKey: String,
        model: String,
        requestBody: GeminiGenerateContentRequest
    ): AiResult<String> = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isBlank()) {
            return@withContext AiResult.Failure(AiError.MissingApiKey)
        }

        val jsonPayload = try {
            requestAdapter.toJson(requestBody)
        } catch (e: Exception) {
            return@withContext AiResult.Failure(AiError.ExecutionError("Request serialization error: ${e.message}"))
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
        val request = Request.Builder()
            .url(url)
            .addHeader("x-goog-api-key", trimmedKey)
            .addHeader("Content-Type", "application/json; charset=utf-8")
            .post(jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""

                when (response.code) {
                    200 -> {
                        val parsed = responseAdapter.fromJson(responseBody)
                        val text = parsed?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                        if (text != null) {
                            AiResult.Success(text)
                        } else {
                            AiResult.Failure(AiError.MalformedResponse("Empty candidate or response structure"))
                        }
                    }
                    400 -> {
                        // Could be bad request or invalid key format
                        if (responseBody.contains("API_KEY_INVALID", ignoreCase = true) ||
                            responseBody.contains("key not valid", ignoreCase = true)) {
                            AiResult.Failure(AiError.InvalidApiKey)
                        } else {
                            AiResult.Failure(AiError.ServerError(400, "Bad request to Gemini API"))
                        }
                    }
                    401, 403 -> {
                        AiResult.Failure(AiError.InvalidApiKey)
                    }
                    429 -> {
                        val retryHeader = response.header("Retry-After")?.toLongOrNull()
                        AiResult.Failure(AiError.RateLimitExceeded(retryHeader))
                    }
                    in 500..599 -> {
                        AiResult.Failure(AiError.ServerError(response.code, "Gemini service temporarily unavailable"))
                    }
                    else -> {
                        AiResult.Failure(AiError.ServerError(response.code, "HTTP ${response.code} error received"))
                    }
                }
            }
        } catch (e: IOException) {
            AiResult.Failure(AiError.NetworkError(e.message ?: "Network connection failed"))
        } catch (e: Exception) {
            AiResult.Failure(AiError.ExecutionError(e.message ?: "Unexpected error during execution"))
        }
    }
}
