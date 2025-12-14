package org.j3y.HuskerBot2

import com.fasterxml.jackson.databind.node.ArrayNode
import jakarta.annotation.PostConstruct
import org.j3y.HuskerBot2.automation.backup.DatabaseBackupService
import org.j3y.HuskerBot2.model.ScheduleEntity
import org.j3y.HuskerBot2.repository.ScheduleRepo
import org.j3y.HuskerBot2.scheduler.SunriseSunsetScheduler
import org.j3y.HuskerBot2.service.HuskersDotComService
import org.j3y.HuskerBot2.util.SeasonResolver
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class StartupSchedulePopulate {
    @Autowired(required = false)
    private var databaseBackupService: DatabaseBackupService? = null
    private val log = LoggerFactory.getLogger(StartupSchedulePopulate::class.java)

    @Autowired private lateinit var scheduleRepo: ScheduleRepo
    @Autowired lateinit var huskersDotComService: HuskersDotComService
    @Autowired lateinit var a: SunriseSunsetScheduler

    @PostConstruct
    fun init() {
        //a.scheduleDailySunMessages()
        val year: Int = SeasonResolver.currentCfbSeason()
        val games = huskersDotComService.getSchedule(year).path("data") as ArrayNode
        games.forEach { game ->
            val id = game.path("id").asLong()
            val location = game.path("location").asText()
            val opponent = game.path("opponent_name").asText()
            val opponentLogo = game.path("opponent_logo").path("url").asText()
            val datetime = Instant.parse(game.path("datetime").asText())
            val isConference = game.path("is_conference").asBoolean()
            val venueType = game.path("venue_type").asText()
            val venue = game.path("venue").asText()

            val week = SeasonResolver.getCfbWeek(datetime)

            val sched: ScheduleEntity = scheduleRepo.findById(id).orElse(ScheduleEntity(id))
            sched.opponent = opponent
            sched.location = location
            sched.venueType = venueType
            sched.isConference = isConference
            sched.opponentLogo = opponentLogo
            sched.dateTime = datetime
            sched.season = year
            sched.week = week
            sched.venue = venue
            sched.isDome = isDomeVenue(venue)

            log.info("Saving Sched Item: {} - {} - {} - venue: {} - isDome: {}", 
                sched.id, sched.opponent, sched.dateTime.toString(), sched.venue, sched.isDome)

            scheduleRepo.save(sched)
        }

        databaseBackupService?.runBackup()
    }
    
    /**
     * Detects if a venue is an indoor/dome stadium based on its name.
     * Uses pattern matching on common dome stadium naming conventions.
     */
    private fun isDomeVenue(venueName: String): Boolean {
        if (venueName.isBlank()) return false
        
        val venueUpper = venueName.uppercase()
        
        // Known dome/indoor stadium keywords
        val domeKeywords = listOf(
            "DOME",
            "FIELD HOUSE",
            "FIELDHOUSE",
            "INDOOR"
        )
        
        // Specific known dome stadiums (in case name doesn't include "dome")
        val knownDomes = listOf(
            "LUCAS OIL",          // Indianapolis - Big Ten Championship
            "FORD FIELD",         // Detroit
            "US BANK STADIUM",    // Minneapolis
            "U.S. BANK STADIUM",
            "ALLEGIANT STADIUM",  // Las Vegas
            "AT&T STADIUM",       // Arlington, TX
            "CAESARS SUPERDOME",  // New Orleans
            "MERCEDES-BENZ STADIUM", // Atlanta
            "NRG STADIUM",        // Houston (retractable roof)
            "SOFI STADIUM",       // Los Angeles (covered)
            "STATE FARM STADIUM", // Arizona (retractable roof)
            "CARRIER DOME",       // Syracuse
            "JMA WIRELESS DOME",  // Syracuse (renamed)
            "KIBBIE DOME"         // Idaho
        )
        
        // Check keywords
        if (domeKeywords.any { venueUpper.contains(it) }) {
            return true
        }
        
        // Check known domes
        if (knownDomes.any { venueUpper.contains(it) }) {
            return true
        }
        
        return false
    }
}
