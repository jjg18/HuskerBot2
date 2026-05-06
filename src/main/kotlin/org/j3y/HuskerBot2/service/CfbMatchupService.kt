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
class CfbMatchupService(
    @Value("\${cfbd.base-url}") private val baseUrl: String,
    @Value("\${cfbd.user-agent}") private val userAgent: String,
    @Value("\${cfbd.api-key}") private val apiKey: String
) {
    
    private val log = LoggerFactory.getLogger(CfbMatchupService::class.java)
    private val restTemplate = RestTemplate()
    
    data class TeamMatchupData(
        val team1: String,
        val team1Wins: Int,
        val team2: String,
        val team2Wins: Int,
        val ties: Int,
        val games: List<GameResult>
    )
    
    data class GameResult(
        val season: Int,
        val week: Int?,
        val seasonType: String,
        val date: String,
        val neutralSite: Boolean,
        val venue: String?,
        val homeTeam: String,
        val homeScore: Int?,
        val awayTeam: String,
        val awayScore: Int?,
        val winner: String?
    )
    
    @Cacheable("cfb-matchup", unless = "#result == null")
    fun getTeamMatchup(team1: String, team2: String): TeamMatchupData? {
        return try {
            log.info("Fetching CFB matchup data for $team1 vs $team2")
            
            val headers = HttpHeaders()
            headers.set("User-Agent", userAgent)
            headers.set("Authorization", "Bearer $apiKey")
            val entity = HttpEntity<String>(headers)
            
            val uri = UriComponentsBuilder
                .fromUriString("$baseUrl/teams/matchup")
                .queryParam("team1", team1)
                .queryParam("team2", team2)
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
            if (body != null && body.has("team1") && body.has("team2")) {
                parseMatchupData(body)
            } else {
                log.warn("No matchup data found for $team1 vs $team2")
                null
            }
        } catch (e: Exception) {
            log.error("Error fetching CFB matchup data for $team1 vs $team2", e)
            null
        }
    }

    @Cacheable("cfb-team-season", unless = "#result == null || #result.isEmpty()")
    fun getTeamSeasonGames(team: String, year: Int): JsonNode? {
        return try {
            log.info("Fetching CFB season games for team=$team, year=$year")

            val headers = HttpHeaders()
            headers.set("User-Agent", userAgent)
            headers.set("Authorization", "Bearer $apiKey")
            val entity = HttpEntity<String>(headers)

            val uri = UriComponentsBuilder
                .fromUriString("$baseUrl/games/teams")
                .queryParam("year", year)
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
            if (body != null && body.isArray) {
                // Remove opponents from data
                //body.forEach { game ->
                //    val teams = game.path("teams") as ArrayNode
                //    teams.removeIf { !team.equals(it.path("team").textValue(), true) }
                //}

                body
            } else {
                log.warn("No season games found for team=$team, year=$year")
                null
            }
        } catch (e: Exception) {
            log.error("Error fetching CFB season games for team=$team, year=$year", e)
            null
        }
    }
    
    private fun parseMatchupData(jsonNode: JsonNode): TeamMatchupData {
        val team1 = jsonNode.get("team1")?.asText() ?: "Unknown"
        val team1Wins = jsonNode.get("team1Wins")?.asInt() ?: 0
        val team2 = jsonNode.get("team2")?.asText() ?: "Unknown"
        val team2Wins = jsonNode.get("team2Wins")?.asInt() ?: 0
        val ties = jsonNode.get("ties")?.asInt() ?: 0
        
        val games = mutableListOf<GameResult>()
        val gamesArray = jsonNode.get("games")
        
        if (gamesArray != null && gamesArray.isArray) {
            gamesArray.forEach { gameNode ->
                val game = GameResult(
                    season = gameNode.get("season")?.asInt() ?: 0,
                    week = gameNode.get("week")?.asInt(),
                    seasonType = gameNode.get("seasonType")?.asText() ?: "regular",
                    date = gameNode.get("date")?.asText() ?: "",
                    neutralSite = gameNode.get("neutralSite")?.asBoolean() ?: false,
                    venue = gameNode.get("venue")?.asText(),
                    homeTeam = gameNode.get("homeTeam")?.asText() ?: "",
                    homeScore = gameNode.get("homeScore")?.asInt(),
                    awayTeam = gameNode.get("awayTeam")?.asText() ?: "",
                    awayScore = gameNode.get("awayScore")?.asInt(),
                    winner = gameNode.get("winner")?.asText()
                )
                games.add(game)
            }
        }
        
        return TeamMatchupData(team1, team1Wins, team2, team2Wins, ties, games)
    }
}