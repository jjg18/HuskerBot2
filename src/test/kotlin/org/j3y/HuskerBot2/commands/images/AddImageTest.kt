package org.j3y.HuskerBot2.commands.images

import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import org.j3y.HuskerBot2.model.ImageEntity
import org.j3y.HuskerBot2.repository.ImageRepo
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import java.util.*

class AddImageTest {

    private lateinit var addImage: AddImage
    private lateinit var imageRepo: ImageRepo

    @BeforeEach
    fun setup() {
        addImage = AddImage()
        imageRepo = Mockito.mock(ImageRepo::class.java)
        addImage.imageRepo = imageRepo
    }

    @Test
    fun `command metadata and options are correct`() {
        assertEquals("add", addImage.getCommandKey())
        assertTrue(addImage.isSubcommand())
        assertEquals("Add an image", addImage.getDescription())

        val opts: List<OptionData> = addImage.getOptions()
        assertEquals(2, opts.size)
        assertEquals("name", opts[0].name)
        assertEquals(OptionType.STRING, opts[0].type)
        assertEquals("url", opts[1].name)
        assertEquals(OptionType.STRING, opts[1].type)
    }

    @Test
    fun `execute replies error when missing name or url`() {
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)
        val hook = Mockito.mock(InteractionHook::class.java)
        @Suppress("UNCHECKED_CAST")
        val messageAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>

        `when`(event.deferReply(true)).thenReturn(replyAction)
        `when`(replyAction.queue()).then { /* no-op */ }
        `when`(event.hook).thenReturn(hook)
        `when`(hook.sendMessage(Mockito.anyString())).thenReturn(messageAction)

        // Options missing -> getOption returns null
        `when`(event.getOption("name")).thenReturn(null)
        `when`(event.getOption("url")).thenReturn(null)

        addImage.execute(event)

        // verify deferred reply
        Mockito.verify(replyAction).queue()

