package org.j3y.HuskerBot2.service

import tools.jackson.databind.JsonNode
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder

@Service
class UrbanDictionaryService(
    @Value("\${urban.base-url}") private val baseUrl: String,
    @Value("\${urban.user-agent}") private val userAgent: String,
) {
    private val log = LoggerFactory.getLogger(UrbanDictionaryService::class.java)
    private val client = RestTemplate()

    data class UrbanDefinition(
        val word: String,
        val definition: String,
        val example: String?,
        val author: String?,
        val permalink: String?
    )

    fun defineAll(term: String): List<UrbanDefinition> {
        return try {
            val uri = UriComponentsBuilder
                .fromUriString(baseUrl)
                .path("/v0/define")
                .queryParam("term", term)
                .build(false)
                .toUri()

            val headers = HttpHeaders()
            headers.set("User-Agent", userAgent)
            val entity = HttpEntity<Void>(headers)

            val response = client.exchange(uri, HttpMethod.GET, entity, JsonNode::class.java).body
            val list = response?.get("list")
            if (list != null && list.isArray && list.size() > 0) {
                list.map<JsonNode, UrbanDefinition> { node ->
                    UrbanDefinition(
                        word = node.get("word")?.asText() ?: term,
                        definition = cleanUdText(node.get("definition")?.asText() ?: ""),
                        example = cleanUdText(node.get("example")?.asText() ?: ""),
                        author = node.get("author")?.takeIf { !it.isNull }?.asText(),
                        permalink = node.get("permalink")?.takeIf { !it.isNull }?.asText()
                    )
                }
            } else {
                emptyList<UrbanDefinition>()
            }
        } catch (ex: Exception) {
            log.error("Error fetching Urban Dictionary definitions for term: {}", term, ex)
            emptyList<UrbanDefinition>()
        }
    }

    fun define(term: String): UrbanDefinition? {
        val all = defineAll(term)
        return if (all.isNotEmpty()) all[0] else null
    }

    private fun cleanUdText(text: String): String {
        // Urban Dictionary wraps words in [brackets]; remove them for clarity in embeds
        return text.replace("[", "").replace("]", "")
    }
}
