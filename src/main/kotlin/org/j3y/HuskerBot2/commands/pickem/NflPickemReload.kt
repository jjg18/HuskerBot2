package org.j3y.HuskerBot2.commands.pickem

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import org.j3y.HuskerBot2.automation.pickem.nfl.NflPickemProcessing
import org.j3y.HuskerBot2.commands.SlashCommand
import org.j3y.HuskerBot2.util.SeasonResolver
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class NflPickemReload : SlashCommand() {
    private val log = LoggerFactory.getLogger(NflPickemReload::class.java)

    @Autowired lateinit var nflPickemProcessing: NflPickemProcessing

    override fun getCommandKey(): String = "reload"
    override fun isSubcommand(): Boolean = true
    override fun getDescription(): String = "Reload the NFL Pick'em Listing. No picks will be deleted."
    override fun getOptions(): List<OptionData> {
        val weekChoices: MutableList<Command.Choice> = mutableListOf()

        for (w in 1..19) {
            weekChoices.add(Command.Choice("Week $w", w.toLong()))
        }

        return listOf(
            OptionData(OptionType.INTEGER, "week", "NFL week number. Default is current NFL week.", false).addChoices(weekChoices)
        )
    }
    override fun getPermissions(): DefaultMemberPermissions = DefaultMemberPermissions.enabledFor(net.dv8tion.jda.api.Permission.MESSAGE_MANAGE)

    override fun execute(commandEvent: SlashCommandInteractionEvent) {
        commandEvent.deferReply(true).queue()
        val week = commandEvent.getOption("week")?.asInt ?: SeasonResolver.currentNflWeek()

        if (commandEvent.member?.hasPermission(net.dv8tion.jda.api.Permission.MESSAGE_MANAGE) != true) {
            commandEvent.hook.sendMessage("You do not have permission to use this command.").queue()
            return
        }

        try {
            nflPickemProcessing.postWeeklyPickem(week)
            commandEvent.hook.sendMessage("Reloaded the NFL Pick'em Listing.").queue()
        } catch (e: Exception) {
            log.error("Error while reloading the NFL Pick'em Listing: ${e.message}", e);
            commandEvent.hook.sendMessage("There was an issue reloading the NFL Pick'em Listing: ${e.message}").queue()
        }

    }
}