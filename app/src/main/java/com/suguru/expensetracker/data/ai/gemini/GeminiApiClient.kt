package com.suguru.expensetracker.data.ai.gemini

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.suguru.expensetracker.domain.ai.AiConfiguration
import com.suguru.expensetracker.domain.ai.AiError
import com.suguru.expensetracker.domain.ai.AiResult
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

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
    ): AiResult<String> {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isBlank()) {
            return AiResult.Failure(AiError.MissingApiKey)
        }

        val jsonPayload = try {
            requestAdapter.toJson(requestBody)
        } catch (_: Exception) {
            return AiResult.Failure(AiError.ExecutionError("Could not prepare the Gemini request."))
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
        val request = Request.Builder()
            .url(url)
            .addHeader("x-goog-api-key", trimmedKey)
            .addHeader("Content-Type", "application/json; charset=utf-8")
            .post(jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    val result = when (e) {
                        is SocketTimeoutException -> AiResult.Failure(AiError.RequestTimeout)
                        else -> AiResult.Failure(AiError.NetworkError("Network request failed."))
                    }
                    if (continuation.isActive) continuation.resume(result)
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = try {
                        response.use { parseResponse(it) }
                    } catch (e: IOException) {
                        if (e is SocketTimeoutException) {
                            AiResult.Failure(AiError.RequestTimeout)
                        } else {
                            AiResult.Failure(AiError.NetworkError("Network request failed."))
                        }
                    } catch (_: Exception) {
                        AiResult.Failure(AiError.ExecutionError("Gemini request failed."))
                    }
                    if (continuation.isActive) continuation.resume(result)
                }
            })
        }
    }

    private fun parseResponse(response: Response): AiResult<String> {
        val responseBody = response.body?.string() ?: ""
        return when (response.code) {
            200 -> {
                val parsed = try {
                    responseAdapter.fromJson(responseBody)
                } catch (_: Exception) {
                    null
                }
                val text = parsed?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (text != null) AiResult.Success(text)
                else AiResult.Failure(AiError.MalformedResponse("Invalid or empty Gemini response"))
            }
            400 -> {
                if (responseBody.contains("API_KEY_INVALID", ignoreCase = true) ||
                    responseBody.contains("key not valid", ignoreCase = true)) {
                    AiResult.Failure(AiError.InvalidApiKey)
                } else {
                    AiResult.Failure(AiError.ServerError(400, "Bad request to Gemini API"))
                }
            }
            401 -> AiResult.Failure(AiError.InvalidApiKey)
            403 -> {
                if (responseBody.contains("API_KEY_INVALID", ignoreCase = true) ||
                    responseBody.contains("API key not valid", ignoreCase = true) ||
                    responseBody.contains("invalid API key", ignoreCase = true)) {
                    AiResult.Failure(AiError.InvalidApiKey)
                } else {
                    AiResult.Failure(AiError.ServerError(403, "Gemini rejected the request for this API configuration"))
                }
            }
            429 -> AiResult.Failure(AiError.RateLimitExceeded(response.header("Retry-After")?.toLongOrNull()))
            in 500..599 -> AiResult.Failure(AiError.ServerError(response.code, "Gemini service temporarily unavailable"))
            else -> AiResult.Failure(AiError.ServerError(response.code, "HTTP ${response.code} error received"))
        }
    }
}
