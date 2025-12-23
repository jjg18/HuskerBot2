package org.j3y.HuskerBot2.commands.ai

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.utils.FileUpload
import org.j3y.HuskerBot2.commands.SlashCommand
import org.j3y.HuskerBot2.service.GoogleGeminiService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.net.HttpURLConnection
import java.net.URI

@Component
class AiImageUpdate : SlashCommand() {
    @Autowired
    lateinit var gemini: GoogleGeminiService

    @Value("\${discord.channels.bot-spam}")
    lateinit var botSpamChannelId: String

    override fun getCommandKey(): String = "ai-image-update"
    override fun getDescription(): String = "Transform an existing image using Gemini with a guiding prompt"
    override fun getOptions(): List<OptionData> = listOf(
        OptionData(OptionType.STRING, "url", "Direct URL to the image to transform (http/https)", true),
        OptionData(OptionType.STRING, "prompt", "Describe how you want the image transformed", true)
    )

    override fun execute(commandEvent: SlashCommandInteractionEvent) {
        val imageUrl = commandEvent.getOption("url")?.asString?.trim()
        val prompt = commandEvent.getOption("prompt")?.asString

        if (imageUrl.isNullOrBlank() || prompt.isNullOrBlank()) {
            commandEvent.reply("Both url and prompt are required.").setEphemeral(true).queue()
            return
        }

        if (!isValidHttpUrl(imageUrl)) {
            commandEvent.reply("The provided URL is not valid. Please use http or https.").setEphemeral(true).queue()
            return
        }

        commandEvent.deferReply(true).queue()

        // Fetch bytes and mime
        val (bytes, mime) = try {
            fetchImageBytes(imageUrl)
        } catch (e: Exception) {
            commandEvent.hook.sendMessage("Failed to download image: ${e.message}").queue()
            return
        }

        if (bytes == null || bytes.isEmpty()) {
            commandEvent.hook.sendMessage("Downloaded image is empty or could not be read.").queue()
            return
        }

        val result = gemini.updateImageWithPrompt(bytes, mime, prompt)

        when (result) {
            is GoogleGeminiService.ImageResult.Error -> {
                commandEvent.hook.sendMessage("Error: ${result.message}").queue()
            }
            is GoogleGeminiService.ImageResult.ImageBytes -> {
                val upload = FileUpload.fromData(result.bytes, "ai-image-update.png")

                val spamChannel = commandEvent.jda.getTextChannelById(botSpamChannelId)
                if (spamChannel == null) {
                    commandEvent.hook.sendMessage("Bot spam channel not found.").queue()
                    return
                }

                val link = spamChannel.sendMessageEmbeds(
                    EmbedBuilder()
                        .addField("Prompt", prompt, false)
                        .addField("Source URL", imageUrl, false)
                        .addField("Requested by", commandEvent.user.asMention, false)
                        .setFooter("This AI slop is brought to you by Gemini")
                        .build()
                )
                    .addFiles(upload)
                    .complete().jumpUrl

                commandEvent.hook
                    .sendMessage("Sent transformed image to bot spam channel: $link")
                    .setEphemeral(true)
                    .queue()
            }
        }
    }

    private fun isValidHttpUrl(url: String): Boolean {
        return try {
            val uri = URI(url)
            uri.scheme == "http" || uri.scheme == "https"
        } catch (e: Exception) {
            false
        }
    }

    private fun fetchImageBytes(url: String, connectTimeoutMs: Int = 7000, readTimeoutMs: Int = 10000): Pair<ByteArray?, String?> {
        val uri = URI(url)
        val conn = (uri.toURL().openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            requestMethod = "GET"
        }
        return try {
            val type = conn.contentType
            val bytes = conn.inputStream.use { it.readAllBytes() }
            Pair(bytes, type)
        } finally {
            conn.disconnect()
        }
    }
}
