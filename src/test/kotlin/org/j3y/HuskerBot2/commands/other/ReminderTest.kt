package org.j3y.HuskerBot2.commands.other

import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import org.j3y.HuskerBot2.scheduler.ReminderService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import java.time.Instant

class ReminderTest {

    @Test
    fun `metadata and options are correct`() {
        val svc = Mockito.mock(ReminderService::class.java)
        val cmd = Reminder(svc)
        assertEquals("reminder", cmd.getCommandKey())
        assertEquals("Create a reminder to post a message at a specified time", cmd.getDescription())

        val opts: List<OptionData> = cmd.getOptions()
        assertEquals(3, opts.size)
        assertEquals("message", opts[0].name)
        assertEquals(OptionType.STRING, opts[0].type)
        assertTrue(opts[0].isRequired)
        assertEquals("time", opts[1].name)
        assertEquals(OptionType.STRING, opts[1].type)
        assertTrue(opts[1].isRequired)
        assertEquals("channel", opts[2].name)
        assertEquals(OptionType.CHANNEL, opts[2].type)
        assertFalse(opts[2].isRequired)
    }

    @Test
    fun `execute replies when message or time missing`() {
        val svc = Mockito.mock(ReminderService::class.java)
        val cmd = Reminder(svc)
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)
        val hook = Mockito.mock(InteractionHook::class.java)
        @Suppress("UNCHECKED_CAST")
        val messageAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>

        `when`(event.deferReply()).thenReturn(replyAction)
        `when`(event.hook).thenReturn(hook)
        `when`(hook.sendMessage(Mockito.anyString())).thenReturn(messageAction)

        // message is missing
        `when`(event.getOption("message")).thenReturn(null)
        `when`(event.getOption("time")).thenReturn(null)

        cmd.execute(event)

