package org.j3y.HuskerBot2.commands.other

import tools.jackson.databind.JsonNode
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import org.j3y.HuskerBot2.commands.SlashCommand
import org.j3y.HuskerBot2.service.EspnService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.awt.Color
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@Component
class Possum : SlashCommand() {
    @Value("\${discord.channels.possum}") lateinit var possumChannelId: String

    override fun getCommandKey(): String = "possum"
    override fun getDescription(): String = "Share possum droppings for the server"
    override fun getOptions(): List<OptionData> = listOf(
        OptionData(OptionType.STRING, "message", "The message to share", true),
    )

    override fun execute(commandEvent: SlashCommandInteractionEvent) {
        val message = commandEvent.getOption("message")?.asString ?: ""

        val possumChannel = commandEvent.jda.getTextChannelById(possumChannelId)

        if (possumChannel == null) {
            commandEvent.reply("Possum channel not found.").setEphemeral(true).queue()
            return
        }

        possumChannel.sendMessageEmbeds(
            EmbedBuilder()
                .setTitle("Possum Droppings")
                .setColor(Color.RED)
                .addField("Dropping", message, false)
                .setFooter("Created by a sneaky possum")
                .setThumbnail("https://media.discordapp.net/attachments/593984711706279937/875162041818693632/unknown.jpeg")
                .build()
        ).queue()

        commandEvent.reply("Possum droppings sent!").setEphemeral(true).queue()
    }
}