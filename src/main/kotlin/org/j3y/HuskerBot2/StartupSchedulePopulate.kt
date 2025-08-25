package org.j3y.HuskerBot2

import com.fasterxml.jackson.databind.node.ArrayNode
import jakarta.annotation.PostConstruct
import org.j3y.HuskerBot2.automation.backup.DatabaseBackupService
import org.j3y.HuskerBot2.model.ScheduleEntity
import org.j3y.HuskerBot2.repository.ScheduleRepo
import org.j3y.HuskerBot2.service.HuskersDotComService
import org.j3y.HuskerBot2.util.WeekResolver
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDate

@Component
class StartupSchedulePopulate {
    @Autowired(required = false)
    private var databaseBackupService: DatabaseBackupService? = null
    private val log = LoggerFactory.getLogger(StartupSchedulePopulate::class.java)

    @Autowired private lateinit var scheduleRepo: ScheduleRepo
    @Autowired lateinit var huskersDotComService: HuskersDotComService

    @PostConstruct
    fun init() {
        val year: Int = LocalDate.now().year
        val games = huskersDotComService.getSchedule(year).path("data") as ArrayNode
        games.forEachIndexed { index, game ->
            val id = game.path("id").asLong()
            val location = game.path("location").asText()
            val opponent = game.path("opponent_name").asText()
            val opponentLogo = game.path("opponent_logo").path("url").asText()
            val datetime = Instant.parse(game.path("datetime").asText())
            val isConference = game.path("is_conference").asBoolean()
            val venueType = game.path("venue_type").asText()

            val week = WeekResolver.getCfbWeek(datetime)

            val sched: ScheduleEntity = scheduleRepo.findById(id).orElse(ScheduleEntity(id))
            sched.opponent = opponent
            sched.location = location
            sched.venueType = venueType
            sched.isConference = isConference
            sched.opponentLogo = opponentLogo
            sched.dateTime = datetime
            sched.season = year
            sched.week = week

            log.info("Saving Sched Item: {} - {} - {}", sched.id, sched.opponent, sched.dateTime.toString())

            scheduleRepo.save(sched)
        }

        databaseBackupService?.runBackup()
    }
}
