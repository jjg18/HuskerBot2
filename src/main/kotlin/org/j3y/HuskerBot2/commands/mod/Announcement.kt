package org.j3y.HuskerBot2.commands.mod

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Message.MentionType
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import org.j3y.HuskerBot2.commands.SlashCommand
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.awt.Color

@Component
class Announcement : SlashCommand() {
    @Value("\${discord.channels.announcements}") lateinit var announcementsChannelId: String

    override fun getCommandKey(): String = "announcement"
    override fun getDescription(): String = "Send an announcement to the configured announcements channel."
    override fun getPermissions(): DefaultMemberPermissions = DefaultMemberPermissions.enabledFor(Permission.MESSAGE_MANAGE)

    override fun getOptions(): List<OptionData> = listOf(
        OptionData(OptionType.STRING, "message", "The announcement message", true)
    )

    override fun execute(commandEvent: SlashCommandInteractionEvent) {
        val message = commandEvent.getOption("message")?.asString ?: ""

        val channel: TextChannel? = commandEvent.jda.getTextChannelById(announcementsChannelId)
        if (channel == null) {
            commandEvent.reply("Announcement channel not found.").setEphemeral(true).queue()
            return
        }

        val embed = EmbedBuilder()
            .setTitle("Server Announcement")
            .setDescription(message)
            .setColor(Color.RED)
            .setFooter("And remember, Go Big Red.")
            .build()

        channel
            .sendMessage("@everyone")
            .setEmbeds(embed)
            .setAllowedMentions(listOf(MentionType.EVERYONE))
            .queue()

        commandEvent.reply("Announcement sent!").setEphemeral(true).queue()
    }
}
