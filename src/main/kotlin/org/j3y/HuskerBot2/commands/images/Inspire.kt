package org.j3y.HuskerBot2.commands.images

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import org.j3y.HuskerBot2.commands.SlashCommand
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@Component
class Inspire : SlashCommand() {
    override fun getCommandKey(): String = "inspire"
    override fun getDescription(): String = "Get an inspirational image from InspiroBot. Optionally tag a user to inspire."

    override fun getOptions(): List<OptionData> = listOf(
        OptionData(OptionType.USER, "user", "User you want to inspire", false)
    )

    override fun execute(commandEvent: SlashCommandInteractionEvent) {
        // We'll reply publicly by default
        commandEvent.deferReply().queue()

        val client = HttpClient.newHttpClient()
        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://inspirobot.me/api?generate=true"))
            .GET()
            .build()

        try {
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) {
                commandEvent.hook.sendMessage("Failed to reach InspiroBot (status ${response.statusCode()}). Please try again later.").queue()
                return
            }
            val imageUrl = response.body().trim()
            if (imageUrl.isEmpty() || !imageUrl.startsWith("http")) {
                commandEvent.hook.sendMessage("InspiroBot returned an unexpected response. Please try again later.").queue()
                return
            }

            val commandUserMention = commandEvent.user.asMention
            val optionUser = commandEvent.getOption("user")?.asUser

            val message = if (optionUser != null) {
                "$commandUserMention wants to inspire ${optionUser.asMention}:"
            } else {
                "$commandUserMention wants to be inspired:"
            }

            commandEvent.hook.sendMessage(message).addEmbeds(EmbedBuilder().setImage(imageUrl).build()).queue()
        } catch (e: Exception) {
            commandEvent.hook.sendMessage("Error calling InspiroBot: ${e.message}").queue()
        }
    }
}