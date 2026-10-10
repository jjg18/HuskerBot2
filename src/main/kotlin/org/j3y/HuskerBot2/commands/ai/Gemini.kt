package org.j3y.HuskerBot2.commands.ai

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import org.j3y.HuskerBot2.commands.SlashCommand
import org.j3y.HuskerBot2.service.GoogleGeminiService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.awt.Color

@Component
class Gemini(
    private val geminiService: GoogleGeminiService,
    @Value("\${discord.channels.bot-spam}") val botSpamChannelId: String
) : SlashCommand() {

    private val log = LoggerFactory.getLogger(Gemini::class.java)

    override fun getCommandKey(): String = "gemini"
    override fun getDescription(): String = "Send a text prompt to Google Gemini (free tier)"

    override fun getOptions(): List<OptionData> = listOf(
        OptionData(OptionType.STRING, "prompt", "The text prompt to send to Gemini", true),
        OptionData(OptionType.BOOLEAN, "search", "Whether to search for relevant information in the prompt", false)
    )

    override fun execute(commandEvent: SlashCommandInteractionEvent) {
        try {
            commandEvent.deferReply(true).queue()
            val prompt = commandEvent.getOption("prompt")?.asString?.trim()
            if (prompt.isNullOrBlank()) {
                commandEvent.hook.sendMessage("Please provide a prompt.").setEphemeral(true).queue()
                return
            }
            val search = commandEvent.getOption("search")?.asBoolean ?: false
            val sendPrompt = "$prompt\nPlease keep the response between 1 and 4 paragraphs."

            val response = geminiService.generateText(sendPrompt, null, search)
            val message = sanitizeForDiscord(response)

            // Split the response into 1024-character chunks and send as multiple embeds (one embed per chunk)
            val chunks = chunkString(message, 1024)
            val embeds = mutableListOf<MessageEmbed>()

            // Discord allows up to 10 embeds per message; cap to 3 and indicate truncation if necessary because too much is too much
            val maxEmbeds = 3
            val cappedChunks: MutableList<String> = if (chunks.size > maxEmbeds) chunks.take(maxEmbeds).toMutableList() else chunks.toMutableList()
            if (chunks.size > maxEmbeds) {
                // Append ellipsis to the last visible chunk to indicate more content was truncated
                val last = cappedChunks.removeAt(cappedChunks.lastIndex)
                val truncatedLast = if (last.length >= 3) last.substring(0, last.length - 3) + "..." else last + "..."
                cappedChunks.add(truncatedLast)
            }

            cappedChunks.forEachIndexed { index: Int, chunk: String ->
                val eb = EmbedBuilder().setColor(Color.CYAN)
                if (index == 0) {
                    eb.setTitle("Gemini AI")
                    eb.addField("Prompt", prompt, false)
                    eb.setFooter("Requested by ${commandEvent.member?.effectiveName ?: commandEvent.user.effectiveName}", commandEvent.user.avatarUrl)
                    eb.addField("Response", chunk, false)
                } else {
                    eb.addField("Response (cont. ${index + 1})", chunk, false)
                }
                embeds.add(eb.build())
            }

            val spamChannel = commandEvent.jda.getTextChannelById(botSpamChannelId)

            if (spamChannel == null) {
                commandEvent.reply("Bot spam channel not found.").setEphemeral(true).queue()
                return
            }

            val link = spamChannel.sendMessageEmbeds(embeds).complete().jumpUrl
            commandEvent.hook.sendMessage("Sent gemini output to bot spam channel: $link").setEphemeral(true).queue()
        } catch (e: Exception) {
            log.error("Error executing /gemini", e)
            commandEvent.hook.sendMessage("Error while calling Gemini: ${e.message}").setEphemeral(true).queue()
        }
    }

    private fun sanitizeForDiscord(text: String): String {
        // Basic sanitization to prevent unintended mentions, etc.
        return text
            .replace("@everyone", "@\u200Beveryone")
            .replace("@here", "@\u200Bhere")
            .takeIf { it.isNotBlank() } ?: "(no content)"
    }

    private fun chunkString(text: String, chunkSize: Int): List<String> {
        if (text.isEmpty() || chunkSize <= 0) return listOf("")
        val chunks = mutableListOf<String>()
        var i = 0
        val len = text.length
        while (i < len) {
            val end = (i + chunkSize).coerceAtMost(len)
            chunks.add(text.substring(i, end))
            i = end
        }
        return chunks
    }
}