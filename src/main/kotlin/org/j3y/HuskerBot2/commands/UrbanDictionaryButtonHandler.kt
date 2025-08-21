package org.j3y.HuskerBot2.commands

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.interactions.components.ActionRow
import org.j3y.HuskerBot2.model.PaginatedUrbanResult
import org.j3y.HuskerBot2.service.PaginationService
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.awt.Color
import java.time.Instant

@Component
class UrbanDictionaryButtonHandler(
    private val paginationService: PaginationService
) {
    
    private val logger = LoggerFactory.getLogger(UrbanDictionaryButtonHandler::class.java)
    
    @EventListener
    fun onButtonClick(event: ButtonInteractionEvent) {
        if (!event.componentId.startsWith("urban_")) return
        
        val userId = event.user.id
        val messageId = event.messageId
        
        when (event.componentId) {
            "urban_prev" -> handlePreviousPage(event, userId, messageId)
            "urban_next" -> handleNextPage(event, userId, messageId)
            "urban_close" -> handleClose(event, userId, messageId)
        }
    }
    
    private fun handlePreviousPage(event: ButtonInteractionEvent, userId: String, messageId: String) {
        val pageData = paginationService.getPageData(userId, messageId) ?: return
        
        val newPage = if (pageData.currentPage > 0) pageData.currentPage - 1 else pageData.totalPages - 1
        val updatedPageData = paginationService.updatePage(userId, messageId, newPage) ?: return
        
        updateMessage(event, updatedPageData)
    }
    
    private fun handleNextPage(event: ButtonInteractionEvent, userId: String, messageId: String) {
        val pageData = paginationService.getPageData(userId, messageId) ?: return
        
        val newPage = if (pageData.currentPage < pageData.totalPages - 1) pageData.currentPage + 1 else 0
        val updatedPageData = paginationService.updatePage(userId, messageId, newPage) ?: return
        
        updateMessage(event, updatedPageData)
    }
    
    private fun handleClose(event: ButtonInteractionEvent, userId: String, messageId: String) {
        paginationService.removePagination(userId, messageId)
        
        event.editMessage()
            .setEmbeds(EmbedBuilder()
                .setTitle("📚 Urban Dictionary")
                .setDescription("Search closed.")
                .setColor(Color.GRAY)
                .build())
            .setActionRow(emptyList())
            .queue()
    }
    
    private fun updateMessage(event: ButtonInteractionEvent, pageData: PaginatedUrbanResult) {
        val embed = createUrbanEmbed(pageData)
        val buttons = paginationService.createPaginationButtons(pageData.totalPages > 1)
        
        event.editMessage()
            .setEmbeds(embed)
            .setActionRow(buttons)
            .queue()
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