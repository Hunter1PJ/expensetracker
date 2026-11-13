package com.suguru.expensetracker.data.ai.gemini

import com.suguru.expensetracker.domain.ai.AiConfiguration
import com.suguru.expensetracker.domain.ai.AiModelDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiRequestFactoryTest {

    @Test
    fun createExtractionRequest_buildsStrictSchemaAndSystemInstruction() {
        val config = AiConfiguration(
            modelIdentifier = AiModelDefaults.GEMINI_MODEL_ID,
            temperature = 0.1f,
            defaultCurrency = "UZS"
        )
        val prompt = "Bugun tushlikka 45000 som toladim"
        val request = GeminiRequestFactory.createExtractionRequest(prompt, config)

        assertNotNull(request.systemInstruction)
        assertTrue(request.systemInstruction!!.parts.first().text.contains("financial safety", ignoreCase = true))

        val userPart = request.contents.first().parts.first().text
        assertTrue(userPart.contains("Default Currency: UZS"))
        assertTrue(userPart.contains(prompt))

        val genConfig = request.generationConfig
        assertNotNull(genConfig)
        assertEquals("application/json", genConfig!!.responseMimeType)
        assertNotNull(genConfig.responseSchema)
    }

    @Test
    fun createValidationRequest_createsMinimalProbePayload() {
        val request = GeminiRequestFactory.createValidationRequest()
        assertNotNull(request.contents)
        assertEquals(1, request.contents.size)
        assertEquals("application/json", request.generationConfig?.responseMimeType)
    }
}
