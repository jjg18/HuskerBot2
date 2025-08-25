package org.j3y.HuskerBot2.commands.other

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import org.j3y.HuskerBot2.commands.SlashCommand
import org.j3y.HuskerBot2.scheduler.ReminderService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Component
class Reminder(
    private val reminderService: ReminderService
) : SlashCommand() {

    private val log = LoggerFactory.getLogger(Reminder::class.java)

    override fun getCommandKey(): String = "reminder"
    override fun getDescription(): String = "Create a reminder to post a message at a specified time"

    override fun getOptions(): List<OptionData> = listOf(
        OptionData(OptionType.STRING, "message", "The message to post when the reminder triggers", true),
        OptionData(OptionType.STRING, "time", "When to post (ISO-8601 like 2025-08-23T18:00:00Z or duration like 24h, 16m)", true),
        OptionData(OptionType.CHANNEL, "channel", "Channel where the message will be posted (defaults to this channel)", false)
    )

    override fun execute(commandEvent: SlashCommandInteractionEvent) {
        commandEvent.deferReply().queue()
        try {
            val message = commandEvent.getOption("message")?.asString?.trim()
            val timeInput = commandEvent.getOption("time")?.asString?.trim()

            if (message.isNullOrBlank() || timeInput.isNullOrBlank()) {
                commandEvent.hook.sendMessage("You must provide both message and time.").queue()
                return
            }

            // Determine target channel
            val targetChannelId: Long = try {
                val provided = commandEvent.getOption("channel")?.asChannel
                if (provided != null) {
                    provided.idLong
                } else {
                    // Use the channel where the command was issued; must be a guild message channel
                    commandEvent.channel.asGuildMessageChannel().idLong
                }
            } catch (e: Exception) {
                commandEvent.hook.sendMessage("Please use this command in a guild text channel or specify a valid channel.").queue()
                return
            }

            val executionTime = parseTimeToInstant(timeInput)
            if (executionTime == null) {
                commandEvent.hook.sendMessage("I couldn't parse the time. Use ISO-8601 like 2025-08-23T18:00:00Z or a duration like 24h, 16m.").queue()
                return
            }

            val now = Instant.now()
            if (!executionTime.isAfter(now)) {
                commandEvent.hook.sendMessage("The time provided must be in the future.").queue()
                return
            }

            reminderService.scheduleReminder(targetChannelId, commandEvent.user.idLong, message, executionTime)

            val formatter = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy h:mm:ss a z").withLocale(Locale.US).withZone(ZoneId.systemDefault())
            val friendlyTime = formatter.format(executionTime)

            val embed = net.dv8tion.jda.api.EmbedBuilder()
                .setTitle("⏰ Reminder Scheduled")
                .setColor(java.awt.Color(0x3B, 0x88, 0xC3))
                .addField("Message", message, false)
                .addField("Submitted by", "<@${commandEvent.user.idLong}>", true)
                .addField("Scheduled for", friendlyTime, true)
                .setTimestamp(OffsetDateTime.now())
                .build()

            commandEvent.hook.sendMessageEmbeds(embed).queue()
        } catch (e: Exception) {
            log.error("Error executing /reminder", e)
            commandEvent.hook.sendMessage("Error while creating reminder: ${e.message}").queue()
        }
    }

    private fun parseTimeToInstant(input: String): Instant? {
        val trimmed = input.trim()

        // Support simple duration formats like "24h", "16m", "30s", "2d"
        val durationMatch = Regex("(?i)^(\\d+)\\s*([smhd])$").matchEntire(trimmed)
        if (durationMatch != null) {
            val amount = durationMatch.groupValues[1].toLongOrNull() ?: return null
            val unit = durationMatch.groupValues[2].lowercase()
            val seconds = when (unit) {
                "s" -> amount
                "m" -> amount * 60
                "h" -> amount * 3600
                "d" -> amount * 86400
                else -> return null
            }
            return Instant.now().plusSeconds(seconds)
        }

        // Try a few common absolute timestamp formats
        return tryParse {
            Instant.parse(trimmed)
        } ?: tryParse {
            OffsetDateTime.parse(trimmed).toInstant()
        } ?: tryParse {
            ZonedDateTime.parse(trimmed).toInstant()
        } ?: tryParse {
            // Assume system default zone if only local date-time provided
            LocalDateTime.parse(trimmed).atZone(ZoneId.systemDefault()).toInstant()
        }
    }

    private inline fun <T> tryParse(block: () -> T): T? = try { block() } catch (_: Exception) { null }
}
