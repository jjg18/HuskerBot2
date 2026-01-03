package org.j3y.HuskerBot2.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.j3y.HuskerBot2.util.SeasonResolver
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.springframework.web.client.RestTemplate
import java.net.URI
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class DefaultEspnServiceHttpTest {

    private fun dummyJson(): JsonNode = ObjectMapper().readTree("{}")

    @Test
    fun `getCfbScoreboard builds url with week and groups when league provided`() {
        val client = Mockito.mock(RestTemplate::class.java)
        val svc = DefaultEspnService(client)
        val captor = ArgumentCaptor.forClass(URI::class.java)
        `when`(client.getForObject(captor.capture(), Mockito.eq(JsonNode::class.java))).thenReturn(dummyJson())

        val league = 5
        val week = 3
        svc.getCfbScoreboard(league, week)

        val uri = captor.value.toString()
        assertTrue(uri.contains("college-football/scoreboard"))
        assertTrue(uri.contains("week=$week"))
        assertTrue(uri.contains("groups=$league"))
        assertTrue(uri.contains("limit=300"))
        assertTrue(uri.contains("seasontype=2"))
        assertTrue(uri.contains("dates=${SeasonResolver.currentCfbSeason()}"))
    }

    @Test
    fun `getCfbScoreboard omits groups when league is zero`() {
        val client = Mockito.mock(RestTemplate::class.java)
        val svc = DefaultEspnService(client)
        val captor = ArgumentCaptor.forClass(URI::class.java)
        `when`(client.getForObject(captor.capture(), Mockito.eq(JsonNode::class.java))).thenReturn(dummyJson())

        val week = 7
        svc.getCfbScoreboard(0, week)

        val uri = captor.value.toString()
        assertTrue(uri.contains("college-football/scoreboard"))
        assertTrue(uri.contains("week=$week"))
        // groups should not be present
        assertFalse(uri.contains("groups="))
    }

    @Test
    fun `getNflScoreboard builds url with week`() {
        val client = Mockito.mock(RestTemplate::class.java)
        val svc = DefaultEspnService(client)
        val captor = ArgumentCaptor.forClass(URI::class.java)
        `when`(client.getForObject(captor.capture(), Mockito.eq(JsonNode::class.java))).thenReturn(dummyJson())

        val week = 12
        svc.getNflScoreboard(week)

        val uri = captor.value.toString()
        assertTrue(uri.contains("football/nfl/scoreboard"))
        assertTrue(uri.contains("week=$week"))
        assertTrue(uri.contains("limit=100"))
        assertTrue(uri.contains("seasontype=2"))
        assertTrue(uri.contains("dates=${SeasonResolver.currentNflSeason()}"))
    }

    @Test
    fun `getNhlScoreboard builds url with computed date`() {
        val client = Mockito.mock(RestTemplate::class.java)
        val svc = DefaultEspnService(client)
        val captor = ArgumentCaptor.forClass(URI::class.java)
        `when`(client.getForObject(captor.capture(), Mockito.eq(JsonNode::class.java))).thenReturn(dummyJson())

        val days = 2
        val expectedDate = LocalDate.now().plusDays(days.toLong()).format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        svc.getNhlScoreboard(days)

        val uri = captor.value.toString()
        assertTrue(uri.contains("sports/hockey/nhl/scoreboard"))
        assertTrue(uri.contains("dates=$expectedDate"))
        assertTrue(uri.contains("limit=100"))
    }

    @Test
    fun `getTeamData builds url with team path variable`() {
        val client = Mockito.mock(RestTemplate::class.java)
        val svc = DefaultEspnService(client)
        val captor = ArgumentCaptor.forClass(URI::class.java)
        `when`(client.getForObject(captor.capture(), Mockito.eq(JsonNode::class.java))).thenReturn(dummyJson())

        val team = "nebraska"
        svc.getTeamData(team)

        val uri = captor.value.toString()
        assertTrue(uri.contains("college-football/teams/$team"))
    }

    @Test
    fun `evictCaches executes without error`() {
        val svc = DefaultEspnService()
        // just ensure it does not throw
        svc.evictCaches()
    }
}
