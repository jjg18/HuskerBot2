package org.j3y.HuskerBot2.chat

import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`

class HuskBubblesListenerTest {

    private fun eventWithAuthor(
        id: Long,
        isBot: Boolean = false,
        isSystem: Boolean = false
    ): Triple<MessageReceivedEvent, Message, User> {
        val event = Mockito.mock(MessageReceivedEvent::class.java)
        val message = Mockito.mock(Message::class.java, Mockito.RETURNS_DEEP_STUBS)
        val user = Mockito.mock(User::class.java)

        `when`(event.message).thenReturn(message)
        `when`(message.author).thenReturn(user)
        `when`(user.isBot).thenReturn(isBot)
        `when`(user.isSystem).thenReturn(isSystem)
        `when`(user.idLong).thenReturn(id)

        return Triple(event, message, user)
    }

    @Test
    fun `ignores bot authors`() {
        val (event, message, _) = eventWithAuthor(id = 598039388148203520L, isBot = true)
        val listener = HuskBubblesListener { 0.0 } // would trigger, but bot check should short-circuit
        listener.onMessageReceived(event)
        Mockito.verify(message, Mockito.never()).addReaction(Mockito.any(Emoji::class.java))
    }

    @Test
    fun `ignores system authors`() {
        val (event, message, _) = eventWithAuthor(id = 598039388148203520L, isSystem = true)
        val listener = HuskBubblesListener { 0.0 }
        listener.onMessageReceived(event)
        Mockito.verify(message, Mockito.never()).addReaction(Mockito.any(Emoji::class.java))
    }

    @Test
    fun `ignores messages from non-target user`() {
        val (event, message, _) = eventWithAuthor(id = 1234567890L)
        val listener = HuskBubblesListener { 0.0 }
        listener.onMessageReceived(event)
        Mockito.verify(message, Mockito.never()).addReaction(Mockito.any(Emoji::class.java))
    }

    @Test
    fun `does not react when probability is above threshold`() {
        val (event, message, _) = eventWithAuthor(id = 598039388148203520L)
        val listener = HuskBubblesListener { 0.50 } // 50% > 5%, so should not react
        listener.onMessageReceived(event)
        Mockito.verify(message, Mockito.never()).addReaction(Mockito.any(Emoji::class.java))
    }

    @Test
    fun `reacts with bubbles when probability is below threshold`() {
        val (event, message, _) = eventWithAuthor(id = 598039388148203520L)
        val listener = HuskBubblesListener { 0.01 } // 1% < 5%, should react
        listener.onMessageReceived(event)

        // Verify reaction with the bubbles emoji was attempted
        val expectedEmoji = Emoji.fromUnicode("🫧")
        Mockito.verify(message).addReaction(expectedEmoji)
    }
}
