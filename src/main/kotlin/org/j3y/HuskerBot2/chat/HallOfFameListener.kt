package org.j3y.HuskerBot2.chat

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.awt.Color
import java.time.Instant

@Component
class HallOfFameListener(
    @Value("\${discord.channels.hall-of-fame}") private val hallOfFameChannelId: String,
    @Value("\${discord.channels.hall-of-shame}") private val hallOfShameChannelId: String,
    @Value("\${discord.channels.hall-of-fame-ignored:}") private val ignoredChannelId: String
) : ListenerAdapter() {
    
    private final val log = LoggerFactory.getLogger(HallOfFameListener::class.java)
    
    private final val slowpokeEmoji = "slowpoke"
    private final val embedEmoji = "embed"
    private final val reactionThreshold = 10
    
    private val processedMessages = mutableSetOf<String>()

    override fun onMessageReactionAdd(event: MessageReactionAddEvent) {
        try {
            // Skip if bot reaction
            if (event.user?.isBot == true) return
            
            // Skip if message is in ignored channel
            if (ignoredChannelId.isNotEmpty() && event.channel.id == ignoredChannelId) return
            
            val messageId = event.messageId
            val message = event.retrieveMessage().complete()
            
            // Skip if already processed
            if (processedMessages.contains(messageId)) return
            
            val reactions = message.reactions
            
            // Check for hall of shame (slowpoke or embed emoji with 10+ reactions)
            val shameReaction = reactions.find { reaction ->
                (reaction.emoji.name.equals(slowpokeEmoji, ignoreCase = true) ||
                reaction.emoji.name.equals(embedEmoji, ignoreCase = true)) &&
                reaction.count >= reactionThreshold
            }
            
            if (shameReaction != null) {
                forwardToHallOfShame(message)
                processedMessages.add(messageId)
                return
            }
            
            // Check for hall of fame (any emoji with 10+ reactions, excluding slowpoke)
            val eligibleReaction = reactions.find { reaction ->
                reaction.count >= reactionThreshold && 
                !reaction.emoji.name.equals(slowpokeEmoji, ignoreCase = true) &&
                !reaction.emoji.name.equals(embedEmoji, ignoreCase = true)
            }
            
            if (eligibleReaction != null) {
                forwardToHallOfFame(message, eligibleReaction.emoji)
                processedMessages.add(messageId)
            }
            
        } catch (e: Exception) {
            log.error("Error in HallOfFameListener handling reaction", e)
        }
    }
    
    private fun forwardToHallOfFame(message: net.dv8tion.jda.api.entities.Message, emoji: Emoji) {
        val guild = message.guild
        val hallOfFameChannel = guild.getTextChannelById(hallOfFameChannelId)
        
        if (hallOfFameChannel == null) {
            log.warn("Hall of Fame channel not found: $hallOfFameChannelId")
            return
        }
        
        val embed = EmbedBuilder()
            .setColor(Color.ORANGE)
            .setTitle("🏆 Hall of Fame")
            .setDescription("A message has reached the hall of fame!")
            .addField("Author", message.author.asMention, true)
            .addField("Channel", message.channel.asMention, true)
            .addField("Reaction", emoji.formatted, true)
            .addField("Message", message.contentDisplay.take(1000), false)
            .addField("Jump to Message", "[Click here](${message.jumpUrl})", false)
            .setTimestamp(Instant.now())
            .setFooter("Hall of Fame", guild.iconUrl)
            .build()
        
        hallOfFameChannel.sendMessageEmbeds(embed).queue(
            { _: net.dv8tion.jda.api.entities.Message -> log.info("Message forwarded to Hall of Fame: ${message.jumpUrl}") },
            { ex: Throwable -> log.error("Failed to forward message to Hall of Fame", ex) }
        )
    }
    
    private fun forwardToHallOfShame(message: net.dv8tion.jda.api.entities.Message) {
        val guild = message.guild
        val hallOfShameChannel = guild.getTextChannelById(hallOfShameChannelId)
        
        if (hallOfShameChannel == null) {
            log.warn("Hall of Shame channel not found: $hallOfShameChannelId")
            return
        }
        
        val embed = EmbedBuilder()
            .setColor(Color.RED)
            .setTitle("🐌 Hall of Shame")
            .setDescription("A message has been deemed too slow for this world!")
            .addField("Author", message.author.asMention, true)
            .addField("Channel", message.channel.asMention, true)
            .addField("Reaction", ":slowpoke:", true)
            .addField("Message", message.contentDisplay.take(1000), false)
            .addField("Jump to Message", "[Click here](${message.jumpUrl})", false)
            .setTimestamp(Instant.now())
            .setFooter("Hall of Shame", guild.iconUrl)
            .build()
        
        hallOfShameChannel.sendMessageEmbeds(embed).queue(
            { _: net.dv8tion.jda.api.entities.Message -> log.info("Message forwarded to Hall of Shame: ${message.jumpUrl}") },
            { ex: Throwable -> log.error("Failed to forward message to Hall of Shame", ex) }
        )
    }
}