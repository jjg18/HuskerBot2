package org.j3y.HuskerBot2.commands.ai

import tools.jackson.databind.JsonNode
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import org.j3y.HuskerBot2.commands.SlashCommand
import org.j3y.HuskerBot2.service.CfbMatchupService
import org.j3y.HuskerBot2.service.GoogleGeminiService
import org.j3y.HuskerBot2.util.SeasonResolver
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.awt.Color
import java.time.LocalDate
import java.time.OffsetDateTime

@Component
class AiMatchup(
    private val gemini: GoogleGeminiService,
    private val cfb: CfbMatchupService,
) : SlashCommand() {

    private val log = LoggerFactory.getLogger(AiMatchup::class.java)

    override fun getCommandKey(): String = "ai-matchup"
    override fun getDescription(): String = "AI analysis of a CFB head-to-head matchup using season-to-date data"

    override fun getOptions(): List<OptionData> = listOf(
        OptionData(OptionType.STRING, "team1", "Team 1 (e.g., Nebraska)", true),
        OptionData(OptionType.STRING, "team2", "Team 2 (e.g., Iowa)", true),
        OptionData(OptionType.INTEGER, "year", "Season year (defaults to current year)", false)
    )

    override fun execute(commandEvent: SlashCommandInteractionEvent) {
        commandEvent.deferReply(false).queue()
        try {
            val team1 = commandEvent.getOption("team1")?.asString?.trim().orEmpty()
            val team2 = commandEvent.getOption("team2")?.asString?.trim().orEmpty()
            val year = (commandEvent.getOption("year")?.asLong?.toInt()) ?: SeasonResolver.currentCfbSeason()

            if (team1.isBlank() || team2.isBlank()) {
                commandEvent.hook.sendMessage("Usage: /ai-matchup team1:<name> team2:<name> [year:<year>]").queue()
                return
            }

            // Fetch season-to-date for both teams
            val t1Games = cfb.getTeamSeasonGames(team1, year)
            val t2Games = cfb.getTeamSeasonGames(team2, year)

            if (t1Games == null || t2Games == null) {
                commandEvent.hook.sendMessage("Couldn't find any season data for $team1 or $team2 in $year.").queue()
                return
            }

            val prompt = buildPrompt(team1, team2, year, t1Games, t2Games)
            val analysis = gemini.generateText(prompt)

            val chunks = splitIntoEmbedChunks(analysis)

            val embeds = chunks.mapIndexed { idx, chunk ->
                val eb = EmbedBuilder()
                    .setColor(Color(0xC3, 0x18, 0x2B))
                    .setDescription(chunk)
                    .setTimestamp(OffsetDateTime.now())
                if (idx == 0) {
                    eb.setTitle("AI Matchup: $team1 vs $team2 ($year)")
                    eb.setFooter("Requested by ${commandEvent.member?.effectiveName ?: commandEvent.user.effectiveName}", commandEvent.user.avatarUrl)
                } else {
                    eb.setTitle("AI Matchup: $team1 vs $team2 ($year) — continued ${idx + 1}/${chunks.size}")
                }
                eb.build()
            }

            // Discord allows up to 10 embeds per message; send in batches if needed
            var i = 0
            while (i < embeds.size) {
                val end = kotlin.math.min(i + 10, embeds.size)
                commandEvent.hook.sendMessageEmbeds(embeds.subList(i, end)).queue()
                i = end
            }
        } catch (e: Exception) {
            log.error("Error executing /ai-matchup", e)
            commandEvent.hook.sendMessage("Error while generating matchup analysis: ${e.message}").setEphemeral(true).queue()
        }
    }

    private fun buildPrompt(
        team1: String,
        team2: String,
        year: Int,
        t1Games: JsonNode,
        t2Games: JsonNode
    ): String {
        val sb = StringBuilder()
        sb.appendLine("""
            Here's data from two different college football teams for their seasons so far. Compare their data and then predict the outcome of a head to head matchup with a final score prediction. Keep the comparison brief. 
        """.trimIndent())
        sb.appendLine()
        sb.appendLine("Important:")
        sb.appendLine("- Base your reasoning only on the provided results; do not invent players/injuries.")
        sb.appendLine("- If data seems incomplete, note uncertainty.")
        sb.appendLine()
        sb.appendLine("Team 1: $team1")
        sb.appendLine(t1Games.toString())
        sb.appendLine()
        sb.appendLine("Team 2: $team2")
        sb.appendLine(t2Games.toString())

        log.info("Generated prompt for AI matchup:\n\n $sb")
        return sb.toString().take(100_000) // keep prompt within limits
    }

    private fun splitIntoEmbedChunks(text: String, chunkSize: Int = 4000): List<String> {
        if (text.isBlank()) return listOf("(no content)")
        val result = mutableListOf<String>()
        var idx = 0
        val len = text.length
        while (idx < len) {
            val end = (idx + chunkSize).coerceAtMost(len)
            result.add(text.substring(idx, end))
            idx = end
        }
        return result
    }
}
