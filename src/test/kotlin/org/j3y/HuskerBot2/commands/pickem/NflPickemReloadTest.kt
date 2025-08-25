package org.j3y.HuskerBot2.commands.pickem

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import org.j3y.HuskerBot2.automation.pickem.nfl.NflPickemProcessing
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.Mockito.`when`

class NflPickemReloadTest {

    private lateinit var cmd: NflPickemReload
    private lateinit var processing: NflPickemProcessing

    @BeforeEach
    fun setup() {
        cmd = NflPickemReload()
        processing = Mockito.mock(NflPickemProcessing::class.java)
        cmd.nflPickemProcessing = processing
    }

    @Test
    fun `execute denies when member lacks permission`() {
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)
        val hook = Mockito.mock(InteractionHook::class.java)
        @Suppress("UNCHECKED_CAST")
        val messageAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>

        val member = Mockito.mock(Member::class.java)
        `when`(member.hasPermission(Permission.MESSAGE_MANAGE)).thenReturn(false)

        `when`(event.member).thenReturn(member)
        `when`(event.deferReply(true)).thenReturn(replyAction)
        `when`(event.hook).thenReturn(hook)
        `when`(hook.sendMessage(Mockito.anyString())).thenReturn(messageAction)

        cmd.execute(event)

        Mockito.verify(replyAction).queue()
        val captor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(hook).sendMessage(captor.capture())
        Mockito.verify(messageAction).queue()

        assert(captor.value == "You do not have permission to use this command.")
        Mockito.verify(processing, Mockito.never()).postWeeklyPickem()
    }

    @Test
    fun `execute calls processing and replies success when permitted`() {
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)
        val hook = Mockito.mock(InteractionHook::class.java)
        @Suppress("UNCHECKED_CAST")
        val messageAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>

        val member = Mockito.mock(Member::class.java)
        `when`(member.hasPermission(Permission.MESSAGE_MANAGE)).thenReturn(true)

        `when`(event.member).thenReturn(member)
        `when`(event.deferReply(true)).thenReturn(replyAction)
        `when`(event.hook).thenReturn(hook)
        `when`(hook.sendMessage(Mockito.anyString())).thenReturn(messageAction)

        cmd.execute(event)

        Mockito.verify(replyAction).queue()
        Mockito.verify(processing).postWeeklyPickem()

        val captor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(hook).sendMessage(captor.capture())
        assert(captor.value == "Reloaded the NFL Pick'em Listing.")
        Mockito.verify(messageAction).queue()
    }

    @Test
    fun `execute replies with error when processing throws`() {
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)
        val hook = Mockito.mock(InteractionHook::class.java)
        @Suppress("UNCHECKED_CAST")
        val messageAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>

        val member = Mockito.mock(Member::class.java)
        `when`(member.hasPermission(Permission.MESSAGE_MANAGE)).thenReturn(true)

        `when`(event.member).thenReturn(member)
        `when`(event.deferReply(true)).thenReturn(replyAction)
        `when`(event.hook).thenReturn(hook)
        `when`(hook.sendMessage(Mockito.anyString())).thenReturn(messageAction)

        `when`(processing.postWeeklyPickem()).thenThrow(RuntimeException("boom"))

        cmd.execute(event)

        Mockito.verify(replyAction).queue()
        val captor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(hook).sendMessage(captor.capture())
        val msg = captor.value
        assert(msg.contains("There was an issue reloading the NFL Pick'em Listing:"))
        assert(msg.contains("boom"))
        Mockito.verify(messageAction).queue()
    }
}
