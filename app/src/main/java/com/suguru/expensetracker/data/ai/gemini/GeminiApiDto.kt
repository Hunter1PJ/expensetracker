package com.suguru.expensetracker.data.ai.gemini

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GeminiGenerateContentRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null,
    val generationConfig: GeminiGenerationConfig? = null
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiPart>
)

@JsonClass(generateAdapter = true)
data class GeminiPart(
    val text: String
)

@JsonClass(generateAdapter = true)
data class GeminiGenerationConfig(
    val temperature: Float? = null,
    val topP: Float? = null,
    val maxOutputTokens: Int? = null,
    val responseMimeType: String? = null,
    val responseSchema: Map<String, Any>? = null
)

@JsonClass(generateAdapter = true)
data class GeminiGenerateContentResponse(
    val candidates: List<GeminiCandidateResponse>? = null,
    val error: GeminiErrorResponse? = null
)

@JsonClass(generateAdapter = true)
data class GeminiCandidateResponse(
    val content: GeminiContent? = null,
    val finishReason: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiErrorResponse(
    val code: Int? = null,
    val message: String? = null,
    val status: String? = null
)
