package org.j3y.HuskerBot2

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.j3y.HuskerBot2.model.ScheduleEntity
import org.j3y.HuskerBot2.repository.ScheduleRepo
import org.j3y.HuskerBot2.scheduler.SunriseSunsetScheduler
import org.j3y.HuskerBot2.service.HuskersDotComService
import org.j3y.HuskerBot2.util.SeasonResolver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import java.time.Instant
import java.time.LocalDate
import java.util.Optional

class StartupSchedulePopulateTest {

    private lateinit var scheduleRepo: ScheduleRepo
    private lateinit var huskersDotComService: HuskersDotComService
    private lateinit var sunriseSunsetScheduler: SunriseSunsetScheduler
    private lateinit var subject: StartupSchedulePopulate

    private val mapper = ObjectMapper()

    @BeforeEach
    fun setUp() {
        scheduleRepo = Mockito.mock(ScheduleRepo::class.java)
        huskersDotComService = Mockito.mock(HuskersDotComService::class.java)
        sunriseSunsetScheduler = Mockito.mock(SunriseSunsetScheduler::class.java)
        subject = StartupSchedulePopulate()

        // Inject mocks into the autowired fields
        // scheduleRepo is private, so use reflection
        val repoField = StartupSchedulePopulate::class.java.getDeclaredField("scheduleRepo")
        repoField.isAccessible = true
        repoField.set(subject, scheduleRepo)
        // huskersDotComService has default visibility; can set directly via reflection too for consistency
        val huskersField = StartupSchedulePopulate::class.java.getDeclaredField("huskersDotComService")
        huskersField.isAccessible = true
        huskersField.set(subject, huskersDotComService)

        val schedulerField = StartupSchedulePopulate::class.java.getDeclaredField("a")
        schedulerField.isAccessible = true
        schedulerField.set(subject, sunriseSunsetScheduler)
    }

    private fun buildGame(
        id: Long,
        location: String,
        opponent: String,
        logoUrl: String,
        datetime: Instant,
        isConference: Boolean,
        venueType: String,
    ): ObjectNode {
        val game = mapper.createObjectNode()
        game.put("id", id)
        game.put("location", location)
        game.put("opponent_name", opponent)
        val logo = mapper.createObjectNode()
        logo.put("url", logoUrl)
        game.set<ObjectNode>("opponent_logo", logo)
        game.put("datetime", datetime.toString())
        game.put("is_conference", isConference)
        game.put("venue_type", venueType)
        return game
    }

    private fun scheduleResponse(vararg games: ObjectNode): ObjectNode {
        val root = mapper.createObjectNode()
        val arr: ArrayNode = mapper.createArrayNode()
        games.forEach { arr.add(it) }
        root.set<ArrayNode>("data", arr)
        return root
    }

    @Nested
    @DisplayName("init() behavior")
    inner class InitBehavior {
        @Test
        fun `creates and saves new schedule entities for returned games`() {
            val year = SeasonResolver.currentCfbSeason()
            val dt1 = Instant.parse("$year-08-31T18:00:00Z")
            val dt2 = Instant.parse("$year-09-07T18:00:00Z")

            val resp = scheduleResponse(
                buildGame(1, "Lincoln, NE", "Team A", "http://logo/a.png", dt1, true, "home"),
                buildGame(2, "Boulder, CO", "Team B", "http://logo/b.png", dt2, false, "away"),
            )

            Mockito.`when`(huskersDotComService.getSchedule(year)).thenReturn(resp)
            Mockito.`when`(scheduleRepo.findById(1L)).thenReturn(Optional.empty())
            Mockito.`when`(scheduleRepo.findById(2L)).thenReturn(Optional.empty())

            subject.init()

            // Verify request
            Mockito.verify(huskersDotComService, Mockito.times(1)).getSchedule(year)

            // Capture saves
            val captor = ArgumentCaptor.forClass(ScheduleEntity::class.java)
            Mockito.verify(scheduleRepo, Mockito.times(2)).save(captor.capture())
            val saved = captor.allValues

            // Saved IDs should be 1 and 2
            assertEquals(setOf(1L, 2L), saved.map { it.id }.toSet())

            val game1 = saved.first { it.id == 1L }
            assertEquals("Team A", game1.opponent)
            assertEquals("Lincoln, NE", game1.location)
            assertEquals("home", game1.venueType)
            assertEquals(true, game1.isConference)
            assertEquals("http://logo/a.png", game1.opponentLogo)
            assertEquals(dt1, game1.dateTime)
            assertEquals(year, game1.season)
            assertEquals(1, game1.week, "Week should be index+1 (first item = 1)")

            val game2 = saved.first { it.id == 2L }
            assertEquals("Team B", game2.opponent)
            assertEquals("Boulder, CO", game2.location)
            assertEquals("away", game2.venueType)
            assertEquals(false, game2.isConference)
            assertEquals("http://logo/b.png", game2.opponentLogo)
            assertEquals(dt2, game2.dateTime)
            assertEquals(year, game2.season)
            assertEquals(2, game2.week, "Week should be cfb week")
        }

        @Test
        fun `updates and saves existing entity when found by id`() {
            val year = SeasonResolver.currentCfbSeason()
            val dt = Instant.parse("$year-09-28T18:00:00Z")

            val resp = scheduleResponse(
                buildGame(100, "Madison, WI", "Badgers", "http://logo/wisc.png", dt, true, "away"),
            )

            val existing = ScheduleEntity(100).apply {
                opponent = "OLD"
                location = "OLD"
                venueType = "home"
                isConference = false
                opponentLogo = "OLD"
                dateTime = Instant.parse("$year-01-01T00:00:00Z")
                season = year - 1
                week = 99
            }

            Mockito.`when`(huskersDotComService.getSchedule(year)).thenReturn(resp)
            Mockito.`when`(scheduleRepo.findById(100L)).thenReturn(Optional.of(existing))

            subject.init()

            val captor = ArgumentCaptor.forClass(ScheduleEntity::class.java)
            Mockito.verify(scheduleRepo).save(captor.capture())
            val saved = captor.value

            assertEquals(100L, saved.id)
            assertEquals("Badgers", saved.opponent)
            assertEquals("Madison, WI", saved.location)
            assertEquals("away", saved.venueType)
            assertTrue(saved.isConference)
            assertEquals("http://logo/wisc.png", saved.opponentLogo)
            assertEquals(dt, saved.dateTime)
            assertEquals(year, saved.season)
            assertEquals(5, saved.week)
        }

        @Test
        fun `does nothing when no games returned`() {
            val year = SeasonResolver.currentCfbSeason()
            val empty = mapper.createObjectNode().apply {
                set<ArrayNode>("data", mapper.createArrayNode())
            }

            Mockito.`when`(huskersDotComService.getSchedule(year)).thenReturn(empty)

            subject.init()

            Mockito.verify(scheduleRepo, Mockito.never()).save(Mockito.any())
        }
    }
}
