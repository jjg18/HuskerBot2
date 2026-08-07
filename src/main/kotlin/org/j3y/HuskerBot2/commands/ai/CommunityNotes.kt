package org.j3y.HuskerBot2.commands.ai

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import org.j3y.HuskerBot2.commands.SlashCommand
import org.j3y.HuskerBot2.service.GoogleGeminiService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.awt.Color
import java.time.OffsetDateTime
import kotlin.random.Random

@Component
class CommunityNotes(
    private val geminiService: GoogleGeminiService,
    private val notesService: org.j3y.HuskerBot2.service.CommunityNotesService
) : SlashCommand() {

    private val log = LoggerFactory.getLogger(CommunityNotes::class.java)

    override fun getCommandKey(): String = "community-notes"
    override fun getDescription(): String = "Analyze a user's recent message(s) and draft a Community Notes-style context"

    override fun getOptions(): List<OptionData> = listOf(
        OptionData(OptionType.USER, "user", "The user whose message(s) to analyze", true),
        OptionData(OptionType.INTEGER, "count", "How many of their most recent messages in this channel (1-10). Default 1", false)
            .setMinValue(1)
            .setMaxValue(10)
    )

    override fun execute(commandEvent: SlashCommandInteractionEvent) {
        commandEvent.deferReply(false).queue()
        try {
            val channel = try {
                commandEvent.channel.asGuildMessageChannel()
            } catch (e: Exception) {
                commandEvent.hook.sendMessage("This command can only be used in guild text channels.").queue()
                return
            }

            val selectedUser = commandEvent.getOption("user")?.asUser
            if (selectedUser == null) {
                commandEvent.hook.sendMessage("You must specify a user.").queue()
                return
            }

            val requested = (commandEvent.getOption("count")?.asLong ?: 1L).toInt()
            val count = requested.coerceIn(1, 10)

            val recentMessages = notesService.fetchRecentMessages(channel, 200)

            val userMessages = recentMessages
                .asSequence()
                .filter { it.author.idLong == selectedUser.idLong }
                .filter { notesService.shouldIncludeMessage(it) }
                .take(count)
                .toList()

            if (userMessages.isEmpty()) {
                commandEvent.hook.sendMessage("I couldn't find recent messages by ${selectedUser.asMention} in this channel.").queue()
                return
            }

            // userMessages is newest-first, so the last element is the oldest of the analyzed messages.
            // We want the 10 immediate messages (from any author) that come right before that one.
            val oldestAnalyzedId = userMessages.last().idLong
            val contextMessages = notesService.fetchMessagesBefore(channel, oldestAnalyzedId, 10)
            val contextBlock = notesService.buildContextBlock(contextMessages)

            val transcript = notesService.buildTranscript(userMessages, selectedUser)
            val prompt = notesService.buildPrompt(transcript, selectedUser, contextBlock)

            val response = geminiService.generateText(prompt)
            val cleaned = notesService.sanitizeForDiscord(response)

            val embed = notesService.buildEmbed(
                notesService.truncate(cleaned, 3900),
                commandEvent.member?.effectiveName ?: commandEvent.user.effectiveName,
                commandEvent.user.avatarUrl
            )

            // Also show a quoted preview of the analyzed messages (short)
            val quotePreview = notesService.buildQuotePreview(userMessages, selectedUser)

            commandEvent.hook.sendMessage(quotePreview).addEmbeds(embed).queue()
        } catch (e: Exception) {
            log.error("Error executing /community-notes", e)
            commandEvent.hook.sendMessage("Error while generating notes: ${e.message}").queue()
        }
    }
}
