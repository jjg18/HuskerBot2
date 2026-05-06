package org.j3y.HuskerBot2.commands.schedules

import tools.jackson.databind.JsonNode
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import org.j3y.HuskerBot2.commands.SlashCommand
import org.j3y.HuskerBot2.service.EspnService
import org.j3y.HuskerBot2.util.SeasonResolver
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class NflSched : SlashCommand() {

    @Autowired
    lateinit var espnService: EspnService

    override fun getCommandKey(): String = "nfl"
    override fun getDescription(): String = "Get the NFL schedules (or scores) for a given week"
    override fun isSubcommand(): Boolean = true

    override fun getOptions(): List<OptionData> = listOf(
        OptionData(OptionType.INTEGER, "week", "The NFL week you would like the schedule for", false),
    )

    override fun execute(commandEvent: SlashCommandInteractionEvent) {
        commandEvent.deferReply().queue()
        val week = commandEvent.getOption("week")?.getAsInt() ?: SeasonResolver.currentNflWeek()

        val apiJson: JsonNode = espnService.getNflScoreboard(week)
        val embeds = espnService.buildEventEmbed(apiJson)
        commandEvent.hook.sendMessage("## \uD83C\uDFC8 \u200E NFL Schedule for Week $week").addEmbeds(embeds).queue()
    }

}
