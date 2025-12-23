package org.j3y.HuskerBot2.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Recover
import org.springframework.retry.annotation.Retryable
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder

@Service
open class GoogleGeminiService(
    @Value("\${gemini.base-url:https://generativelanguage.googleapis.com}") private val baseUrl: String,
    @Value("\${gemini.model:models/gemini-2.5-flash-lite}") private val model: String,
    @Value("\${gemini.api-key:}") private val apiKey: String,
    @Value("\${gemini.image-model:models/gemini-2.5-flash-image}") private val imageModel: String = "models/gemini-2.5-flash-image",
) {
    private val log = LoggerFactory.getLogger(GoogleGeminiService::class.java)
    private val client = RestTemplate()
    private val mapper = ObjectMapper()

    @Retryable(include = [HttpStatusCodeException::class], maxAttempts = 5, backoff = Backoff(delay = 5000))
    open fun generateText(prompt: String): String {
        if (apiKey.isBlank()) {
            return "Gemini is not configured. Please set gemini.api-key in application.yml or environment."
        }
        val uri = UriComponentsBuilder
            .fromUriString(baseUrl)
            .path("/v1beta/")
            .path(model)
            .path(":generateContent")
            .queryParam("key", apiKey)
            .build(true)
            .toUri()

        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON

        val body = mapOf(
            "contents" to listOf(
                mapOf(
                    "parts" to listOf(
                        mapOf("text" to prompt)
                    )
                )
            )
        )

        val entity = HttpEntity(body, headers)
        var response: JsonNode? = null
        try {
            response = client.exchange(uri, HttpMethod.POST, entity, JsonNode::class.java).body
        } catch (e: HttpStatusCodeException) {
            log.error("Error calling Google Gemini API", e)
            throw e
        }

        val text = response
            ?.get("candidates")?.get(0)
            ?.get("content")?.get("parts")?.get(0)
            ?.get("text")?.asText()

        return text?.takeIf { it.isNotBlank() }
            ?: "No response from Gemini (empty or blocked)."
    }

    sealed class ImageResult {
        data class ImageBytes(val bytes: ByteArray, val mimeType: String = "image/png") : ImageResult()
        data class Error(val message: String) : ImageResult()
    }

    @Retryable(include = [HttpStatusCodeException::class], maxAttempts = 5, backoff = Backoff(delay = 5000))
    open fun generateImage(prompt: String): ImageResult {
        if (apiKey.isBlank()) {
            return ImageResult.Error("Gemini is not configured. Please set gemini.api-key in application.yml or environment.")
        }
        val uri = UriComponentsBuilder
            .fromUriString(baseUrl)
            .path("/v1beta/")
            .path(imageModel)
            .path(":generateContent")
            .queryParam("key", apiKey)
            .build(true)
            .toUri()

        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON

        val body = mapOf(
            "contents" to listOf(
                mapOf(
                    "role" to "user",
                    "parts" to listOf(
                        mapOf("text" to prompt)
                    )
                )
            ),
            "generationConfig" to mapOf(
                "responseModalities" to listOf("IMAGE")
            )
        )

        val entity = HttpEntity(body, headers)
        val response = client.exchange(uri, HttpMethod.POST, entity, JsonNode::class.java).body

        val candidate = response?.get("candidates")?.get(0)
        val parts = candidate?.get("content")?.get("parts")
        if (parts == null || !parts.isArray || parts.size() == 0) {
            return ImageResult.Error("No image returned by Gemini.")
        }

        // Find the first part that has inline_data (image) or file_data
        val iterator = parts.elements()
        while (iterator.hasNext()) {
            val part = iterator.next()
            val inline = part.get("inlineData")
            if (inline != null) {
                val data = inline.get("data")?.asText()
                val mime = inline.get("mimeType")?.asText() ?: "image/png"
                if (!data.isNullOrBlank()) {
                    val bytes = java.util.Base64.getDecoder().decode(data)
                    return ImageResult.ImageBytes(bytes, mime)
                }
            }
            val fileData = part.get("file_data")
            if (fileData != null) {
                // If API returns a URI to a file, we cannot fetch here without external call; return error with hint
                val uriStr = fileData.get("file_uri")?.asText()
                if (!uriStr.isNullOrBlank()) {
                    return ImageResult.Error("Gemini returned file uri; downloading not implemented: ${'$'}uriStr")
                }
            }
        }

        return ImageResult.Error("Gemini response did not include image data.")
    }

    /**
     * Update/transform an input image using a guidance prompt via Gemini Image model.
     */
    @Retryable(include = [HttpStatusCodeException::class], maxAttempts = 5, backoff = Backoff(delay = 5000))
    open fun updateImageWithPrompt(imageBytes: ByteArray, mimeType: String?, prompt: String): ImageResult {
        if (apiKey.isBlank()) {
            return ImageResult.Error("Gemini is not configured. Please set gemini.api-key in application.yml or environment.")
        }

        val uri = UriComponentsBuilder
            .fromUriString(baseUrl)
            .path("/v1beta/")
            .path(imageModel)
            .path(":generateContent")
            .queryParam("key", apiKey)
            .build(true)
            .toUri()

        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON

        val base64 = java.util.Base64.getEncoder().encodeToString(imageBytes)
        val mime = mimeType?.ifBlank { null } ?: "image/png"

        val body = mapOf(
            "contents" to listOf(
                mapOf(
                    "role" to "user",
                    "parts" to listOf(
                        mapOf(
                            "inline_data" to mapOf(
                                "mime_type" to mime,
                                "data" to base64
                            )
                        ),
                        mapOf("text" to prompt)
                    )
                )
            ),
            "generationConfig" to mapOf(
                "responseModalities" to listOf("IMAGE")
            )
        )

        val entity = HttpEntity(body, headers)
        val response = client.exchange(uri, HttpMethod.POST, entity, JsonNode::class.java).body

        val candidate = response?.get("candidates")?.get(0)
        val parts = candidate?.get("content")?.get("parts")
        if (parts == null || !parts.isArray || parts.size() == 0) {
            return ImageResult.Error("No image returned by Gemini.")
        }

        val iterator = parts.elements()
        while (iterator.hasNext()) {
            val part = iterator.next()
            val inline = part.get("inlineData")
            if (inline != null) {
                val data = inline.get("data")?.asText()
                val outMime = inline.get("mimeType")?.asText() ?: "image/png"
                if (!data.isNullOrBlank()) {
                    val bytes = java.util.Base64.getDecoder().decode(data)
                    return ImageResult.ImageBytes(bytes, outMime)
                }
            }
            val fileData = part.get("file_data")
            if (fileData != null) {
                val uriStr = fileData.get("file_uri")?.asText()
                if (!uriStr.isNullOrBlank()) {
                    return ImageResult.Error("Gemini returned file uri; downloading not implemented: ${'$'}uriStr")
                }
            }
        }

        return ImageResult.Error("Gemini response did not include image data.")
    }

    @Recover
    fun recoverGenerateText(ex: HttpStatusCodeException, prompt: String): String {
        log.error("Error calling Google Gemini API after retries", ex)
        val response = mapper.readTree(ex.responseBodyAsString).path("error").path("message").asText()

        return "Error calling Gemini: ${response.ifEmpty { ex.message }}"
    }

    @Recover
    fun recoverGenerateImage(ex: HttpStatusCodeException, prompt: String): ImageResult {
        log.error("Error calling Google Gemini Image API after retries", ex)
        val response = mapper.readTree(ex.responseBodyAsString).path("error").path("message").asText()

        return ImageResult.Error("Error calling Gemini: ${response.ifEmpty { ex.message }}")
    }

    @Recover
    fun recoverUpdateImage(
        ex: HttpStatusCodeException,
        imageBytes: ByteArray,
        mimeType: String?,
        prompt: String
    ): ImageResult {
        log.error("Error calling Google Gemini Image Update API after retries", ex)
        val response = mapper.readTree(ex.responseBodyAsString).path("error").path("message").asText()
        return ImageResult.Error("Error calling Gemini: ${response.ifEmpty { ex.message }}")
    }
}
