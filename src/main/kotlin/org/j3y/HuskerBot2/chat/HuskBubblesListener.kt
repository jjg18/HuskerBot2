package org.j3y.HuskerBot2.chat

import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import kotlin.random.Random

@Component
class HuskBubblesListener(
    // Testability seam: allows deterministic tests by injecting probability supplier
    private val probabilitySupplier: () -> Double = { Random.nextDouble() }
) : ListenerAdapter() {
    private final val log = LoggerFactory.getLogger(HuskBubblesListener::class.java)

    // Target Discord user ID to watch for
    private final val targetUserId = 598039388148203520L

    // Emoji: Bubbles (🫧)
    private final val bubblesEmoji: Emoji = Emoji.fromUnicode("🫧")

    override fun onMessageReceived(event: MessageReceivedEvent) {
        try {
            val message = event.message
            val author = message.author

            // Ignore webhooks and bots entirely
            if (author.isBot || author.isSystem) return

            if (author.idLong != targetUserId) return

            // 5% chance to react
            if (probabilitySupplier.invoke() < 0.05) {
                message.addReaction(bubblesEmoji).queue(
                    { /* success - no op */ },
                    { ex -> log.warn("Failed to add bubbles reaction", ex) }
                )
            }
        } catch (e: Exception) {
            log.error("Error in HuskBubblesListener handling message", e)
        }
    }
}