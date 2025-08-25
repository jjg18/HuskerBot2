package org.j3y.HuskerBot2.commands.images

import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import org.j3y.HuskerBot2.model.ImageEntity
import org.j3y.HuskerBot2.repository.ImageRepo
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.Mockito.`when`

class ListImagesTest {

    private lateinit var listImages: ListImages
    private lateinit var imageRepo: ImageRepo

    @BeforeEach
    fun setup() {
        listImages = ListImages()
        imageRepo = Mockito.mock(ImageRepo::class.java)
        listImages.imageRepo = imageRepo
    }

    @Test
    fun `command metadata is correct`() {
        assertEquals("list", listImages.getCommandKey())
        assertTrue(listImages.isSubcommand())
        assertEquals("List all available images", listImages.getDescription())
    }

    @Test
    fun `execute sends embed with comma separated image names`() {
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)

        // Repo returns three images (order preserved)
        `when`(imageRepo.findAll()).thenReturn(listOf(
            ImageEntity("alpha", "https://x/a.png", "u1"),
            ImageEntity("beta", "https://x/b.png", "u2"),
            ImageEntity("gamma", "https://x/c.png", "u3")
        ))

        val embedCaptor = ArgumentCaptor.forClass(MessageEmbed::class.java)
        `when`(event.replyEmbeds(embedCaptor.capture())).thenReturn(replyAction)

        listImages.execute(event)

        // Verify repository was queried
        Mockito.verify(imageRepo).findAll()
        // Verify reply was queued
        Mockito.verify(replyAction).queue()

        val embed = embedCaptor.value
        assertNotNull(embed)
        assertEquals("Available Images", embed.title)
        assertEquals("alpha, beta, gamma", embed.description)
    }

    @Test
    fun `execute handles empty repository by sending embed with empty description`() {
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)

        `when`(imageRepo.findAll()).thenReturn(emptyList())

        val embedCaptor = ArgumentCaptor.forClass(MessageEmbed::class.java)
        `when`(event.replyEmbeds(embedCaptor.capture())).thenReturn(replyAction)

        listImages.execute(event)

        Mockito.verify(imageRepo).findAll()
        Mockito.verify(replyAction).queue()

        val embed = embedCaptor.value
        assertNotNull(embed)
        assertEquals("Available Images", embed.title)
        // joinToString on empty list yields empty string, JDA may set description to null -> accept null or empty
        assertTrue(embed.description.isNullOrEmpty())
    }
}
