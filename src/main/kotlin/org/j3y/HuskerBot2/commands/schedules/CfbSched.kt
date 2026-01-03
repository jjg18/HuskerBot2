package org.j3y.HuskerBot2.commands.schedules

import com.fasterxml.jackson.databind.JsonNode
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import org.j3y.HuskerBot2.commands.SlashCommand
import org.j3y.HuskerBot2.service.EspnService
import org.j3y.HuskerBot2.util.SeasonResolver
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component


@Component
class CfbSched : SlashCommand() {

    @Autowired
    lateinit var espnService: EspnService

    private val leagueMap: Map<String, Int> = mapOf(
        "top25" to 0,
        "acc" to 1,
        "american" to 151,
        "big12" to 4,
        "big10" to 5,
        "sec" to 8,
        "pac12" to 9,
        "mac" to 15,
        "independent" to 18
    )

    private val leagueLabelMap: Map<String, String> = mapOf(
        "top25" to "Top 25",
        "acc" to "ACC",
        "american" to "American",
        "big12" to "Big 12",
        "big10" to "Big 10",
        "sec" to "SEC",
        "pac12" to "Pac 12",
        "mac" to "MAC",
        "independent" to "Independent"
    )

    private val weekLabelMap: Map<String, Int> = mapOf(
        "Week 1" to 1,
        "Week 2" to 2,
        "Week 3" to 3,
        "Week 4" to 4,
        "Week 5" to 5,
        "Week 6" to 6,
        "Week 7" to 7,
        "Week 8" to 8,
        "Week 9" to 9,
        "Week 10" to 10,
        "Week 11" to 11,
        "Week 12" to 12,
        "Week 13" to 13,
        "Week 14" to 14,
        "Week 15" to 15,
        "Week 16" to 16,
        "Bowls" to -1,
        "College Football Playoffs" to -2
    )

    override fun getCommandKey(): String = "cfb"
    override fun getDescription(): String = "Get the CFB schedules for a given week and/or league"
    override fun isSubcommand(): Boolean = true

    override fun getOptions(): List<OptionData> {
        val leagueOption = OptionData(OptionType.STRING, "league", "The league to get the schedule for (top25, acc, american, b12, b10, sec, p12, mac, independent)", false)
        leagueLabelMap.forEach { (league, label) -> leagueOption.addChoice(label, league) }

        val weekOption = OptionData(OptionType.INTEGER, "week", "The CFB week you would like the schedule for", false)
        weekLabelMap.forEach { (label, week) -> weekOption.addChoice(label, week.toLong()) }

        return listOf(
            leagueOption,
            weekOption,
        )
    }

    override fun execute(commandEvent: SlashCommandInteractionEvent) {
        commandEvent.deferReply().queue()

        val leagueStr = commandEvent.getOption("league")?.getAsString() ?: "top25"
        val league = leagueMap[leagueStr] ?: -1
        if (league == -1) {
            commandEvent.hook.sendMessage("That league was not recognized.").queue()
            return
        }

        var weekInt = commandEvent.getOption("week")?.asInt ?: SeasonResolver.currentCfbWeek()

        if (weekInt < 1 && weekInt != -1 && weekInt != -2 || weekInt > 17) {
            // Just set it to bowls if week is out of range
            weekInt = -1
        }

        val apiJson: JsonNode = espnService.getCfbScoreboard(league, weekInt)

        val embeds = espnService.buildEventEmbed(apiJson)
        val weekLabel = weekLabelMap.entries.find { it.value == weekInt }?.key ?: "Week $weekInt"
        var leagueLabel = leagueLabelMap[leagueStr] ?: league
        if (weekInt > 0) {
            leagueLabel = " for $leagueLabel"
        } else {
            leagueLabel = ""
        }

        if (embeds.isEmpty()) {
            commandEvent.hook.sendMessage("No games found$leagueLabel in $weekLabel").queue()
            return
        }

        val embedChunks = embeds.chunked(10)
        embedChunks.forEachIndexed { index, chunk ->
            if (index == 0) {
                commandEvent.hook.sendMessage("## \uD83C\uDFC8 \u200E CFB Schedule $leagueLabel in $weekLabel")
                    .addEmbeds(chunk)
                    .queue()
            } else {
                commandEvent.hook.sendMessageEmbeds(chunk)
                    .queue()
            }
        }
    }
}
