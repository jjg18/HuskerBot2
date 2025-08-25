package org.j3y.HuskerBot2.service

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.*
import org.springframework.test.web.client.response.MockRestResponseCreators.*
import org.springframework.web.client.RestTemplate

class CfbMatchupServiceTest {

    private lateinit var service: CfbMatchupService
    private lateinit var server: MockRestServiceServer
    private lateinit var restTemplate: RestTemplate

    private val baseUrl = "https://cfbd.example.test"
    private val userAgent = "HB2-Test-UA"
    private val apiKey = "test-api-key"

    @BeforeEach
    fun setUp() {
        service = CfbMatchupService(baseUrl, userAgent, apiKey)
        // Reflect the private RestTemplate inside the service so we can bind the mock server
        val field = CfbMatchupService::class.java.getDeclaredField("restTemplate")
        field.isAccessible = true
        restTemplate = field.get(service) as RestTemplate
        server = MockRestServiceServer.bindTo(restTemplate).build()
    }

    @AfterEach
    fun tearDown() {
        server.verify()
    }

    @Test
    fun `getTeamMatchup parses full successful response with games`() {
        val team1 = "Nebraska"
        val team2 = "Iowa"
        val json = """
            {
              "team1": "$team1",
              "team1Wins": 30,
              "team2": "$team2",
              "team2Wins": 20,
              "ties": 3,
              "games": [
                {
                  "season": 2023,
                  "week": 13,
                  "seasonType": "regular",
                  "date": "2023-11-24",
                  "neutralSite": false,
                  "venue": "Memorial Stadium",
                  "homeTeam": "Nebraska",
                  "homeScore": 14,
                  "awayTeam": "Iowa",
                  "awayScore": 17,
                  "winner": "Iowa"
                },
                {
                  "season": 2022,
                  "week": 13,
                  "seasonType": "regular",
                  "date": "2022-11-25",
                  "neutralSite": false,
                  "venue": "Kinnick Stadium",
                  "homeTeam": "Iowa",
                  "homeScore": 21,
                  "awayTeam": "Nebraska",
                  "awayScore": 24,
                  "winner": "Nebraska"
                }
              ]
            }
        """.trimIndent()

        val expectedUri = "$baseUrl/teams/matchup?team1=$team1&team2=$team2"

        server.expect(ExpectedCount.once(), requestTo(expectedUri))
            .andExpect(method(org.springframework.http.HttpMethod.GET))
            .andExpect(header("User-Agent", userAgent))
            .andExpect(header("Authorization", "Bearer $apiKey"))
            .andRespond(withSuccess(json, MediaType.APPLICATION_JSON))

        val result = service.getTeamMatchup(team1, team2)
        assertNotNull(result)
        result!!
        assertEquals(team1, result.team1)
        assertEquals(30, result.team1Wins)
        assertEquals(team2, result.team2)
        assertEquals(20, result.team2Wins)
        assertEquals(3, result.ties)
        assertEquals(2, result.games.size)

        val g1 = result.games[0]
        assertEquals(2023, g1.season)
        assertEquals(13, g1.week)
        assertEquals("regular", g1.seasonType)
        assertEquals("2023-11-24", g1.date)
        assertFalse(g1.neutralSite)
        assertEquals("Memorial Stadium", g1.venue)
        assertEquals("Nebraska", g1.homeTeam)
        assertEquals(14, g1.homeScore)
        assertEquals("Iowa", g1.awayTeam)
        assertEquals(17, g1.awayScore)
        assertEquals("Iowa", g1.winner)

        val g2 = result.games[1]
        assertEquals(2022, g2.season)
        assertEquals(13, g2.week)
        assertEquals("regular", g2.seasonType)
        assertEquals("2022-11-25", g2.date)
        assertFalse(g2.neutralSite)
        assertEquals("Kinnick Stadium", g2.venue)
        assertEquals("Iowa", g2.homeTeam)
        assertEquals(21, g2.homeScore)
        assertEquals("Nebraska", g2.awayTeam)
        assertEquals(24, g2.awayScore)
        assertEquals("Nebraska", g2.winner)
    }

    @Test
    fun `getTeamMatchup returns defaults when some fields are missing`() {
        val team1 = "TeamA"
        val team2 = "TeamB"
        val json = """
            {
              "team1": "$team1",
              "team2": "$team2",
              "games": [
                {
                  "season": 2020,
                  "homeTeam": "TeamA",
                  "awayTeam": "TeamB"
                }
              ]
            }
        """.trimIndent()

        val expectedUri = "$baseUrl/teams/matchup?team1=$team1&team2=$team2"
        server.expect(requestTo(expectedUri))
            .andExpect(method(org.springframework.http.HttpMethod.GET))
            .andRespond(withSuccess(json, MediaType.APPLICATION_JSON))

        val result = service.getTeamMatchup(team1, team2)
        assertNotNull(result)
        result!!
        assertEquals(team1, result.team1)
        assertEquals(0, result.team1Wins)
        assertEquals(team2, result.team2)
        assertEquals(0, result.team2Wins)
        assertEquals(0, result.ties)
        assertEquals(1, result.games.size)

        val g = result.games[0]
        assertEquals(2020, g.season)
        assertNull(g.week)
        assertEquals("regular", g.seasonType) // default
        assertEquals("", g.date)
        assertFalse(g.neutralSite)
        assertNull(g.venue)
        assertEquals("TeamA", g.homeTeam)
        assertNull(g.homeScore)
        assertEquals("TeamB", g.awayTeam)
        assertNull(g.awayScore)
        assertNull(g.winner)
    }

    @Test
    fun `getTeamMatchup handles missing or non-array games gracefully`() {
        val team1 = "X"
        val team2 = "Y"
        val jsonNoGames = """
            {
              "team1": "$team1",
              "team2": "$team2"
            }
        """.trimIndent()

        val expectedUri = "$baseUrl/teams/matchup?team1=$team1&team2=$team2"
        server.expect(ExpectedCount.once(), requestTo(expectedUri))
            .andRespond(withSuccess(jsonNoGames, MediaType.APPLICATION_JSON))

        val result1 = service.getTeamMatchup(team1, team2)
        assertNotNull(result1)
        assertTrue(result1!!.games.isEmpty())
    }

    @Test
    fun `getTeamMatchup returns null when required fields missing`() {
        val team1 = "A"
        val team2 = "B"
        val json = "{}"
        val expectedUri = "$baseUrl/teams/matchup?team1=$team1&team2=$team2"
        server.expect(requestTo(expectedUri))
            .andRespond(withSuccess(json, MediaType.APPLICATION_JSON))

        val result = service.getTeamMatchup(team1, team2)
        assertNull(result)
    }

    @Test
    fun `getTeamMatchup returns null on server error and does not throw`() {
        val team1 = "A"
        val team2 = "B"
        val expectedUri = "$baseUrl/teams/matchup?team1=$team1&team2=$team2"
        server.expect(requestTo(expectedUri))
            .andRespond(withServerError())

        val result = service.getTeamMatchup(team1, team2)
        assertNull(result)
    }
}
