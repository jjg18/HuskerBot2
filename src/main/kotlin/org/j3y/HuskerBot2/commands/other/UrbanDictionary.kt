package org.j3y.HuskerBot2.commands.other

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import org.j3y.HuskerBot2.commands.SlashCommand
import org.j3y.HuskerBot2.model.PaginatedUrbanResult
import org.j3y.HuskerBot2.service.PaginationService
import org.j3y.HuskerBot2.service.UrbanDictionaryService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.awt.Color
import java.time.Instant

@Component
class UrbanDictionary(
    private val urbanDictionaryService: UrbanDictionaryService,
    private val paginationService: PaginationService
) : SlashCommand() {
    
    private val logger = LoggerFactory.getLogger(UrbanDictionary::class.java)
    
    override fun getCommandKey(): String = "urban-dictionary"
    override fun getDescription(): String = "Look up a word or phrase on Urban Dictionary"
    override fun getOptions(): List<OptionData> = listOf(
        OptionData(OptionType.STRING, "word", "The word or phrase to look up", true)
    )
    
    override fun execute(event: SlashCommandInteractionEvent) {
        val word = event.getOption("word")?.asString
        if (word.isNullOrBlank()) {
            event.reply("Please provide a word to look up!").setEphemeral(true).queue()
            return
        }
        
        logger.info("Urban Dictionary search for term: $word by user: ${event.user.asTag}")
        
        event.deferReply().queue()
        
        try {
            val definitions = urbanDictionaryService.searchDefinitions(word.trim())
            
            if (definitions.isNullOrEmpty()) {
                logger.warn("No definitions found for term: $word")
                event.hook.sendMessage("No definitions found for \"$word\". Try a different spelling or term.").queue()
                return
            }
            
            // Create pagination
            val userId = event.user.id
            event.hook.sendMessage("Loading...").queue { response ->
                val messageId = response.id
                val pageData = paginationService.createPagination(userId, messageId, definitions, word)
                
                val embed = createUrbanEmbed(pageData)
                val buttons = paginationService.createPaginationButtons(definitions.size > 1)
                
                response.editOriginal()
                    .setEmbeds(embed)
                    .setActionRow(buttons)
                    .queue()
            }
            
        } catch (e: Exception) {
            logger.error("Error executing urban-dictionary command for word: $word", e)
            event.hook.sendMessage("Sorry, there was an error looking up that word. Please try again later.").queue()
        }
    }
    
    private fun createUrbanEmbed(pageData: PaginatedUrbanResult): MessageEmbed {
        val definition = pageData.definitions[pageData.currentPage]
        
        val embed = EmbedBuilder()
        
        embed.setTitle("📚 ${definition.word}")
        embed.setColor(Color.ORANGE)
        embed.setUrl(definition.permalink)
        
        // Definition
        embed.addField("Definition", definition.definition, false)
        
        // Example (if available and not empty)
        if (definition.example.isNotBlank()) {
            embed.addField("Example", definition.example, false)
        }
        
        // Stats
        embed.addField("👍", definition.thumbsUp.toString(), true)
        embed.addField("👎", definition.thumbsDown.toString(), true)
        embed.addField("Author", definition.author, true)
        
        // Page info
        if (pageData.totalPages > 1) {
            embed.setFooter("Page ${pageData.currentPage + 1} of ${pageData.totalPages}")
        }
        
        embed.setTimestamp(Instant.now())
        
        return embed.build()
    }
}