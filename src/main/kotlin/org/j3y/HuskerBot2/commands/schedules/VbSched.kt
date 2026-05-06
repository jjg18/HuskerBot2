package org.j3y.HuskerBot2.commands.schedules

import tools.jackson.databind.JsonNode
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import org.j3y.HuskerBot2.commands.SlashCommand
import org.j3y.HuskerBot2.service.HuskersDotComService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.awt.Color
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Component
class VbSched : SlashCommand() {

    @Autowired
    lateinit var huskersDotComService: HuskersDotComService

    private val volleyballScheduleId = 1363

    override fun getCommandKey(): String = "vb"
    override fun getDescription(): String = "Get the Nebraska volleyball schedule"
    override fun isSubcommand(): Boolean = true

    override fun getOptions(): List<OptionData> = emptyList()

    override fun execute(commandEvent: SlashCommandInteractionEvent) {
        commandEvent.deferReply().queue()

        try {
            val apiJson: JsonNode = huskersDotComService.getScheduleById(volleyballScheduleId)
            val events = apiJson.path("data")

            if (events.isEmpty) {
                commandEvent.hook.sendMessage("No volleyball schedule found.").queue()
                return
            }

            val embeds = buildScheduleEmbeds(events)
            commandEvent.hook.sendMessage("## \uD83C\uDFD0 Nebraska Volleyball Schedule").addEmbeds(embeds).queue()
        } catch (e: Exception) {
            commandEvent.hook.sendMessage("Failed to retrieve volleyball schedule: ${e.message}").queue()
        }
    }

    private fun buildScheduleEmbeds(events: JsonNode): List<net.dv8tion.jda.api.entities.MessageEmbed> {
        val embeds = mutableListOf<net.dv8tion.jda.api.entities.MessageEmbed>()
        val embedBuilder = EmbedBuilder()
        var fieldCount = 0

        for (event in events) {
            // Filter out non-Nebraska games (e.g., tournament games where Nebraska isn't playing)
            if (!isNebraskaGame(event)) {
                continue
            }
            
            if (fieldCount >= 25) {
                embeds.add(embedBuilder.build())
                embedBuilder.clear()
                fieldCount = 0
            }

            val opponent = event.path("opponent_name").asText("TBD")
            val venueType = event.path("venue_type").asText("")
            val location = event.path("location").asText("")
            val datetime = event.path("datetime").asText()
            val status = event.path("status").asText("")
            val result = event.path("schedule_event_result").path("result").asText("")

            // Get network information from schedule_event_links icon data
            val network = extractNetworkFromScheduleLinks(event.path("schedule_event_links"))
            
            // Get score information
            val nebraskaScore = event.path("schedule_event_result").path("nebraska_score").asText("")
            val opponentScore = event.path("schedule_event_result").path("opponent_score").asText("")

            val gameDateTime = if (datetime.isNotEmpty()) {
                try {
                    val instant = Instant.parse(datetime)
                    val zdt = instant.atZone(ZoneId.of("America/Chicago"))
                    zdt.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM))
                } catch (e: Exception) {
                    "TBD"
                }
            } else {
                "TBD"
            }

            val venueInfo = when (venueType.lowercase()) {
                "home" -> "vs $opponent"
                "away" -> "@ $opponent"
                "neutral" -> "vs $opponent (N)"
                else -> "vs $opponent"
            }

            val statusIcon = when {
                result == "win" -> "✅"
                result == "loss" -> "❌" 
                status == "completed" -> "✅"
                else -> "🕐"
            }

            // Build field value with scores if available
            val scoreInfo = if (result.isNotEmpty() && nebraskaScore.isNotEmpty() && opponentScore.isNotEmpty()) {
                val nebScore = nebraskaScore.toIntOrNull() ?: 0
                val oppScore = opponentScore.toIntOrNull() ?: 0
                "\n**Score:** Nebraska $nebScore - $opponent $oppScore"
            } else {
                ""
            }

            val networkInfo = if (network != "TBD") " • $network" else ""
            val fieldValue = "$statusIcon $gameDateTime$scoreInfo\n📍 $location$networkInfo"

