package org.j3y.HuskerBot2.service

import org.j3y.HuskerBot2.model.UrbanDefinition
import org.j3y.HuskerBot2.model.UrbanDictionaryResponse
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Service
class UrbanDictionaryService {
    
    private val logger = LoggerFactory.getLogger(UrbanDictionaryService::class.java)
    private val restTemplate = RestTemplate()
    
    companion object {
        private const val URBAN_API_BASE_URL = "https://unofficialurbandictionaryapi.com/api"
        private const val MAX_DEFINITION_LENGTH = 1024
        private const val MAX_EXAMPLE_LENGTH = 512
    }
    
    @Cacheable("urban-definitions", unless = "#result == null")
    fun searchDefinitions(term: String): List<UrbanDefinition>? {
        return try {
            val encodedTerm = URLEncoder.encode(term, StandardCharsets.UTF_8)
            val url = "$URBAN_API_BASE_URL/search?term=$encodedTerm"
            
            logger.info("Searching Urban Dictionary for term: $term")
            
            val response = restTemplate.getForObject(url, UrbanDictionaryResponse::class.java)
            
            response?.list?.map { definition ->
                definition.copy(
                    definition = cleanUrbanText(truncateText(definition.definition, MAX_DEFINITION_LENGTH)),
                    example = cleanUrbanText(truncateText(definition.example, MAX_EXAMPLE_LENGTH))
                )
            }?.sortedByDescending { it.thumbsUp }
            
        } catch (e: Exception) {
            logger.error("Error fetching Urban Dictionary definitions for term: $term", e)
            null
        }
    }
    
    private fun cleanUrbanText(text: String): String {
        return text
            .replace("[", "**")
            .replace("]", "**")
            .replace("\\r\\n", "\n")
            .replace("\\n", "\n")
            .trim()
    }
    
    private fun truncateText(text: String, maxLength: Int): String {
        return if (text.length <= maxLength) {
            text
        } else {
            "${text.substring(0, maxLength - 3)}..."
        }
    }
}