        Mockito.verify(replyAction).queue()
        val msgCaptor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(hook).sendMessage(msgCaptor.capture())
        assertEquals("You must provide both message and time.", msgCaptor.value)
        Mockito.verify(messageAction).queue()
        Mockito.verifyNoInteractions(svc)
    }

    @Test
    fun `execute replies when time cannot be parsed`() {
        val svc = Mockito.mock(ReminderService::class.java)
        val cmd = Reminder(svc)
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)
        val hook = Mockito.mock(InteractionHook::class.java)
        @Suppress("UNCHECKED_CAST")
        val messageAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>

        val optMsg = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        val optTime = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)

        `when`(optMsg.asString).thenReturn("Hello")
        `when`(optTime.asString).thenReturn("nonsense-time")

        `when`(event.deferReply()).thenReturn(replyAction)
        `when`(event.hook).thenReturn(hook)
        `when`(event.getOption("message")).thenReturn(optMsg)
        `when`(event.getOption("time")).thenReturn(optTime)
        `when`(hook.sendMessage(Mockito.anyString())).thenReturn(messageAction)

        // channel resolution when no channel option: we need event.channel.asGuildMessageChannel to be callable.
        // However, since time parsing fails before channel resolution is used to schedule, execute accesses channel before parse.
        // In Reminder.kt, channel resolution happens before time parse, so we must satisfy it.
        // Provide a channel option to bypass asGuildMessageChannel access.
        val optChannel = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        val fakeChannel = Mockito.mock(net.dv8tion.jda.api.entities.channel.unions.GuildChannelUnion::class.java)
        `when`(optChannel.asChannel).thenReturn(fakeChannel)
        `when`(fakeChannel.idLong).thenReturn(123L)
        `when`(event.getOption("channel")).thenReturn(optChannel)

        cmd.execute(event)

        Mockito.verify(replyAction).queue()
        val msgCaptor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(hook).sendMessage(msgCaptor.capture())
        assertEquals("I couldn't parse the time. Use ISO-8601 like 2025-08-23T18:00:00Z or a duration like 24h, 16m.", msgCaptor.value)
        Mockito.verify(messageAction).queue()
        Mockito.verifyNoInteractions(svc)
    }

    @Test
    fun `execute replies when time is not in the future`() {
        val svc = Mockito.mock(ReminderService::class.java)
        val cmd = Reminder(svc)
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)
        val hook = Mockito.mock(InteractionHook::class.java)
        @Suppress("UNCHECKED_CAST")
        val messageAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>

        val optMsg = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        val optTime = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        val optChannel = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        val fakeChannel = Mockito.mock(net.dv8tion.jda.api.entities.channel.unions.GuildChannelUnion::class.java)
        
        `when`(optMsg.asString).thenReturn("Hello")
        // Provide an instant that is definitely in the past
        `when`(optTime.asString).thenReturn("1970-01-01T00:00:00Z")

        `when`(event.deferReply()).thenReturn(replyAction)
        `when`(event.hook).thenReturn(hook)
        `when`(event.getOption("message")).thenReturn(optMsg)
        `when`(event.getOption("time")).thenReturn(optTime)
        `when`(hook.sendMessage(Mockito.anyString())).thenReturn(messageAction)

        // Provide channel option to avoid needing event.channel
        `when`(optChannel.asChannel).thenReturn(fakeChannel)
        `when`(fakeChannel.idLong).thenReturn(456L)
        `when`(event.getOption("channel")).thenReturn(optChannel)

        cmd.execute(event)

        Mockito.verify(replyAction).queue()
        val msgCaptor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(hook).sendMessage(msgCaptor.capture())
        assertEquals("The time provided must be in the future.", msgCaptor.value)
        Mockito.verify(messageAction).queue()
        Mockito.verifyNoInteractions(svc)
    }

    @Test
    fun `happy path schedules reminder and replies with embed`() {
        val svc = Mockito.mock(ReminderService::class.java)
        val cmd = Reminder(svc)
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)
        val hook = Mockito.mock(InteractionHook::class.java)
        @Suppress("UNCHECKED_CAST")
        val messageAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>

        val optMsg = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        val optTime = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        val optChannel = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        val fakeChannel = Mockito.mock(net.dv8tion.jda.api.entities.channel.unions.GuildChannelUnion::class.java)
        
        `when`(optMsg.asString).thenReturn("Pay rent")
        // Use a duration to avoid dealing with absolute parsing and timezones
        `when`(optTime.asString).thenReturn("10s")
        `when`(optChannel.asChannel).thenReturn(fakeChannel)
        `when`(fakeChannel.idLong).thenReturn(789L)

        `when`(event.deferReply()).thenReturn(replyAction)
        `when`(event.hook).thenReturn(hook)
        `when`(event.getOption("message")).thenReturn(optMsg)
        `when`(event.getOption("time")).thenReturn(optTime)
        `when`(event.getOption("channel")).thenReturn(optChannel)

        val capturedEmbed = java.util.concurrent.atomic.AtomicReference<MessageEmbed>()
        `when`(hook.sendMessageEmbeds(Mockito.any(MessageEmbed::class.java))).thenAnswer { invocation ->
            capturedEmbed.set(invocation.getArgument(0))
            messageAction
        }

        val user = Mockito.mock(net.dv8tion.jda.api.entities.User::class.java)
        `when`(event.user).thenReturn(user)
        `when`(user.idLong).thenReturn(42L)

        val before = Instant.now()
        cmd.execute(event)
        val after = Instant.now()

        // Verify defer
        Mockito.verify(replyAction).queue()

        // Verify embed sent and its contents
        Mockito.verify(hook).sendMessageEmbeds(Mockito.any(MessageEmbed::class.java))
        Mockito.verify(messageAction).queue()
        val embed = capturedEmbed.get()
        assertNotNull(embed)
        assertEquals("⏰ Reminder Scheduled", embed.title)
        val fields = embed.fields
        assertEquals(3, fields.size)
        assertEquals("Message", fields[0].name)
        assertEquals("Pay rent", fields[0].value)
        assertEquals("Submitted by", fields[1].name)
        assertTrue(fields[1].value?.contains("<@") == true)
        assertEquals("Scheduled for", fields[2].name)
        assertNotNull(fields[2].value)
    }

    @Test
    fun `execute catches exception and replies error`() {
        val svc = Mockito.mock(ReminderService::class.java)
        val cmd = Reminder(svc)
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)
        val hook = Mockito.mock(InteractionHook::class.java)
        @Suppress("UNCHECKED_CAST")
        val messageAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>

        val optMsg = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        val optTime = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        val optChannel = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        val fakeChannel = Mockito.mock(net.dv8tion.jda.api.entities.channel.unions.GuildChannelUnion::class.java)
        
        `when`(optMsg.asString).thenReturn("Do thing")
        `when`(optTime.asString).thenReturn("10s")
        `when`(optChannel.asChannel).thenReturn(fakeChannel)
        `when`(fakeChannel.idLong).thenReturn(111L)

        `when`(event.deferReply()).thenReturn(replyAction)
        `when`(event.hook).thenReturn(hook)
        `when`(event.getOption("message")).thenReturn(optMsg)
        `when`(event.getOption("time")).thenReturn(optTime)
        `when`(event.getOption("channel")).thenReturn(optChannel)

        `when`(hook.sendMessage(Mockito.anyString())).thenReturn(messageAction)

        val user2 = Mockito.mock(net.dv8tion.jda.api.entities.User::class.java)
        `when`(event.user).thenReturn(user2)
        `when`(user2.idLong).thenReturn(99L)

        // Cause exception when sending the embed (post-scheduling)
        `when`(hook.sendMessageEmbeds(Mockito.any(MessageEmbed::class.java))).thenThrow(RuntimeException("boom"))

        cmd.execute(event)

        Mockito.verify(replyAction).queue()
        val msgCaptor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(hook).sendMessage(msgCaptor.capture())
        assertEquals("Error while creating reminder: boom", msgCaptor.value)
        Mockito.verify(messageAction).queue()
    }
}
