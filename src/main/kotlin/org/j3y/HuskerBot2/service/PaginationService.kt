package org.j3y.HuskerBot2.service

import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.interactions.components.buttons.Button
import org.j3y.HuskerBot2.model.PaginatedUrbanResult
import org.j3y.HuskerBot2.model.UrbanDefinition
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class PaginationService {
    
    private val activePages = ConcurrentHashMap<String, PaginatedUrbanResult>()
    
    companion object {
        private const val PAGE_TIMEOUT_MINUTES = 15L
        private const val PREV_EMOJI = "⬅️"
        private const val NEXT_EMOJI = "➡️"
        private const val CLOSE_EMOJI = "❌"
    }
    
    fun createPagination(
        userId: String, 
        messageId: String, 
        definitions: List<UrbanDefinition>, 
        searchTerm: String
    ): PaginatedUrbanResult {
        val pageData = PaginatedUrbanResult(
            definitions = definitions,
            currentPage = 0,
            totalPages = definitions.size,
            searchTerm = searchTerm
        )
        
        activePages[getPaginationKey(userId, messageId)] = pageData
        return pageData
    }
    
    fun getPageData(userId: String, messageId: String): PaginatedUrbanResult? {
        return activePages[getPaginationKey(userId, messageId)]
    }
    
    fun updatePage(userId: String, messageId: String, newPage: Int): PaginatedUrbanResult? {
        val key = getPaginationKey(userId, messageId)
        val currentData = activePages[key] ?: return null
        
        val updatedData = currentData.copy(currentPage = newPage)
        activePages[key] = updatedData
        return updatedData
    }
    
    fun removePagination(userId: String, messageId: String) {
        activePages.remove(getPaginationKey(userId, messageId))
    }
    
    fun createPaginationButtons(hasMultiplePages: Boolean): List<Button> {
        return if (hasMultiplePages) {
            listOf(
                Button.secondary("urban_prev", Emoji.fromUnicode(PREV_EMOJI)),
                Button.secondary("urban_next", Emoji.fromUnicode(NEXT_EMOJI)),
                Button.danger("urban_close", Emoji.fromUnicode(CLOSE_EMOJI))
            )
        } else {
            listOf(Button.danger("urban_close", Emoji.fromUnicode(CLOSE_EMOJI)))
        }
    }
    
    private fun getPaginationKey(userId: String, messageId: String): String = "$userId:$messageId"
    
    @Scheduled(fixedRate = 300000) // Run every 5 minutes
    fun cleanupExpiredPages() {
        // For simplicity, we'll clear all pages during cleanup
        // In a production system, you'd want to track timestamps and only remove expired entries
        activePages.clear()
    }
}