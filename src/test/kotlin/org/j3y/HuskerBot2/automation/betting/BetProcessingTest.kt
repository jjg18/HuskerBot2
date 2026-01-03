package org.j3y.HuskerBot2.automation.betting

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import net.dv8tion.jda.api.JDA
import org.j3y.HuskerBot2.model.BetEntity
import org.j3y.HuskerBot2.model.ScheduleEntity
import org.j3y.HuskerBot2.repository.BetRepo
import org.j3y.HuskerBot2.repository.ScheduleRepo
import org.j3y.HuskerBot2.service.CfbBettingLinesService
import org.j3y.HuskerBot2.util.SeasonResolver
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import java.time.Instant
import java.time.LocalDate

class BetProcessingTest {

    private lateinit var betProcessing: BetProcessing
    private lateinit var cfbBettingLinesService: CfbBettingLinesService
    private lateinit var betRepo: BetRepo
    private lateinit var scheduleRepo: ScheduleRepo
    private lateinit var jda: JDA

    private val mapper = ObjectMapper()

    private fun setPrivateField(target: Any, fieldName: String, value: Any?) {
        val field = target.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(target, value)
    }

    @BeforeEach
    fun setup() {
        betProcessing = BetProcessing()
        cfbBettingLinesService = Mockito.mock(CfbBettingLinesService::class.java)
        betRepo = Mockito.mock(BetRepo::class.java)
        scheduleRepo = Mockito.mock(ScheduleRepo::class.java)
        jda = Mockito.mock(JDA::class.java)

        // Inject mocks (use reflection for private fields)
        setPrivateField(betProcessing, "cfbBettingLinesService", cfbBettingLinesService)
        setPrivateField(betProcessing, "betRepo", betRepo)
        setPrivateField(betProcessing, "scheduleRepo", scheduleRepo)
        // jda property is not private in BetProcessing; set directly
        betProcessing.jda = jda
    }

    @Test
    fun `returns when no schedule for week`() {
        val season = LocalDate.now().year
        Mockito.`when`(scheduleRepo.findBySeasonAndWeek(season, 3)).thenReturn(null)

        // Should not throw
        betProcessing.processBets(3)

        // No saves or external calls
        Mockito.verify(scheduleRepo, Mockito.never()).save(Mockito.any(ScheduleEntity::class.java))
        Mockito.verify(betRepo, Mockito.never()).findBySeasonAndWeek(anyInt(), anyInt())
        Mockito.verify(cfbBettingLinesService, Mockito.never()).getLines(anyInt(), anyInt(), anyString())
    }

    @Test
    fun `returns when schedule already completed`() {
        val season = LocalDate.now().year
        val entity = ScheduleEntity(
            id = 1,
            opponent = "Iowa",
            season = season,
            week = 5,
            dateTime = Instant.now(),
            completed = true
        )
        Mockito.`when`(scheduleRepo.findBySeasonAndWeek(season, 5)).thenReturn(entity)

        betProcessing.processBets(5)

        Mockito.verify(scheduleRepo, Mockito.never()).save(Mockito.any(ScheduleEntity::class.java))
        Mockito.verify(betRepo, Mockito.never()).findBySeasonAndWeek(anyInt(), anyInt())
        Mockito.verify(cfbBettingLinesService, Mockito.never()).getLines(anyInt(), anyInt(), anyString())
    }

    @Test
    fun `returns when betting lines service returns null`() {
        val season = LocalDate.now().year
        val entity = ScheduleEntity(
            id = 2,
            opponent = "Iowa",
            season = season,
            week = 6,
            dateTime = Instant.now(),
            completed = false
        )
        Mockito.`when`(scheduleRepo.findBySeasonAndWeek(season, 6)).thenReturn(entity)
        Mockito.`when`(cfbBettingLinesService.getLines(season, 6, "nebraska")).thenReturn(null)

        betProcessing.processBets(6)

        Mockito.verify(scheduleRepo, Mockito.never()).save(Mockito.any(ScheduleEntity::class.java))
        Mockito.verify(betRepo, Mockito.never()).findBySeasonAndWeek(anyInt(), anyInt())
    }

