package org.j3y.HuskerBot2.scheduler

import com.github.kagkarlsson.scheduler.task.Task
import com.github.kagkarlsson.scheduler.task.helper.Tasks
import com.github.kagkarlsson.scheduler.task.schedule.CronSchedule
import org.j3y.HuskerBot2.automation.betting.BetProcessing
import org.j3y.HuskerBot2.automation.backup.DatabaseBackupService
import org.j3y.HuskerBot2.automation.pickem.nfl.NflPickemProcessing
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy
import java.time.ZoneId

@Configuration
class AutomationSchedulerConfig {
    private val log = LoggerFactory.getLogger(AutomationSchedulerConfig::class.java)

    @Bean
    fun betProcessingRecurringTask(@Lazy betProcessing: BetProcessing): Task<Void> {
        val schedule = CronSchedule("0 0 2 * * MON", ZoneId.of("America/Chicago"))
        return Tasks.recurring("bet-processing", schedule)
            .execute { _, _ ->
                log.info("Running recurring task: bet-processing")
                betProcessing.processBets()
            }
    }

    @Bean
    fun nflPickemRecurringTask(@Lazy nflPickemProcessing: NflPickemProcessing): Task<Void> {
        val schedule = CronSchedule("0 0 2 * * TUE", ZoneId.of("America/Chicago"))
        return Tasks.recurring("nfl-pickem-post-weekly", schedule)
            .execute { _, _ ->
                log.info("Running recurring task: nfl-pickem-post-weekly")
                nflPickemProcessing.postWeeklyPickem()
            }
    }

    @Bean
    fun databaseBackupHourlyTask(@Lazy databaseBackupService: DatabaseBackupService): Task<Void> {
        // Run at the top of every hour in America/Chicago timezone
        val schedule = CronSchedule("0 0 * * * *", ZoneId.of("America/Chicago"))
        return Tasks.recurring("database-backup-hourly", schedule)
            .execute { _, _ ->
                log.info("Running recurring task: database-backup-hourly")
                databaseBackupService.runBackup()
            }
    }
}
