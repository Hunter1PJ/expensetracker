package com.suguru.expensetracker.data.ai.gemini

import com.suguru.expensetracker.domain.ai.AiError
import com.suguru.expensetracker.domain.ai.AiModelDefaults
import com.suguru.expensetracker.domain.ai.AiResult
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class GeminiApiClientTest {

    @Test
    fun sendsApiKeyOnlyInHttpsHeaderAndParsesResponse() = runTest {
        var capturedRequest: Request? = null
        val client = GeminiApiClient(
            client = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    capturedRequest = chain.request()
                    response(chain.request(), 200, """{"candidates":[{"content":{"parts":[{"text":"{}"}]}}]}""")
                }
                .build()
        )

        val result = client.generateContent(
            apiKey = " fixture-value ",
            model = AiModelDefaults.GEMINI_MODEL_ID,
            requestBody = GeminiRequestFactory.createValidationRequest()
        )

        assertEquals(AiResult.Success("{}"), result)
        assertTrue(capturedRequest!!.url.isHttps)
        assertEquals("fixture-value", capturedRequest!!.header("x-goog-api-key"))
        assertFalse(capturedRequest!!.url.toString().contains("fixture-value"))
    }

    @Test
    fun classifiesHttp400InvalidKeyResponse() = runTest {
        val result = clientWithResponse(400, "API_KEY_INVALID")
            .generateContent("fixture-value", AiModelDefaults.GEMINI_MODEL_ID, validationRequest())

        assertEquals(AiResult.Failure(AiError.InvalidApiKey), result)
    }

    @Test
    fun classifiesOtherHttp400ResponseAsServerError() = runTest {
        val result = clientWithResponse(400, "{\"error\":{\"message\":\"invalid request\"}}")
            .generateContent("fixture-value", AiModelDefaults.GEMINI_MODEL_ID, validationRequest())

        assertEquals(AiResult.Failure(AiError.ServerError(400, "Bad request to Gemini API")), result)
    }

    @Test
    fun classifiesDefinitiveHttp403KeyRejectionAsInvalidKey() = runTest {
        val result = clientWithResponse(403, "{\"error\":{\"status\":\"API_KEY_INVALID\"}}")
            .generateContent("fixture-value", AiModelDefaults.GEMINI_MODEL_ID, validationRequest())

        assertEquals(AiResult.Failure(AiError.InvalidApiKey), result)
    }

    @Test
    fun classifiesOtherHttp403AsTemporaryConfigurationError() = runTest {
        val result = clientWithResponse(403, "{}")
            .generateContent("fixture-value", AiModelDefaults.GEMINI_MODEL_ID, validationRequest())

        assertEquals(
            AiResult.Failure(AiError.ServerError(403, "Gemini rejected the request for this API configuration")),
            result
        )
    }

    @Test
    fun preservesHttp404500And503ServerErrorStatusCodes() = runTest {
        listOf(
            404 to "HTTP 404 error received",
            500 to "Gemini service temporarily unavailable",
            503 to "Gemini service temporarily unavailable"
        ).forEach { (statusCode, message) ->
            val result = clientWithResponse(statusCode, "{}")
                .generateContent("fixture-value", AiModelDefaults.GEMINI_MODEL_ID, validationRequest())

            assertEquals(AiResult.Failure(AiError.ServerError(statusCode, message)), result)
        }
    }

    @Test
    fun classifiesNetworkIOExceptionWithoutExposingExceptionMessage() = runTest {
        val client = GeminiApiClient(
            client = OkHttpClient.Builder()
                .addInterceptor(Interceptor { throw IOException("network unavailable") })
                .build()
        )

        val result = client.generateContent("fixture-value", AiModelDefaults.GEMINI_MODEL_ID, validationRequest())

        assertEquals(AiResult.Failure(AiError.NetworkError("Network request failed.")), result)
    }

    @Test
    fun classifiesTimeout() = runTest {
        val client = GeminiApiClient(
            client = OkHttpClient.Builder()
                .addInterceptor(Interceptor { throw SocketTimeoutException("request timed out") })
                .build()
        )

        val result = client.generateContent("fixture-value", AiModelDefaults.GEMINI_MODEL_ID, validationRequest())

        assertEquals(AiResult.Failure(AiError.RequestTimeout), result)
    }

    @Test
    fun cancellingRequestCancelsUnderlyingOkHttpCall() = runTest {
        val requestStarted = CountDownLatch(1)
        val requestCancelled = CountDownLatch(1)
        val client = GeminiApiClient(
            client = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    requestStarted.countDown()
                    while (!chain.call().isCanceled()) {
                        Thread.sleep(5)
                    }
                    requestCancelled.countDown()
                    throw IOException("Canceled request")
                }
                .build()
        )

        val requestJob = launch {
            client.generateContent("fixture-value", AiModelDefaults.GEMINI_MODEL_ID, validationRequest())
        }
        testScheduler.runCurrent()
        assertTrue(requestStarted.await(2, TimeUnit.SECONDS))
        requestJob.cancelAndJoin()

        assertTrue(requestCancelled.await(2, TimeUnit.SECONDS))
    }

    @Test
    fun classifiesMalformedJsonWithoutExposingParserMessage() = runTest {
        val result = clientWithResponse(200, "{")
            .generateContent("fixture-value", AiModelDefaults.GEMINI_MODEL_ID, validationRequest())

        assertEquals(
            AiResult.Failure(AiError.MalformedResponse("Invalid or empty Gemini response")),
            result
        )
    }

    private fun validationRequest() = GeminiRequestFactory.createValidationRequest()

    private fun clientWithResponse(statusCode: Int, body: String) = GeminiApiClient(
        client = OkHttpClient.Builder()
            .addInterceptor { chain -> response(chain.request(), statusCode, body) }
            .build()
    )

    private fun response(request: Request, status: Int, body: String): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(status)
            .message("test response")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
}