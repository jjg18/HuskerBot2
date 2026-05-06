package org.j3y.HuskerBot2.service

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.awt.Color
import java.time.OffsetDateTime
import kotlin.random.Random

@Service
class CommunityNotesService {

    private val log = LoggerFactory.getLogger(CommunityNotesService::class.java)

    fun fetchRecentMessages(channel: GuildMessageChannel, limit: Int): List<Message> {
        return try {
            channel.iterableHistory
                .cache(false)
                .takeAsync(limit.coerceAtMost(200))
                .get()
        } catch (e: Exception) {
            // Fallback to retrievePast
            channel.history.retrievePast(limit.coerceAtMost(100)).complete()
        }
    }

    fun shouldIncludeMessage(message: Message): Boolean {
        if (message.author.isBot) return false
        if (message.isWebhookMessage) return false
        val content = message.contentStripped.trim()
        if (content.isBlank()) return false
        if (content.startsWith("/")) return false
        return true
    }

    fun buildTranscript(messagesNewestFirst: List<Message>, user: User): String {
        // Messages come newest-first; reverse for chronological order
        return messagesNewestFirst.asReversed().joinToString("\n\n") { m ->
            val clean = m.contentStripped
                .replace(Regex("https?://\\S+"), "[link]")
                .replace("@everyone", "@\u200Beveryone")
                .replace("@here", "@\u200Bhere")
            "${user.name}: ${clean}"
        }
    }

    fun buildQuotePreview(messagesNewestFirst: List<Message>, user: User): String {
        val lines = messagesNewestFirst.asReversed().map { m ->
            val clean = m.contentStripped
                .replace("`", "\u200B`") // avoid code block breaks
                .take(150)
            "> ${clean}"
        }
        return (listOf("${user.asMention} said:") + lines).joinToString("\n")
    }

    fun buildQuotePreview(transcript: String, user: User): String {
        return "${user.asMention} said:" + "\n> $transcript"
    }

    fun buildPrompt(transcript: String, user: User): String {
        var takeShot = ""
        if (Random.nextDouble() < 0.33) {
            log.info("TAKING A SHOT!")
            takeShot = "\n            - You hate the Iowa Hawkeyes and the Colorado Buffaloes.\n            - Find a way to relevantly take a shot at the Colorado Buffaloes or Iowa Hawkeyes being awful if you can.\n"
        }

        return """
            Act as a Community Notes contributor.
            Analyze the following message(s) written by ${user.name} on Discord and draft a single Community Notes–style note that:
            - Briefly explains what is inaccurate, missing context, or potentially misleading.
            - Uses a neutral, non-accusatory tone focused on facts and context. Be a little bit blunt, savage, and funny, but not too much.
            - Provides concrete corrections and important context.
            - When relevant, cite reputable sources inline (domain names or titles) that a reader could check (e.g., CDC, WHO, reputable news, primary data). If no solid sources apply, state the uncertainty and what would be needed to verify.
            - Keep it concise (2–6 sentences maximum).
            - If the content appears accurate and you cannot justify a corrective note, say: "No corrective note warranted based on the provided text.". Only do this as a last resort, make a best effort to correct the user. $takeShot

            Messages by ${user.name} (chronological):
            ---
            $transcript
            ---
        """.trimIndent()
    }

    fun sanitizeForDiscord(text: String): String {
        return text
            .replace("@everyone", "@\u200Beveryone")
            .replace("@here", "@\u200Bhere")
            .ifBlank { "(no content)" }
    }

    fun truncate(text: String, maxLen: Int): String {
        return if (text.length <= maxLen) text else text.substring(0, maxLen - 3) + "..."
    }

    fun buildEmbed(description: String, requesterName: String, requesterAvatarUrl: String?): net.dv8tion.jda.api.entities.MessageEmbed {
        return EmbedBuilder()
            .setTitle("Community Notes – Context & Corrections")
            .setColor(Color(0xF5, 0xC2, 0x42))
            .setDescription(description)
            .setFooter("Requested by $requesterName", requesterAvatarUrl)
            .setThumbnail("https://cdn.discordapp.com/emojis/1292496875332567180.webp")
            .setTimestamp(OffsetDateTime.now())
            .build()
    }
}
