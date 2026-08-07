package org.j3y.HuskerBot2.commands.ai

import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent
import net.dv8tion.jda.api.interactions.commands.Command
import org.j3y.HuskerBot2.commands.ContextCommand
import org.j3y.HuskerBot2.service.GoogleGeminiService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class CommunityNotesContext(
    private val geminiService: GoogleGeminiService,
    private val notesService: org.j3y.HuskerBot2.service.CommunityNotesService
) : ContextCommand() {

    private val log = LoggerFactory.getLogger(CommunityNotesContext::class.java)

    override fun getCommandMenuText() = "Fact Check (Community Notes)"
    override fun getCommandType() = Command.Type.MESSAGE
    override fun getDescription(): String = "Analyze a user's recent message(s) and draft a Community Notes-style context"

    override fun execute(commandEvent: MessageContextInteractionEvent) {
        commandEvent.deferReply(false).queue()
        try {
            val channel = try {
                commandEvent.channel?.asGuildMessageChannel()
            } catch (e: Exception) {
                commandEvent.hook.sendMessage("This command can only be used in guild text channels.").queue()
                return
            }

            if (channel == null) { return }

            val selectedUser = commandEvent.target.author
            if (selectedUser == null) {
                commandEvent.hook.sendMessage("You must specify a user.").queue()
                return
            }


            val targetMessage = commandEvent.target
            val transcript = targetMessage.contentStripped

            // We want the 10 immediate messages (from any author) that come right before the target message.
            val contextMessages = notesService.fetchMessagesBefore(channel, targetMessage.idLong, 10)
            val contextBlock = notesService.buildContextBlock(contextMessages)

            val prompt = notesService.buildPrompt(transcript, selectedUser, contextBlock)

            val response = geminiService.generateText(prompt)
            val cleaned = notesService.sanitizeForDiscord(response)

            val embed = notesService.buildEmbed(
                notesService.truncate(cleaned, 3900),
                commandEvent.member?.effectiveName ?: commandEvent.user.effectiveName,
                commandEvent.user.avatarUrl
            )

            // Also show a quoted preview of the analyzed messages (short)
            val quotePreview = notesService.buildQuotePreview(transcript, selectedUser)

            commandEvent.hook.sendMessage(quotePreview).addEmbeds(embed).queue()
        } catch (e: Exception) {
            log.error("Error executing /community-notes", e)
            commandEvent.hook.sendMessage("Error while generating notes: ${e.message}").queue()
        }
    }
}
