package org.j3y.HuskerBot2.scheduler

import com.github.kagkarlsson.scheduler.SchedulerClient
import com.github.kagkarlsson.scheduler.task.Task
import com.github.kagkarlsson.scheduler.task.helper.Tasks
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.EmbedBuilder
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import java.awt.Color
import java.io.Serializable
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Defines the db-scheduler Task for sending reminders and provides a service to schedule them.
 */
@Configuration
class ReminderSchedulerConfig {
    data class ReminderPayload(val channelId: Long, val userId: Long, val message: String) : Serializable

    companion object {
        const val TASK_NAME: String = "reminder-send"
    }

    @Bean
    fun reminderTask(@Lazy jda: JDA): Task<ReminderPayload> {
        val log = LoggerFactory.getLogger("ReminderTask")
        return Tasks.oneTime(TASK_NAME, ReminderPayload::class.java)
            .execute { taskInstance, _ ->
                val payload = taskInstance.getData()
                val channel = jda.getTextChannelById(payload.channelId)
                    ?: jda.getThreadChannelById(payload.channelId)
                    ?: jda.getNewsChannelById(payload.channelId)
                if (channel == null) {
                    log.warn("Reminder task could not find channel with id {}", payload.channelId)
                    return@execute
                }

                val mention = "<@${payload.userId}>"

                val embed = EmbedBuilder()
                    .setTitle("⏰ Reminder")
                    .setColor(Color(0xE6, 0x27, 0x27))
                    .setDescription(payload.message)
                    .addField("Reminder sent by", mention, true)
                    .setTimestamp(OffsetDateTime.now())
                    .build()

                channel.sendMessageEmbeds(embed).queue(
                    { log.info("Sent reminder to channel {}", payload.channelId) },
                    { ex -> log.error("Failed to send reminder to channel {}: {}", payload.channelId, ex.message) }
                )
            }
    }
}

@Service
class ReminderService(
    private val schedulerClient: SchedulerClient,
    private val reminderTask: Task<ReminderSchedulerConfig.ReminderPayload>
) {
    private val log = LoggerFactory.getLogger(ReminderService::class.java)

    fun scheduleReminder(channelId: Long, userId: Long, message: String, executionTime: Instant) {
        val instanceId = UUID.randomUUID().toString()
        val payload = ReminderSchedulerConfig.ReminderPayload(channelId, userId, message)
        val taskInstance = reminderTask.instance(instanceId, payload)
        log.info("Scheduling reminder for channel {} at {}", channelId, executionTime)
        schedulerClient.scheduleIfNotExists(taskInstance, executionTime)
    }
}
