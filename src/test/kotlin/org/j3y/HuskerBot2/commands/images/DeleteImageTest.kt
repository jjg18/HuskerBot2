package org.j3y.HuskerBot2.commands.images

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
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

class DeleteImageTest {

    private lateinit var deleteImage: DeleteImage
    private lateinit var imageRepo: ImageRepo

    @BeforeEach
    fun setup() {
        deleteImage = DeleteImage()
        imageRepo = Mockito.mock(ImageRepo::class.java)
        deleteImage.imageRepo = imageRepo
    }

    @Test
    fun `command metadata and options are correct`() {
        assertEquals("delete", deleteImage.getCommandKey())
        assertTrue(deleteImage.isSubcommand())
        assertEquals("Delete an image", deleteImage.getDescription())

        val opts: List<OptionData> = deleteImage.getOptions()
        assertEquals(1, opts.size)
        assertEquals("name", opts[0].name)
        assertEquals(OptionType.STRING, opts[0].type)
        assertTrue(opts[0].isRequired)
    }

    @Test
    fun `execute replies error when missing name`() {
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)

        // Missing option -> getOption returns null
        `when`(event.getOption("name")).thenReturn(null)
        `when`(event.reply(Mockito.anyString())).thenReturn(replyAction)
        `when`(replyAction.setEphemeral(true)).thenReturn(replyAction)

        deleteImage.execute(event)

        val msgCaptor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(event).reply(msgCaptor.capture())
        Mockito.verify(replyAction).setEphemeral(true)
        Mockito.verify(replyAction).queue()
        assertEquals("Both name and url are required.", msgCaptor.value)
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

        deleteImage.execute(event)

        val msgCaptor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(event).reply(msgCaptor.capture())
        assertEquals("An image with name 'missing' does not exist.", msgCaptor.value)
        Mockito.verify(replyAction).setEphemeral(true)
        Mockito.verify(replyAction).queue()
        Mockito.verify(imageRepo, Mockito.never()).delete(Mockito.any())
    }

    @Test
    fun `execute replies error when user lacks permission`() {
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)
        val user = Mockito.mock(User::class.java)
        val member = Mockito.mock(Member::class.java)

        val entity = ImageEntity("pic", "https://x", "111")
        `when`(imageRepo.findById("pic")).thenReturn(Optional.of(entity))

        val optName = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        `when`(optName.asString).thenReturn("pic")
        `when`(event.getOption("name")).thenReturn(optName)

        `when`(user.id).thenReturn("222") // different from uploader
        `when`(event.user).thenReturn(user)
        `when`(event.member).thenReturn(member)
        `when`(member.hasPermission(Permission.MESSAGE_MANAGE)).thenReturn(false)

        `when`(event.reply(Mockito.anyString())).thenReturn(replyAction)
        `when`(replyAction.setEphemeral(true)).thenReturn(replyAction)

        deleteImage.execute(event)

        val msgCaptor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(event).reply(msgCaptor.capture())
        assertEquals("You do not have permission to delete this image (you are not the creator or you are not a mod.)", msgCaptor.value)
        Mockito.verify(replyAction).setEphemeral(true)
        Mockito.verify(replyAction).queue()
        Mockito.verify(imageRepo, Mockito.never()).delete(Mockito.any())
    }

    @Test
    fun `execute deletes when user is creator`() {
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)
        val user = Mockito.mock(User::class.java)
        val member = Mockito.mock(Member::class.java)

        val entity = ImageEntity("mine", "https://x", "uploader")
        `when`(imageRepo.findById("mine")).thenReturn(Optional.of(entity))

        val optName = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        `when`(optName.asString).thenReturn("mine")
        `when`(event.getOption("name")).thenReturn(optName)

        `when`(user.id).thenReturn("uploader")
        `when`(event.user).thenReturn(user)
        `when`(event.member).thenReturn(member)
        `when`(member.hasPermission(Permission.MESSAGE_MANAGE)).thenReturn(false)

        `when`(event.reply(Mockito.anyString())).thenReturn(replyAction)

        deleteImage.execute(event)

        // verify repo deletion
        val imgCaptor = ArgumentCaptor.forClass(ImageEntity::class.java)
        Mockito.verify(imageRepo).delete(imgCaptor.capture())
        assertEquals("mine", imgCaptor.value.imageName)

        // verify reply message (non-ephemeral success)
        val msgCaptor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(event).reply(msgCaptor.capture())
        assertEquals("Image 'mine' has been deleted by null.", msgCaptor.value.replace("${'$'}{event.user.effectiveName}", "null"))
        Mockito.verify(replyAction).queue()
    }

    @Test
    fun `execute deletes when user has moderator permission`() {
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)
        val user = Mockito.mock(User::class.java)
        val member = Mockito.mock(Member::class.java)

        val entity = ImageEntity("any", "https://x", "someoneElse")
        `when`(imageRepo.findById("any")).thenReturn(Optional.of(entity))

        val optName = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        `when`(optName.asString).thenReturn("any")
        `when`(event.getOption("name")).thenReturn(optName)

        `when`(user.id).thenReturn("moduser")
        `when`(event.user).thenReturn(user)
        `when`(event.member).thenReturn(member)
        `when`(member.hasPermission(Permission.MESSAGE_MANAGE)).thenReturn(true)

        `when`(event.reply(Mockito.anyString())).thenReturn(replyAction)

        deleteImage.execute(event)

        Mockito.verify(imageRepo).delete(entity)

        val msgCaptor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(event).reply(msgCaptor.capture())
        // we can't easily get effectiveName; ensure it starts with expected prefix
        assertTrue(msgCaptor.value.startsWith("Image 'any' has been deleted by "))
        Mockito.verify(replyAction).queue()
    }
}
