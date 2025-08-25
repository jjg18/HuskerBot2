package org.j3y.HuskerBot2.chat

import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.Mockito.`when`

class SocialEmbedFixerListenerTest {

    private fun basicEventWithMessage(
        content: String,
        authorName: String = "Alice",
        isBot: Boolean = false,
        isSystem: Boolean = false,
        isWebhook: Boolean = false,
    ): Triple<MessageReceivedEvent, Message, User> {
        val event = Mockito.mock(MessageReceivedEvent::class.java)
        val message = Mockito.mock(Message::class.java, Mockito.RETURNS_DEEP_STUBS)
        val user = Mockito.mock(User::class.java)

        `when`(event.message).thenReturn(message)
        `when`(message.author).thenReturn(user)
        `when`(user.isBot).thenReturn(isBot)
        `when`(user.isSystem).thenReturn(isSystem)
        `when`(message.isWebhookMessage).thenReturn(isWebhook)
        `when`(user.effectiveName).thenReturn(authorName)
        `when`(message.contentRaw).thenReturn(content)

        // For sendMessage verification, ensure channel exists
        val channel = Mockito.mock(MessageChannelUnion::class.java, Mockito.RETURNS_DEEP_STUBS)
        `when`(message.channel).thenReturn(channel)

        return Triple(event, message, user)
    }

    @Test
    fun `converts twitter and x links to fxtwitter, dedupes, suppresses embeds, and posts replacements`() {
        val listener = SocialEmbedFixerListener()
        val input = "Check these https://twitter.com/user/status/123 and https://x.com/u/status/456 and https://fxtwitter.com/already/ok"
        val (event, message, _) = basicEventWithMessage(input)

        listener.onMessageReceived(event)

        // verify suppressEmbeds called
        Mockito.verify(message).suppressEmbeds(true)

        // capture sendMessage text
        val channel = message.channel
        val captor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(channel).sendMessage(captor.capture())
        val posted = captor.value.trim()

        // Should contain two fxtwitter links, one per line, no duplicates
        val lines = posted.split('\n')
        assertEquals(2, lines.size)
        assertTrue(lines[0].startsWith("https://fxtwitter.com/") || lines[1].startsWith("https://fxtwitter.com/"))
        assertTrue(lines.all { it.contains("/status/") || it.contains("/u/") || it.startsWith("https://fxtwitter.com/") })
    }

    @Test
    fun `converts instagram tiktok reddit variants correctly`() {
        val listener = SocialEmbedFixerListener()
        val input = listOf(
            "https://www.instagram.com/p/abc123",
            "https://tiktok.com/@user/video/987",
            "https://old.reddit.com/r/test/comments/xyz",
            "https://www.reddit.com/r/test/comments/xyz2",
            "https://m.reddit.com/r/test/comments/xyz3",
            "https://np.reddit.com/r/test/comments/xyz4",
        ).joinToString(" ")
        val (event, message, _) = basicEventWithMessage(input)

        listener.onMessageReceived(event)

        val captor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(message.channel).sendMessage(captor.capture())
        val posted = captor.value.trim()
        val lines = posted.split('\n')

        assertTrue(lines.any { it.startsWith("https://kkinstagram.com/") })
        assertTrue(lines.any { it.startsWith("https://vxtiktok.com/") })
        // all reddit variants should be converted to rxddit.com
        assertTrue(lines.count { it.startsWith("https://rxddit.com/") } >= 3)
    }

    @Test
    fun `converts facebook share r to embedez with encoded url`() {
        val listener = SocialEmbedFixerListener()
        val source = "https://www.facebook.com/share/r/abc?mibextid=123"
        val (event, message, _) = basicEventWithMessage(source)

        listener.onMessageReceived(event)

        val captor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(message.channel).sendMessage(captor.capture())
        val posted = captor.value.trim()
        assertTrue(posted.startsWith("https://embedez.seria.moe/embed?url="))
        // ensure original is url-encoded within
        assertTrue(posted.contains(java.net.URLEncoder.encode(source, "UTF-8")))
    }

    @Test
    fun `ignores when author is bot system or webhook or blank content`() {
        val listener = SocialEmbedFixerListener()

        // bot
        run {
            val (event, message, _) = basicEventWithMessage("https://twitter.com/user/status/1", isBot = true)
            listener.onMessageReceived(event)
            Mockito.verify(message, Mockito.never()).suppressEmbeds(true)
            Mockito.verify(message.channel, Mockito.never()).sendMessage(Mockito.anyString())
        }
        // system
        run {
            val (event, message, _) = basicEventWithMessage("https://twitter.com/user/status/1", isSystem = true)
            listener.onMessageReceived(event)
            Mockito.verify(message, Mockito.never()).suppressEmbeds(true)
            Mockito.verify(message.channel, Mockito.never()).sendMessage(Mockito.anyString())
        }
        // webhook
        run {
            val (event, message, _) = basicEventWithMessage("https://twitter.com/user/status/1", isWebhook = true)
            listener.onMessageReceived(event)
            Mockito.verify(message, Mockito.never()).suppressEmbeds(true)
            Mockito.verify(message.channel, Mockito.never()).sendMessage(Mockito.anyString())
        }
        // blank
        run {
            val (event, message, _) = basicEventWithMessage("   ")
            listener.onMessageReceived(event)
            Mockito.verify(message, Mockito.never()).suppressEmbeds(true)
            Mockito.verify(message.channel, Mockito.never()).sendMessage(Mockito.anyString())
        }
    }

    @Test
    fun `does nothing when urls already on target domains or no relevant urls`() {
        val listener = SocialEmbedFixerListener()
        val alreadyGood = "Here are good ones https://fxtwitter.com/a/b https://vxtiktok.com/x/y https://kkinstagram.com/p/1 https://embedez.seria.moe/embed?url=foo https://rxddit.com/r/a"
        val (event1, message1, _) = basicEventWithMessage(alreadyGood)
        listener.onMessageReceived(event1)
        Mockito.verify(message1, Mockito.never()).suppressEmbeds(true)
        Mockito.verify(message1.channel, Mockito.never()).sendMessage(Mockito.anyString())

        val noneRelevant = "hello https://example.com/foo bar"
        val (event2, message2, _) = basicEventWithMessage(noneRelevant)
        listener.onMessageReceived(event2)
        Mockito.verify(message2, Mockito.never()).suppressEmbeds(true)
        Mockito.verify(message2.channel, Mockito.never()).sendMessage(Mockito.anyString())
    }
}
