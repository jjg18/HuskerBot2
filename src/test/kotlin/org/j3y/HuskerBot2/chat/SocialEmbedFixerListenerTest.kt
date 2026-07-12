package org.j3y.HuskerBot2.chat

import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import org.j3y.HuskerBot2.model.SocialEmbedReplacementEntity
import org.j3y.HuskerBot2.service.SocialEmbedReplacementService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.Mockito.`when`

class SocialEmbedFixerListenerTest {

    /** A fake service that applies the same default rules as the production seed, without a DB. */
    private fun fakeService(): SocialEmbedReplacementService {
        val repo = Mockito.mock(org.j3y.HuskerBot2.repository.SocialEmbedReplacementRepo::class.java)
        val defaults = listOf(
            SocialEmbedReplacementEntity(matchPattern = "fxtwitter.com/", replaceRegex = "", replaceWith = "", strategy = "SKIP", label = "fxtwitter skip", sortOrder = 1),
            SocialEmbedReplacementEntity(matchPattern = "vxtiktok.com/", replaceRegex = "", replaceWith = "", strategy = "SKIP", label = "vxtiktok skip", sortOrder = 2),
            SocialEmbedReplacementEntity(matchPattern = "tnktok.com/", replaceRegex = "", replaceWith = "", strategy = "SKIP", label = "tnktok skip", sortOrder = 3),
            SocialEmbedReplacementEntity(matchPattern = "kkinstagram.com/", replaceRegex = "", replaceWith = "", strategy = "SKIP", label = "kkinstagram skip", sortOrder = 4),
            SocialEmbedReplacementEntity(matchPattern = "vxinstagram.com/", replaceRegex = "", replaceWith = "", strategy = "SKIP", label = "vxinstagram skip", sortOrder = 5),
            SocialEmbedReplacementEntity(matchPattern = "embedez.seria.moe/", replaceRegex = "", replaceWith = "", strategy = "SKIP", label = "embedez skip", sortOrder = 6),
            SocialEmbedReplacementEntity(matchPattern = "vxreddit.com/", replaceRegex = "", replaceWith = "", strategy = "SKIP", label = "vxreddit skip", sortOrder = 7),
            SocialEmbedReplacementEntity(matchPattern = "fxbsky.app/", replaceRegex = "", replaceWith = "", strategy = "SKIP", label = "fxbsky skip", sortOrder = 8),
            SocialEmbedReplacementEntity(matchPattern = "twitter.com/", replaceRegex = "^(https?://)(?:www\\.)?(?:twitter|x)\\.com", replaceWith = "\$1fxtwitter.com", strategy = "REGEX", label = "Twitter/X -> fxtwitter", sortOrder = 10),
            SocialEmbedReplacementEntity(matchPattern = "x.com/", replaceRegex = "^(https?://)(?:www\\.)?(?:twitter|x)\\.com", replaceWith = "\$1fxtwitter.com", strategy = "REGEX", label = "X.com -> fxtwitter", sortOrder = 11),
            SocialEmbedReplacementEntity(matchPattern = "tiktok.com/", replaceRegex = "^(https?://)(?:www\\.)?tiktok\\.com", replaceWith = "\$1tnktok.com", strategy = "REGEX", label = "TikTok -> tnktok", sortOrder = 20),
            SocialEmbedReplacementEntity(matchPattern = "instagram.com/", replaceRegex = "^(https?://)(?:www\\.)?instagram\\.com", replaceWith = "\$1vxinstagram.com", strategy = "REGEX", label = "Instagram -> vxinstagram", sortOrder = 30),
            SocialEmbedReplacementEntity(matchPattern = "bsky.app/", replaceRegex = "^(https?://)(?:www\\.)?bsky\\.app", replaceWith = "\$1fxbsky.app", strategy = "REGEX", label = "Bluesky -> fxbsky", sortOrder = 40),
            SocialEmbedReplacementEntity(matchPattern = "facebook.com/share/r/", replaceRegex = "", replaceWith = "https://embedez.seria.moe/embed?url=", strategy = "WRAP", label = "Facebook reel -> embedez", sortOrder = 50),
            SocialEmbedReplacementEntity(matchPattern = "reddit.com/", replaceRegex = "^(https?://)(?:www\\.|old\\.|m\\.|np\\.)?reddit\\.com", replaceWith = "\$1vxreddit.com", strategy = "REGEX", label = "Reddit -> vxreddit", sortOrder = 60),
        )
        `when`(repo.count()).thenReturn(defaults.size.toLong())
        `when`(repo.findAllByOrderBySortOrderAsc()).thenReturn(defaults)
        return SocialEmbedReplacementService(repo)
    }

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
        `when`(event.author).thenReturn(user)
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
        val listener = SocialEmbedFixerListener(fakeService())
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
        val contentOnly = posted.substringAfter("posted:").trim()

        // Should contain two fxtwitter links, one per line, no duplicates
        val lines = contentOnly.split('\n').filter { it.isNotBlank() }
        assertEquals(2, lines.size)
        assertTrue(lines.any { it.startsWith("https://fxtwitter.com/") })
        assertTrue(lines.all { it.startsWith("https://fxtwitter.com/") })
    }

    @Test
    fun `converts instagram tiktok reddit variants correctly`() {
        val listener = SocialEmbedFixerListener(fakeService())
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
        val contentOnly = posted.substringAfter("posted:").trim()
        val lines = contentOnly.split('\n').filter { it.isNotBlank() }

        assertTrue(lines.any { it.startsWith("https://vxinstagram.com/") })
        assertTrue(lines.any { it.startsWith("https://tnktok.com/") })
        // all reddit variants should be converted to vxreddit.com
        assertTrue(lines.count { it.startsWith("https://vxreddit.com/") } >= 3)
    }

    @Test
    fun `converts facebook share r to embedez with encoded url`() {
        val listener = SocialEmbedFixerListener(fakeService())
        val source = "https://www.facebook.com/share/r/abc?mibextid=123"
        val (event, message, _) = basicEventWithMessage(source)

        listener.onMessageReceived(event)

        val captor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(message.channel).sendMessage(captor.capture())
        val posted = captor.value.trim()
        val contentOnly = posted.substringAfter("posted:").trim()
        assertTrue(contentOnly.startsWith("https://embedez.seria.moe/embed?url="))
        // ensure original is url-encoded within
        assertTrue(contentOnly.contains(java.net.URLEncoder.encode(source, "UTF-8")))
    }

    @Test
    fun `ignores when author is bot system or webhook or blank content`() {
        val listener = SocialEmbedFixerListener(fakeService())

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
        val listener = SocialEmbedFixerListener(fakeService())
        val alreadyGood = "Here are good ones https://fxtwitter.com/a/b https://vxtiktok.com/x/y https://kkinstagram.com/p/1 https://embedez.seria.moe/embed?url=foo https://vxreddit.com/r/a https://fxbsky.app/profile/handle/post/123"
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
    @Test
    fun `converts bluesky links to fxbsky`() {
        val listener = SocialEmbedFixerListener(fakeService())
        val input = "Check https://bsky.app/profile/handle.example/post/3k4l5m and https://www.bsky.app/profile/did:plc:abc123/post/xyz"
        val (event, message, _) = basicEventWithMessage(input)

        listener.onMessageReceived(event)

        val captor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(message.channel).sendMessage(captor.capture())
        val posted = captor.value.trim()
        val contentOnly = posted.substringAfter("posted:").trim()
        val lines = contentOnly.split('\n').filter { it.isNotBlank() }

        assertTrue(lines.all { it.startsWith("https://fxbsky.app/") })
    }
}