            embedBuilder.addField(venueInfo, fieldValue, true)
            fieldCount++
        }

        if (fieldCount > 0) {
            embeds.add(embedBuilder.build())
        }

        // Set color for all embeds
        return embeds.map { embed ->
            EmbedBuilder(embed).setColor(Color.RED).build()
        }
    }

    /**
     * Determines if Nebraska is actually playing in this game.
     * This helps filter out tournament games where Nebraska hosts but isn't playing.
     */
    internal fun isNebraskaGame(event: JsonNode): Boolean {
        // The most reliable way to determine if this is a Nebraska game:
        // If second_opponent_id is null, it's a Nebraska game
        // If second_opponent_id is not null, it's a game between two other teams
        val secondOpponentId = event.path("second_opponent_id")
        
        if (!secondOpponentId.isMissingNode && !secondOpponentId.isNull) {
            // This is a game between two other teams (tournament game)
            return false
        }
        
        // Additional validation: ensure we have basic game data
        val hasOpponent = event.path("opponent_name").asText("").isNotEmpty()
        
        // If second_opponent_id is null/missing and we have an opponent, it's a Nebraska game
        if (hasOpponent) {
            return true
        }
        
        // Fallback: check for Nebraska score data (indicates Nebraska is playing)
        val scheduleEventResult = event.path("schedule_event_result")
        val hasNebraskScore = scheduleEventResult.path("nebraska_score").asText("").isNotEmpty() ||
                             scheduleEventResult.path("opponent_score").asText("").isNotEmpty()
        
        return hasNebraskScore
    }

    internal fun extractNetworkFromScheduleLinks(scheduleLinks: JsonNode): String {
        // Look for TV/broadcast related links
        for ((_, link) in scheduleLinks.withIndex()) {
            val icon = link.path("icon")
            val iconName = icon.path("name").asText("")
            val iconTitle = icon.path("title").asText("")
            val iconAlt = icon.path("alt").asText("")

            // Check if this is a TV/broadcast link
            val isTvLink = iconName.contains("tv", ignoreCase = true) ||
                          iconTitle.contains("tv", ignoreCase = true) ||
                          iconAlt.contains("tv", ignoreCase = true) ||
                          iconName.contains("broadcast", ignoreCase = true) ||
                          iconTitle.contains("broadcast", ignoreCase = true) ||
                          iconAlt.contains("broadcast", ignoreCase = true) ||
                          // Check for known TV networks
                          iconName.contains("espn", ignoreCase = true) ||
                          iconTitle.contains("espn", ignoreCase = true) ||
                          iconAlt.contains("espn", ignoreCase = true) ||
                          iconName.contains("fs1", ignoreCase = true) ||
                          iconTitle.contains("fs1", ignoreCase = true) ||
                          iconAlt.contains("fs1", ignoreCase = true) ||
                          iconName.contains("fox sports 1", ignoreCase = true) ||
                          iconTitle.contains("fox sports 1", ignoreCase = true) ||
                          iconAlt.contains("fox sports 1", ignoreCase = true) ||
                          iconName.contains("fox", ignoreCase = true) ||
                          iconTitle.contains("fox", ignoreCase = true) ||
                          iconAlt.contains("fox", ignoreCase = true) ||
                          iconName.contains("btn", ignoreCase = true) ||
                          iconTitle.contains("btn", ignoreCase = true) ||
                          iconAlt.contains("btn", ignoreCase = true) ||
                          iconAlt.contains("big ten network", ignoreCase = true) ||
                          iconAlt.contains("big ten", ignoreCase = true) ||
                          iconName.contains("b1g+", ignoreCase = true) ||
                          iconTitle.contains("b1g+", ignoreCase = true) ||
                          iconAlt.contains("b1g+", ignoreCase = true) ||
                          iconAlt.contains("big ten plus", ignoreCase = true) ||
                          iconName.contains("abc", ignoreCase = true) ||
                          iconTitle.contains("abc", ignoreCase = true) ||
                          iconAlt.contains("abc", ignoreCase = true) ||
                          iconName.contains("npm", ignoreCase = true) ||
                          iconTitle.contains("npm", ignoreCase = true) ||
                          iconAlt.contains("npm", ignoreCase = true)

            if (isTvLink) {
                // Try to extract network from title first (most reliable)
                val networkFromTitle = extractNetworkFromText(iconTitle)
                if (networkFromTitle != "TBD") return networkFromTitle
                
                // Try alt field next
                val networkFromAlt = extractNetworkFromText(iconAlt)
                if (networkFromAlt != "TBD") return networkFromAlt
                
                // Fall back to filename
                val networkFromFilename = extractNetworkFromIconFilename(iconName)
                if (networkFromFilename != "TBD") return networkFromFilename
            }
        }

        return "TBD"
    }

    private fun extractNetworkFromText(text: String): String {
        // Extract network name from text (title, alt text, etc.)
        // Check more specific patterns first to avoid false matches
        return when {
            text.contains("espn+", ignoreCase = true) -> "ESPN+"
            text.contains("b1g+", ignoreCase = true) || text.contains("big ten plus", ignoreCase = true) -> "B1G+"
            text.contains("btn", ignoreCase = true) || text.contains("big ten network", ignoreCase = true) || text.contains("big ten", ignoreCase = true) -> "BTN"
            text.contains("espn", ignoreCase = true) -> "ESPN"
            text.contains("fs1", ignoreCase = true) || text.contains("fox sports 1", ignoreCase = true) -> "FS1"
            text.contains("fox", ignoreCase = true) -> "FOX"
            text.contains("abc", ignoreCase = true) -> "ABC"
            text.contains("npm", ignoreCase = true) -> "NPM"
            else -> "TBD"
        }
    }

    private fun extractNetworkFromIconFilename(iconName: String): String {
        // Extract network name from icon filename
        // Common patterns: "tv-espn.png", "tv-fox.svg", "network-name-tv.png", etc.
        // Check more specific patterns first to avoid false matches
        return when {
            iconName.contains("espn+", ignoreCase = true) -> "ESPN+"
            iconName.contains("b1g+", ignoreCase = true) -> "B1G+"
            iconName.contains("btn", ignoreCase = true) -> "BTN"
            iconName.contains("espn", ignoreCase = true) -> "ESPN"
            iconName.contains("fs1", ignoreCase = true) -> "FS1"
            iconName.contains("fox", ignoreCase = true) -> "FOX"
            iconName.contains("abc", ignoreCase = true) -> "ABC"
            iconName.contains("npm", ignoreCase = true) -> "NPM"
            else -> {
                // Try to extract network name from filename patterns
                val cleanName = iconName.lowercase()
                    .removePrefix("tv-")
                    .removePrefix("network-")
                    .removeSuffix(".png")
                    .removeSuffix(".svg")
                    .removeSuffix(".jpg")
                    .removeSuffix(".jpeg")
                    .removeSuffix("-tv")
                    .uppercase()
                    
                if (cleanName.isNotEmpty() && cleanName != "TV") cleanName else "TBD"
            }
        }
    }
}