        val msgCaptor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(hook).sendMessage(msgCaptor.capture())
        assertEquals("Both name and url are required.", msgCaptor.value)
        Mockito.verify(messageAction).queue()
        Mockito.verifyNoInteractions(imageRepo)
    }

    @Test
    fun `execute replies error for invalid url scheme`() {
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)
        val hook = Mockito.mock(InteractionHook::class.java)
        @Suppress("UNCHECKED_CAST")
        val messageAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>

        `when`(event.deferReply(true)).thenReturn(replyAction)
        `when`(event.hook).thenReturn(hook)
        `when`(hook.sendMessage(Mockito.anyString())).thenReturn(messageAction)

        // Provide options
        val optName = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        `when`(optName.asString).thenReturn("logo")
        val optUrl = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        `when`(optUrl.asString).thenReturn("ftp://example.com/file.png") // invalid scheme
        `when`(event.getOption("name")).thenReturn(optName)
        `when`(event.getOption("url")).thenReturn(optUrl)

        addImage.execute(event)

        Mockito.verify(replyAction).queue()
        val msgCaptor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(hook).sendMessage(msgCaptor.capture())
        assertEquals("The provided URL is not valid. Please use http or https.", msgCaptor.value)
        Mockito.verify(messageAction).queue()
        Mockito.verifyNoMoreInteractions(imageRepo)
    }

    @Test
    fun `execute replies error when url is not an image`() {
        // Spy to stub validateImageUrl
        val spy = Mockito.spy(addImage)

        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)
        val hook = Mockito.mock(InteractionHook::class.java)
        @Suppress("UNCHECKED_CAST")
        val messageAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>

        `when`(event.deferReply(true)).thenReturn(replyAction)
        `when`(event.hook).thenReturn(hook)
        `when`(hook.sendMessage(Mockito.anyString())).thenReturn(messageAction)

        val optName = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        val optUrl = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        `when`(optName.asString).thenReturn("logo2")
        `when`(optUrl.asString).thenReturn("https://example.com/not-image.txt")
        `when`(event.getOption("name")).thenReturn(optName)
        `when`(event.getOption("url")).thenReturn(optUrl)

        Mockito.doReturn(false).`when`(spy).validateImageUrl("https://example.com/not-image.txt")

        spy.execute(event)

        Mockito.verify(replyAction).queue()
        val msgCaptor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(hook).sendMessage(msgCaptor.capture())
        assertEquals("The provided URL does not appear to be a valid image.", msgCaptor.value)
        Mockito.verify(messageAction).queue()
        Mockito.verifyNoInteractions(imageRepo)
    }

    @Test
    fun `execute replies error when image name already exists`() {
        val spy = Mockito.spy(addImage)

        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)
        val hook = Mockito.mock(InteractionHook::class.java)
        @Suppress("UNCHECKED_CAST")
        val messageAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>

        `when`(event.deferReply(true)).thenReturn(replyAction)
        `when`(event.hook).thenReturn(hook)
        `when`(hook.sendMessage(Mockito.anyString())).thenReturn(messageAction)

        val optName = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        val optUrl = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        `when`(optName.asString).thenReturn("dupe")
        `when`(optUrl.asString).thenReturn("https://example.com/image.png")
        `when`(event.getOption("name")).thenReturn(optName)
        `when`(event.getOption("url")).thenReturn(optUrl)

        Mockito.doReturn(true).`when`(spy).validateImageUrl("https://example.com/image.png")

        // Repo has an existing entity for name
        `when`(imageRepo.findById("dupe")).thenReturn(Optional.of(ImageEntity("dupe", "https://x", "u")))

        spy.execute(event)

        Mockito.verify(replyAction).queue()
        val msgCaptor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(hook).sendMessage(msgCaptor.capture())
        assertEquals("An image with name 'dupe' already exists.", msgCaptor.value)
        Mockito.verify(messageAction).queue()
        Mockito.verify(imageRepo, Mockito.never()).save(Mockito.any())
    }

    @Test
    fun `execute saves image and replies success message`() {
        val spy = Mockito.spy(addImage)

        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)
        val hook = Mockito.mock(InteractionHook::class.java)
        @Suppress("UNCHECKED_CAST")
        val messageAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>

        `when`(event.deferReply(true)).thenReturn(replyAction)
        `when`(event.hook).thenReturn(hook)
        `when`(hook.sendMessage(Mockito.anyString())).thenReturn(messageAction)

        val optName = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        val optUrl = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        `when`(optName.asString).thenReturn("newlogo")
        val url = "https://example.com/img.jpg"
        `when`(optUrl.asString).thenReturn(url)
        `when`(event.getOption("name")).thenReturn(optName)
        `when`(event.getOption("url")).thenReturn(optUrl)

        val user = Mockito.mock(User::class.java)
        `when`(user.id).thenReturn("12345")
        `when`(event.user).thenReturn(user)

        Mockito.doReturn(true).`when`(spy).validateImageUrl(url)
        `when`(imageRepo.findById("newlogo")).thenReturn(Optional.empty())

        spy.execute(event)

        Mockito.verify(replyAction).queue()

        val captor = ArgumentCaptor.forClass(ImageEntity::class.java)
        Mockito.verify(imageRepo).save(captor.capture())
        val saved = captor.value
        assertEquals("newlogo", saved.imageName)
        assertEquals(url, saved.imageUrl)
        assertEquals("12345", saved.uploadingUser)

        val msgCaptor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(hook).sendMessage(msgCaptor.capture())
        assertEquals("Added image 'newlogo' from URL: $url", msgCaptor.value)
        Mockito.verify(messageAction).queue()
    }

    @Test
    fun `isValidHttpUrl validates basic http and https urls`() {
        assertTrue(addImage.isValidHttpUrl("http://example.com/a.png"))
        assertTrue(addImage.isValidHttpUrl("https://example.com/path?x=1#frag"))
        assertFalse(addImage.isValidHttpUrl("ftp://example.com/a.png"))
        assertFalse(addImage.isValidHttpUrl("not a url"))
        assertFalse(addImage.isValidHttpUrl("http://"))
        assertFalse(addImage.isValidHttpUrl(""))
        assertFalse(addImage.isValidHttpUrl("   "))
    }

    @Test
    fun `validateImageUrl returns true when content type check passes and false otherwise`() {
        val spy = Mockito.spy(addImage)
        val good = "https://example.com/pic.png"
        val bad = "https://example.com/file.txt"

        // Good: isValidHttpUrl -> true (built-in), isImageUrlByContentType -> true
        Mockito.doReturn(true).`when`(spy).isImageUrlByContentType(good, 5000, 5000)
        assertTrue(spy.validateImageUrl(good))

        // Bad: isValidHttpUrl true but content-type check says false -> validate false
        Mockito.doReturn(false).`when`(spy).isImageUrlByContentType(bad, 5000, 5000)
        assertFalse(spy.validateImageUrl(bad))

        // Also, if the url isn't valid http/https, validate should be false regardless
        assertFalse(spy.validateImageUrl("ftp://x/y.png"))
    }
}
