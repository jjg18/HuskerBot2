package org.j3y.HuskerBot2.chat

import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.j3y.HuskerBot2.service.SocialEmbedReplacementService
import org.j3y.HuskerBot2.service.SocialEmbedReplacementService.ApplyResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class SocialEmbedFixerListener(
    private val socialEmbedReplacementService: SocialEmbedReplacementService,
) : ListenerAdapter() {
    private val log = LoggerFactory.getLogger(SocialEmbedFixerListener::class.java)

    // Regex to roughly match URLs (whitespace-terminated)
    private val urlRegex = Regex("\\bhttps?://\\S+", RegexOption.IGNORE_CASE)

    override fun onMessageReceived(event: MessageReceivedEvent) {
        val message = event.message
        val author = message.author

        // Ignore bots/system/webhooks and empty messages
        if (author.isBot || author.isSystem || message.isWebhookMessage) return
        val raw = message.contentRaw
        if (raw.isBlank()) return

        try {
            // Find all URLs in message
            val matches = urlRegex.findAll(raw).map { it.value }.toList()
            if (matches.isEmpty()) return

            // Apply DB-driven replacement rules to each URL
            val replacements = matches.mapNotNull { url ->
                when (val result = socialEmbedReplacementService.applyRules(url)) {
                    is ApplyResult.Replaced -> result.url
                    is ApplyResult.Skip, is ApplyResult.NoMatch -> null
                }
            }

            if (replacements.isEmpty()) return

            // Suppress embeds on the original message (best-effort)
            message.suppressEmbeds(true).queue({
                // success - no op
            }, { ex ->
                log.warn("Failed to suppress embeds on original message: ${ex.message}")
            })

            // Post rewritten links so Discord re-embeds them nicely
            val response = event.author.asMention + " posted: " + buildString {
                replacements.distinct().forEach { append(it).append('\n') }
            }

            message.channel.sendMessage(response.trim()).queue({ /* ok */ }, { ex ->
                log.warn("Failed to send re-embed message", ex)
            })
        } catch (e: Exception) {
            log.error("Error in SocialEmbedFixerListener", e)
        }
    }
}
