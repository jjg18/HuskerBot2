package org.j3y.HuskerBot2.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import java.net.URI

class GoogleGeminiServiceTest {

    private val mapper = ObjectMapper()

    private fun buildServiceWithMock(
        apiKey: String = "key-123",
        baseUrl: String = "https://example.com",
        model: String = "models/test-model"
    ): Pair<GoogleGeminiService, RestTemplate> {
        val svc = GoogleGeminiService(baseUrl, model, apiKey)
        val mockClient = Mockito.mock(RestTemplate::class.java)
        val field = GoogleGeminiService::class.java.getDeclaredField("client")
        field.isAccessible = true
        field.set(svc, mockClient)
        return svc to mockClient
    }

    @Test
    fun `generateText returns configuration message when apiKey blank`() {
        val svc = GoogleGeminiService(
            baseUrl = "https://example.com",
            model = "models/test",
            apiKey = ""
        )
        val result = svc.generateText("hello")
        assertEquals(
            "Gemini is not configured. Please set gemini.api-key in application.yml or environment.",
            result
        )
    }

    @Test
    fun `generateText performs POST with correct URL headers and body then returns text`() {
        val (svc, client) = buildServiceWithMock()

        // Prepare JSON response from API
        val json: JsonNode = mapper.readTree(
            """
            {"candidates":[{"content":{"parts":[{"text":"Hello world"}]}}]}
            """.trimIndent()
        )

        // Stubbing exchange
        `when`(
            client.exchange(
                Mockito.any(URI::class.java),
                Mockito.any(HttpMethod::class.java),
                Mockito.any(HttpEntity::class.java),
                Mockito.eq(JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity.ok(json))

        val prompt = "Tell me a joke"
        val result = svc.generateText(prompt)
        assertEquals("Hello world", result)

        // Capture and verify request details
        val uriCaptor = ArgumentCaptor.forClass(URI::class.java)
        val methodCaptor = ArgumentCaptor.forClass(HttpMethod::class.java)
        @Suppress("UNCHECKED_CAST")
        val entityCaptor = ArgumentCaptor.forClass(HttpEntity::class.java) as ArgumentCaptor<HttpEntity<*>>

        Mockito.verify(client).exchange(
            uriCaptor.capture(),
            methodCaptor.capture(),
            entityCaptor.capture(),
            Mockito.eq(JsonNode::class.java)
        )

        val uri = uriCaptor.value.toString()
        assertTrue(uri.startsWith("https://example.com/v1beta/models/test-model:generateContent"))
        assertTrue(uri.contains("?key=key-123"))
        assertEquals(HttpMethod.POST, methodCaptor.value)

        val entity = entityCaptor.value
        val headers: HttpHeaders = entity.headers
        assertEquals(MediaType.APPLICATION_JSON, headers.contentType)

        @Suppress("UNCHECKED_CAST")
        val body = entity.body as Map<*, *>
        val contents = body["contents"] as List<*>?
        assertNotNull(contents)
        val content0 = contents!![0] as Map<*, *>
        val parts = content0["parts"] as List<*>?
        assertNotNull(parts)
        val part0 = parts!![0] as Map<*, *>
        assertEquals(prompt, part0["text"])
    }

    @Test
    fun `generateText returns fallback when response structure missing`() {
        val (svc, client) = buildServiceWithMock()

        val json: JsonNode = mapper.readTree("{}")

        `when`(
            client.exchange(
                Mockito.any(URI::class.java),
                Mockito.any(HttpMethod::class.java),
                Mockito.any(HttpEntity::class.java),
                Mockito.eq(JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity.ok(json))

        val result = svc.generateText("prompt")
        assertEquals("No response from Gemini (empty or blocked).", result)
    }

    @Test
    fun `generateText returns fallback when text is blank`() {
        val (svc, client) = buildServiceWithMock()

        val json: JsonNode = mapper.readTree(
            """
            {"candidates":[{"content":{"parts":[{"text":""}]}}]}
            """.trimIndent()
        )

        `when`(
            client.exchange(
                Mockito.any(URI::class.java),
                Mockito.any(HttpMethod::class.java),
                Mockito.any(HttpEntity::class.java),
                Mockito.eq(JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity.ok(json))

        val result = svc.generateText("prompt")
        assertEquals("No response from Gemini (empty or blocked).", result)
    }

    @Test
    fun `generateText returns error message when exception thrown`() {
        val (svc, client) = buildServiceWithMock()

        `when`(
            client.exchange(
                Mockito.any(URI::class.java),
                Mockito.any(HttpMethod::class.java),
                Mockito.any(HttpEntity::class.java),
                Mockito.eq(JsonNode::class.java)
            )
        ).thenThrow(RuntimeException("boom"))

        val result = svc.generateText("prompt")
        assertEquals("Error calling Gemini: boom", result)
    }
}
