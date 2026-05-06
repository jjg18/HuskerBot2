package org.j3y.HuskerBot2.service

import tools.jackson.databind.JsonNode
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.Cacheable
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder

@Service
class CfbBettingLinesService(
    @Value("\${cfbd.base-url}") private val baseUrl: String,
    @Value("\${cfbd.user-agent}") private val userAgent: String,
    @Value("\${cfbd.api-key}") private val apiKey: String
) {
    private val log = LoggerFactory.getLogger(CfbBettingLinesService::class.java)
    private val restTemplate = RestTemplate()

    /**
     * Retrieve betting lines from CollegeFootballData.com for a specific team/week/year.
     * Only parameters accepted are year, week, and team per requirement.
     *
     * Docs: https://api.collegefootballdata.com/api/docs/?url=/api-docs.json#/betting/getLines
     */
    @Cacheable("cfb-lines", unless = "#result == null")
    fun getLines(year: Int, week: Int, team: String): JsonNode? {
        return try {
            log.info("Fetching CFB betting lines for team=$team, year=$year, week=$week")

            val headers = HttpHeaders()
            headers.set("User-Agent", userAgent)
            headers.set("Authorization", "Bearer $apiKey")
            val entity = HttpEntity<String>(headers)

            val uri = UriComponentsBuilder
                .fromUriString("$baseUrl/lines")
                .queryParam("year", year)
                .queryParam("seasonType", "regular")
                .queryParam("week", week)
                .queryParam("team", team)
                .build(false)
                .toUri()

            log.debug("Making request to: $uri")

            val response = restTemplate.exchange(
                uri,
                HttpMethod.GET,
                entity,
                JsonNode::class.java
            )

            val body = response.body
            if (body != null && body.isArray && body.size() > 0) {
                body
            } else {
                log.warn("No betting lines found for team=$team, year=$year, week=$week")
                null
            }
        } catch (e: Exception) {
            log.error("Error fetching CFB betting lines for team=$team, year=$year, week=$week", e)
            null
        }
    }
}
