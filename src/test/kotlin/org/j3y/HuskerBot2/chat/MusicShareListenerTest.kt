package org.j3y.HuskerBot2.chat

import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.MockedConstruction
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.springframework.web.client.RestTemplate

class MusicShareListenerTest {


    private fun buildOdesliResponse(
        title: String? = "Test Song",
        artist: String? = "Test Artist",
        album: String? = "Test Album",
        thumb: String? = "https://img/cover.jpg",
        platforms: Map<String, String> = mapOf(
            "spotify" to "https://open.spotify.com/track/abc",
            "appleMusic" to "https://music.apple.com/some",
            "youtubeMusic" to "https://music.youtube.com/watch?v=1",
            "youtube" to "https://youtube.com/watch?v=1"
        )
    ): Any {
        val pkg = "org.j3y.HuskerBot2.chat"
        val entityClass = Class.forName("$pkg.OdesliEntity")
        val platformLinkClass = Class.forName("$pkg.OdesliPlatformLink")
        val responseClass = Class.forName("$pkg.OdesliResponse")

        val entity = entityClass.getDeclaredConstructor(
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java
        ).newInstance(title, artist, thumb, album)

        val entitiesByUniqueId = mapOf("songId" to entity)

        val linksByPlatform: MutableMap<String, Any> = mutableMapOf()
        for ((k, v) in platforms) {
            val link = platformLinkClass.getDeclaredConstructor(String::class.java).newInstance(v)
            linksByPlatform[k] = link
        }

        return responseClass.getDeclaredConstructor(
            String::class.java,
            Map::class.java,
            Map::class.java
        ).newInstance("songId", entitiesByUniqueId, linksByPlatform)
    }

    private fun mockRestTemplateConstruction(returnObj: Any, sourceUrl: String): MockedConstruction<RestTemplate> {
        return Mockito.mockConstruction(RestTemplate::class.java) { mock, _ ->
            val encoded = java.net.URLEncoder.encode(sourceUrl, Charsets.UTF_8)
            val endpoint = "https://api.song.link/v1-alpha.1/links?url=$encoded"
            @Suppress("UNCHECKED_CAST")
            `when`(
                mock.getForObject(
                    endpoint,
                    Class.forName("org.j3y.HuskerBot2.chat.OdesliResponse") as Class<Any>
                )
            ).thenReturn(returnObj)
        }
    }

    private fun basicEventWithMessage(content: String, authorName: String = "Alice"): Triple<MessageReceivedEvent, Message, User> {
        val event = Mockito.mock(MessageReceivedEvent::class.java)
        val message = Mockito.mock(Message::class.java, Mockito.RETURNS_DEEP_STUBS)
        val user = Mockito.mock(User::class.java)

        `when`(event.message).thenReturn(message)
        `when`(message.author).thenReturn(user)
        `when`(user.isBot).thenReturn(false)
        `when`(user.isSystem).thenReturn(false)
        `when`(message.isWebhookMessage).thenReturn(false)
        `when`(user.effectiveName).thenReturn(authorName)
        `when`(message.contentRaw).thenReturn(content)

        // Deep-stubbed message will allow chained calls like suppressEmbeds(...).queue(...) and replyEmbeds(...).setComponents(...).queue(...)

        return Triple(event, message, user)
    }

    @Test
    fun `sends embed with multi-platform buttons for supported link`() {
        val odesli = buildOdesliResponse()
        val src = "https://open.spotify.com/track/123?si=xyz"
        mockRestTemplateConstruction(odesli, src).use {
            val listener = MusicShareListener()
            val (event, message, _) = basicEventWithMessage("check this https://open.spotify.com/track/123?si=xyz")

            listener.onMessageReceived(event)

            val embedCaptor = ArgumentCaptor.forClass(MessageEmbed::class.java)
            Mockito.verify(message).replyEmbeds(embedCaptor.capture())
            val embed = embedCaptor.value
            assertEquals("Test Song — Test Artist", embed.title)
            assertTrue(embed.description!!.contains("Album: Test Album"))
            assertTrue(embed.description!!.contains("Shared by Alice"))
            assertEquals("https://img/cover.jpg", embed.thumbnail?.url)

        }
    }

    @Test
    fun `ignores when only youtube links returned`() {
        val odesli = buildOdesliResponse(
            platforms = mapOf(
                "youtubeMusic" to "https://music.youtube.com/watch?v=1",
                "youtube" to "https://youtube.com/watch?v=1"
            )
        )
        val src = "https://music.apple.com/us/album/foo/123"
                mockRestTemplateConstruction(odesli, src).use {
            val listener = MusicShareListener()
            val (event, message, _) = basicEventWithMessage("https://music.apple.com/us/album/foo/123")

            listener.onMessageReceived(event)

            Mockito.verify(message, Mockito.never()).replyEmbeds(ArgumentMatchers.any(MessageEmbed::class.java))
        }
    }

    @Test
    fun `ignores messages without supported music URLs`() {
        // RestTemplate shouldn't be called; still mock construction to be safe
        Mockito.mockConstruction(RestTemplate::class.java).use {
            val listener = MusicShareListener()
            val (event, message, _) = basicEventWithMessage("hello https://example.com not music")

            listener.onMessageReceived(event)

            Mockito.verify(message, Mockito.never()).replyEmbeds(ArgumentMatchers.any(MessageEmbed::class.java))
        }
    }

    @Test
    fun `ignores empty or whitespace messages`() {
        Mockito.mockConstruction(RestTemplate::class.java).use {
            val listener = MusicShareListener()
            val (eventBlank, messageBlank, _) = basicEventWithMessage("   ")

            listener.onMessageReceived(eventBlank)

            Mockito.verify(messageBlank, Mockito.never()).replyEmbeds(ArgumentMatchers.any(MessageEmbed::class.java))
        }
    }
}
