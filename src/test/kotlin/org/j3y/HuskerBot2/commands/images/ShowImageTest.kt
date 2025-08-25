package org.j3y.HuskerBot2.commands.images

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.requests.restaction.CacheRestAction
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
import java.util.function.Consumer

class ShowImageTest {

    private lateinit var showImage: ShowImage
    private lateinit var imageRepo: ImageRepo

    @BeforeEach
    fun setup() {
        showImage = ShowImage()
        imageRepo = Mockito.mock(ImageRepo::class.java)
        showImage.imageRepo = imageRepo
    }

    @Test
    fun `command metadata and options are correct`() {
        assertEquals("show", showImage.getCommandKey())
        assertTrue(showImage.isSubcommand())
        assertEquals("Show an image", showImage.getDescription())

        val opts: List<OptionData> = showImage.getOptions()
        assertEquals(1, opts.size)
        assertEquals("name", opts[0].name)
        assertEquals(OptionType.STRING, opts[0].type)
        assertTrue(opts[0].isRequired)
    }

    @Test
    fun `execute replies error when name is missing`() {
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)

        `when`(event.getOption("name")).thenReturn(null) // missing
        `when`(event.reply(Mockito.anyString())).thenReturn(replyAction)
        `when`(replyAction.setEphemeral(true)).thenReturn(replyAction)

        showImage.execute(event)

        val msgCaptor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(event).reply(msgCaptor.capture())
        assertEquals("Image name is required.", msgCaptor.value)
        Mockito.verify(replyAction).setEphemeral(true)
        Mockito.verify(replyAction).queue()
        Mockito.verifyNoInteractions(imageRepo)
    }

    @Test
    fun `execute replies error when image not found`() {
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)

        val optName = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        `when`(optName.asString).thenReturn("missing")
        `when`(event.getOption("name")).thenReturn(optName)

        `when`(imageRepo.findById("missing")).thenReturn(Optional.empty())

        `when`(event.reply(Mockito.anyString())).thenReturn(replyAction)
        `when`(replyAction.setEphemeral(true)).thenReturn(replyAction)

        showImage.execute(event)

        val msgCaptor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(event).reply(msgCaptor.capture())
        assertEquals("An image with name 'missing' was not found.", msgCaptor.value)
        Mockito.verify(replyAction).setEphemeral(true)
        Mockito.verify(replyAction).queue()
    }

    @Test
    fun `execute builds and sends embed when image found`() {
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)
        val embedCaptor = ArgumentCaptor.forClass(MessageEmbed::class.java)

        val optName = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        `when`(optName.asString).thenReturn("pic1")
        `when`(event.getOption("name")).thenReturn(optName)

        val entity = ImageEntity("pic1", "https://example.com/p1.png", "uploader123")
        `when`(imageRepo.findById("pic1")).thenReturn(Optional.of(entity))

        // Mock JDA and RestAction for retrieveUserById
        val jda = Mockito.mock(JDA::class.java)
        val restAction = Mockito.mock(CacheRestAction::class.java) as CacheRestAction<User>
        val user = Mockito.mock(User::class.java)
        `when`(user.effectiveName).thenReturn("UploaderGuy")

        `when`(event.jda).thenReturn(jda)
        `when`(jda.retrieveUserById("uploader123")).thenReturn(restAction)

        // Stub queue(success) to immediately invoke the success Consumer with our user
        Mockito.doAnswer { invocation ->
            val success = invocation.arguments[0] as Consumer<User>
            success.accept(user)
            null
        }.`when`(restAction).queue(Mockito.any())

        `when`(event.replyEmbeds(embedCaptor.capture())).thenReturn(replyAction)

        showImage.execute(event)

        // Verify embed & reply
        val embed = embedCaptor.value
        assertNotNull(embed)
        assertEquals("pic1", embed.title)
        assertEquals("https://example.com/p1.png", embed.image?.url)
        assertEquals("Uploaded by UploaderGuy", embed.footer?.text)
        Mockito.verify(replyAction).queue()
    }
}