    @Test
    fun `processes bets when Nebraska is home`() {
        val season = SeasonResolver.currentCfbSeason()
        val week = 7
        val entity = ScheduleEntity(
            id = 3,
            opponent = "Iowa",
            season = season,
            week = week,
            dateTime = Instant.now(),
            completed = false
        )
        Mockito.`when`(scheduleRepo.findBySeasonAndWeek(season, week)).thenReturn(entity)

        val json = """
            [
              {
                "homeTeam": "Nebraska",
                "homeScore": 28,
                "awayScore": 20,
                "lines": [ { "overUnder": 45.0, "spread": -3.0, "formattedSpread": "NEB -3" } ]
              }
            ]
        """.trimIndent()
        val node: JsonNode = mapper.readTree(json)
        Mockito.`when`(cfbBettingLinesService.getLines(season, week, "nebraska")).thenReturn(node)

        val bets = listOf(
            BetEntity(userId = 1, season = season, week = week, userTag = "u1", winner = "Nebraska", predictPoints = "Over", predictSpread = "Nebraska"),
            BetEntity(userId = 2, season = season, week = week, userTag = "u2", winner = "Opponent", predictPoints = "Under", predictSpread = "Opponent"),
            BetEntity(userId = 3, season = season, week = week, userTag = "u3", winner = "Nebraska", predictPoints = "Under", predictSpread = "Opponent")
        )
        Mockito.`when`(betRepo.findBySeasonAndWeek(season, week)).thenReturn(bets)

        betProcessing.processBets(week)

        // Verify schedule saved with expected values
        val captor = ArgumentCaptor.forClass(ScheduleEntity::class.java)
        Mockito.verify(scheduleRepo).save(captor.capture())
        val saved = captor.value
        assertEquals(28, saved.huskersScore)
        assertEquals(20, saved.opponentScore)
        assertEquals(true, saved.didNebraskaBeatSpread)
        assertEquals("NEB -3", saved.spread)
        assertEquals(45.0, saved.overUnder)
        assertEquals(true, saved.completed)

        // Winner Nebraska, beat spread Nebraska, total 48>45 => Over
        assertEquals(true, bets[0].correctWinner)
        assertEquals(true, bets[0].correctSpread)
        assertEquals(true, bets[0].correctPoints)

        assertEquals(false, bets[1].correctWinner)
        assertEquals(false, bets[1].correctSpread)
        assertEquals(false, bets[1].correctPoints)

        assertEquals(true, bets[2].correctWinner)
        assertEquals(false, bets[2].correctSpread)
        assertEquals(false, bets[2].correctPoints)
    }

    @Test
    fun `processes bets when Nebraska is away`() {
        val season = SeasonResolver.currentCfbSeason()
        val week = 8
        val entity = ScheduleEntity(
            id = 4,
            opponent = "Iowa",
            season = season,
            week = week,
            dateTime = Instant.now(),
            completed = false
        )
        Mockito.`when`(scheduleRepo.findBySeasonAndWeek(season, week)).thenReturn(entity)

        // Home is Iowa, Nebraska away wins 21-17. Spread favors home by 3.
        val json = """
            [
              {
                "homeTeam": "Iowa",
                "homeScore": 17,
                "awayScore": 21,
                "lines": [ { "overUnder": 41.5, "spread": -3.0, "formattedSpread": "IOWA -3" } ]
              }
            ]
        """.trimIndent()
        val node: JsonNode = mapper.readTree(json)
        Mockito.`when`(cfbBettingLinesService.getLines(season, week, "nebraska")).thenReturn(node)

        val bets = listOf(
            BetEntity(userId = 1, season = season, week = week, userTag = "u1", winner = "Nebraska", predictPoints = "Over", predictSpread = "Nebraska"),
            BetEntity(userId = 2, season = season, week = week, userTag = "u2", winner = "Opponent", predictPoints = "Under", predictSpread = "Opponent"),
            BetEntity(userId = 3, season = season, week = week, userTag = "u3", winner = "Nebraska", predictPoints = "Under", predictSpread = "Opponent")
        )
        Mockito.`when`(betRepo.findBySeasonAndWeek(season, week)).thenReturn(bets)

        betProcessing.processBets(week)

        val captor = ArgumentCaptor.forClass(ScheduleEntity::class.java)
        Mockito.verify(scheduleRepo).save(captor.capture())
        val saved = captor.value
        assertEquals(21, saved.huskersScore)
        assertEquals(17, saved.opponentScore)
        // homeBeatSpread = (17 + -3) > 21 == false; away -> didNebraskaBeatSpread = !homeBeatSpread = true
        assertEquals(true, saved.didNebraskaBeatSpread)
        assertEquals("IOWA -3", saved.spread)
        assertEquals(41.5, saved.overUnder)
        assertEquals(true, saved.completed)

        // Winner Nebraska, beat spread Nebraska, total 38 < 41.5 => Under
        assertEquals(true, bets[0].correctWinner)
        assertEquals(true, bets[0].correctSpread)
        assertEquals(false, bets[0].correctPoints)

        assertEquals(false, bets[1].correctWinner)
        assertEquals(false, bets[1].correctSpread)
        assertEquals(true, bets[1].correctPoints)

        assertEquals(true, bets[2].correctWinner)
        assertEquals(false, bets[2].correctSpread)
        assertEquals(true, bets[2].correctPoints)
    }
}
