package org.j3y.HuskerBot2.commands.other

import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import org.j3y.HuskerBot2.commands.ai.Summarize
import org.j3y.HuskerBot2.service.GoogleGeminiService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.Mockito.`when`

class SummarizeTest {

    @Test
    fun `metadata and options are correct`() {
        val svc = Mockito.mock(GoogleGeminiService::class.java)
        val cmd = Summarize(svc, "1234567890")
        assertEquals("summarize", cmd.getCommandKey())
        assertEquals("Summarize the last N posts in this channel using Gemini", cmd.getDescription())
        val opts: List<OptionData> = cmd.getOptions()
        assertEquals(1, opts.size)
        assertEquals("count", opts[0].name)
        assertEquals(OptionType.INTEGER, opts[0].type)
        assertFalse(opts[0].isRequired)
    }

    @Test
    fun `execute replies when not in guild channel`() {
        val svc = Mockito.mock(GoogleGeminiService::class.java)
        val cmd = Summarize(svc, "1234567890")
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)
        val hook = Mockito.mock(InteractionHook::class.java)
        @Suppress("UNCHECKED_CAST")
        val messageAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>

        val union = Mockito.mock(MessageChannelUnion::class.java)

        `when`(event.deferReply(true)).thenReturn(replyAction)
        `when`(event.hook).thenReturn(hook)
        `when`(event.channel).thenReturn(union)
        `when`(hook.sendMessage(Mockito.anyString())).thenReturn(messageAction)

        // Make asGuildMessageChannel() throw to simulate non-guild context
        `when`(union.asGuildMessageChannel()).thenThrow(IllegalStateException("no guild"))

        cmd.execute(event)

        Mockito.verify(replyAction).queue()
        val msgCaptor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(hook).sendMessage(msgCaptor.capture())
        assertEquals("This command can only be used in guild text channels.", msgCaptor.value)
        Mockito.verify(messageAction).queue()
    }

    @Test
    fun `execute replies when no recent user messages`() {
        val svc = Mockito.mock(GoogleGeminiService::class.java)
        val cmd = Summarize(svc, "1234567890")
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)
        val hook = Mockito.mock(InteractionHook::class.java)
        @Suppress("UNCHECKED_CAST")
        val messageAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>

        val union = Mockito.mock(MessageChannelUnion::class.java)
        val channel = Mockito.mock(GuildMessageChannel::class.java)
        val history = Mockito.mock(net.dv8tion.jda.api.entities.MessageHistory::class.java)
        @Suppress("UNCHECKED_CAST")
        val rest = Mockito.mock(RestAction::class.java) as RestAction<List<Message>>

        `when`(event.deferReply(true)).thenReturn(replyAction)
        `when`(event.hook).thenReturn(hook)
        `when`(event.channel).thenReturn(union)
        `when`(union.asGuildMessageChannel()).thenReturn(channel)
        `when`(channel.name).thenReturn("general")
        // Force fallback to history by making iterableHistory fail
        `when`(channel.iterableHistory).thenThrow(RuntimeException("no iterable"))
        `when`(channel.history).thenReturn(history)
        `when`(history.retrievePast(Mockito.anyInt())).thenReturn(rest)
        `when`(rest.complete()).thenReturn(emptyList())
        `when`(hook.sendMessage(Mockito.anyString())).thenReturn(messageAction)

        cmd.execute(event)

        val msgCaptor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(hook).sendMessage(msgCaptor.capture())
        assertEquals("I couldn't find any recent user messages to summarize.", msgCaptor.value)
        Mockito.verify(messageAction).queue()
    }

    @Test
    fun `execute happy path builds embed with sanitized response and transcript`() {
        val svc = Mockito.mock(GoogleGeminiService::class.java)
        val cmd = Summarize(svc, "1234567890")
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)
        val hook = Mockito.mock(InteractionHook::class.java)
        @Suppress("UNCHECKED_CAST")
        val messageAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>

        val union = Mockito.mock(MessageChannelUnion::class.java)
        val channel = Mockito.mock(GuildMessageChannel::class.java)
        val history = Mockito.mock(net.dv8tion.jda.api.entities.MessageHistory::class.java)
        @Suppress("UNCHECKED_CAST")
        val rest = Mockito.mock(RestAction::class.java) as RestAction<List<Message>>

        // Build messages: newest first in history; method reverses to chronological
        val m1 = mockMessage("alice", false, "Hello check https://example.com")
        val m2 = mockMessage("botty", true, "I am a bot, ignore") // should be filtered out
        val m3 = mockMessage("bob", false, "/command should be filtered") // filtered
        val m4 = mockMessage("charlie", false, "Hi @everyone and @here")
        val fetched = listOf(m4, m3, m2, m1) // newest->oldest

        `when`(event.deferReply(true)).thenReturn(replyAction)
        `when`(event.hook).thenReturn(hook)
        `when`(event.channel).thenReturn(union)
        `when`(union.asGuildMessageChannel()).thenReturn(channel)
        `when`(channel.name).thenReturn("chat")
        // Force fallback to history by making iterableHistory fail
        `when`(channel.iterableHistory).thenThrow(RuntimeException("no iterable"))
        `when`(channel.history).thenReturn(history)
        `when`(history.retrievePast(Mockito.anyInt())).thenReturn(rest)
        `when`(rest.complete()).thenReturn(fetched)
        @Suppress("UNCHECKED_CAST")
        `when`(hook.sendMessage(Mockito.anyString())).thenReturn(messageAction)

        // Stub service and capture prompt via verify afterwards
        `when`(svc.generateText(Mockito.anyString())).thenReturn("Summary with @here mention")

        // Event user for footer
        val evUser = Mockito.mock(User::class.java)
        `when`(evUser.asTag).thenReturn("tester#0001")
        `when`(event.user).thenReturn(evUser)

        // Mock JDA spam channel flow
        val jda = Mockito.mock(net.dv8tion.jda.api.JDA::class.java)
        val spamChannel = Mockito.mock(net.dv8tion.jda.api.entities.channel.concrete.TextChannel::class.java)
        val channelMsgAction = Mockito.mock(net.dv8tion.jda.api.requests.restaction.MessageCreateAction::class.java)
        val sentMsg = Mockito.mock(Message::class.java)
        `when`(event.jda).thenReturn(jda)
        `when`(jda.getTextChannelById("1234567890")).thenReturn(spamChannel)
        `when`(spamChannel.sendMessageEmbeds(Mockito.any(net.dv8tion.jda.api.entities.MessageEmbed::class.java))).thenReturn(channelMsgAction)
        `when`((channelMsgAction as net.dv8tion.jda.api.requests.RestAction<Message>).complete()).thenReturn(sentMsg)
        `when`(sentMsg.jumpUrl).thenReturn("https://discord.com/channels/1/2/3")

        cmd.execute(event)

        // Verify embed sent to spam channel and link posted via hook
        val embedCaptor = ArgumentCaptor.forClass(net.dv8tion.jda.api.entities.MessageEmbed::class.java)
        Mockito.verify(spamChannel).sendMessageEmbeds(embedCaptor.capture())
        val embed = embedCaptor.value
        assertEquals("Channel Summary", embed.title)
        val fields = embed.fields
        assertEquals("#chat", fields[0].value)
        assertEquals("2", fields[1].value) // only alice and charlie included after filtering
        assertTrue(embed.description?.contains("@\u200Bhere") == true)

        val linkMsgCaptor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(hook).sendMessage(linkMsgCaptor.capture())
        assertTrue(linkMsgCaptor.value.contains("https://discord.com/channels/1/2/3"))
        Mockito.verify(messageAction).queue()
    }

    @Test
    fun `count option is clamped to 100`() {
        val svc = Mockito.mock(GoogleGeminiService::class.java)
        val cmd = Summarize(svc, "1234567890")
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)
        val hook = Mockito.mock(InteractionHook::class.java)
        @Suppress("UNCHECKED_CAST")
        val messageAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>

        val union = Mockito.mock(MessageChannelUnion::class.java)
        val channel = Mockito.mock(GuildMessageChannel::class.java)
        val history = Mockito.mock(net.dv8tion.jda.api.entities.MessageHistory::class.java)
        @Suppress("UNCHECKED_CAST")
        val rest = Mockito.mock(RestAction::class.java) as RestAction<List<Message>>

        // Build 120 user messages (oldest first list created below)
        val oldestFirst = (1..120).map { idx ->
            mockMessage("user$idx", false, "m$idx")
        }
        val newestFirst = oldestFirst.reversed()

        val opt = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        `when`(opt.asLong).thenReturn(1000L) // request way above max

        `when`(event.deferReply(true)).thenReturn(replyAction)
        `when`(event.hook).thenReturn(hook)
        `when`(event.channel).thenReturn(union)
        `when`(event.getOption("count")).thenReturn(opt)
        `when`(union.asGuildMessageChannel()).thenReturn(channel)
        `when`(channel.name).thenReturn("chat")
        // Force fallback to history by making iterableHistory fail
        `when`(channel.iterableHistory).thenThrow(RuntimeException("no iterable"))
        `when`(channel.history).thenReturn(history)
        `when`(history.retrievePast(Mockito.anyInt())).thenReturn(rest)
        `when`(rest.complete()).thenReturn(newestFirst)
        `when`(svc.generateText(Mockito.anyString())).thenReturn("ok")
        `when`(hook.sendMessage(Mockito.anyString())).thenReturn(messageAction)

        // Mock JDA spam channel flow
        val jda = Mockito.mock(net.dv8tion.jda.api.JDA::class.java)
        val spamChannel = Mockito.mock(net.dv8tion.jda.api.entities.channel.concrete.TextChannel::class.java)
        val channelMsgAction = Mockito.mock(net.dv8tion.jda.api.requests.restaction.MessageCreateAction::class.java)
        val sentMsg = Mockito.mock(Message::class.java)
        `when`(event.jda).thenReturn(jda)
        `when`(jda.getTextChannelById("1234567890")).thenReturn(spamChannel)
        `when`(spamChannel.sendMessageEmbeds(Mockito.any(net.dv8tion.jda.api.entities.MessageEmbed::class.java))).thenReturn(channelMsgAction)
        `when`((channelMsgAction as net.dv8tion.jda.api.requests.RestAction<Message>).complete()).thenReturn(sentMsg)
        `when`(sentMsg.jumpUrl).thenReturn("https://discord.com/channels/1/2/3")

        val evUser = Mockito.mock(User::class.java)
        `when`(evUser.asTag).thenReturn("tester#0001")
        `when`(event.user).thenReturn(evUser)

        cmd.execute(event)

        // Capture embed sent to spam channel to inspect field for count
        val embedCaptor = ArgumentCaptor.forClass(net.dv8tion.jda.api.entities.MessageEmbed::class.java)
        Mockito.verify(spamChannel).sendMessageEmbeds(embedCaptor.capture())
        val embed = embedCaptor.value
        val fields = embed.fields
        // The embed has fields: Channel and Messages summarized
        assertEquals("Messages summarized", fields[1].name)
        assertEquals("100", fields[1].value) // clamped to 100

        Mockito.verify(messageAction).queue()
    }

    @Test
    fun `blank service response becomes no content`() {
        val svc = Mockito.mock(GoogleGeminiService::class.java)
        val cmd = Summarize(svc, "1234567890")
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)
        val hook = Mockito.mock(InteractionHook::class.java)
        @Suppress("UNCHECKED_CAST")
        val messageAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>

        val union = Mockito.mock(MessageChannelUnion::class.java)
        val channel = Mockito.mock(GuildMessageChannel::class.java)
        val history = Mockito.mock(net.dv8tion.jda.api.entities.MessageHistory::class.java)
        @Suppress("UNCHECKED_CAST")
        val rest = Mockito.mock(RestAction::class.java) as RestAction<List<Message>>

        val m1 = mockMessage("alice", false, "hi")

        `when`(event.deferReply(true)).thenReturn(replyAction)
        `when`(event.hook).thenReturn(hook)
        `when`(event.channel).thenReturn(union)
        `when`(union.asGuildMessageChannel()).thenReturn(channel)
        `when`(channel.name).thenReturn("chat")
        // Force fallback to history by making iterableHistory fail
        `when`(channel.iterableHistory).thenThrow(RuntimeException("no iterable"))
        `when`(channel.history).thenReturn(history)
        `when`(history.retrievePast(Mockito.anyInt())).thenReturn(rest)
        `when`(rest.complete()).thenReturn(listOf(m1))
        `when`(svc.generateText(Mockito.anyString())).thenReturn("")
        `when`(hook.sendMessage(Mockito.anyString())).thenReturn(messageAction)

        val evUser = Mockito.mock(User::class.java)
        `when`(evUser.asTag).thenReturn("tester#0001")
        `when`(event.user).thenReturn(evUser)

        // Mock JDA spam channel flow
        val jda = Mockito.mock(net.dv8tion.jda.api.JDA::class.java)
        val spamChannel = Mockito.mock(net.dv8tion.jda.api.entities.channel.concrete.TextChannel::class.java)
        val channelMsgAction = Mockito.mock(net.dv8tion.jda.api.requests.restaction.MessageCreateAction::class.java)
        val sentMsg = Mockito.mock(Message::class.java)
        `when`(event.jda).thenReturn(jda)
        `when`(jda.getTextChannelById("1234567890")).thenReturn(spamChannel)
        `when`(spamChannel.sendMessageEmbeds(Mockito.any(net.dv8tion.jda.api.entities.MessageEmbed::class.java))).thenReturn(channelMsgAction)
        `when`((channelMsgAction as net.dv8tion.jda.api.requests.RestAction<Message>).complete()).thenReturn(sentMsg)
        `when`(sentMsg.jumpUrl).thenReturn("https://discord.com/channels/1/2/3")

        cmd.execute(event)

        val embedCaptor = ArgumentCaptor.forClass(net.dv8tion.jda.api.entities.MessageEmbed::class.java)
        Mockito.verify(spamChannel).sendMessageEmbeds(embedCaptor.capture())
        val embed = embedCaptor.value
        assertEquals("(no content)", embed.description)
    }

    @Test
    fun `execute catches exception and replies error`() {
        val svc = Mockito.mock(GoogleGeminiService::class.java)
        val cmd = Summarize(svc, "1234567890")
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)
        val hook = Mockito.mock(InteractionHook::class.java)
        @Suppress("UNCHECKED_CAST")
        val messageAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>

        val union = Mockito.mock(MessageChannelUnion::class.java)
        val channel = Mockito.mock(GuildMessageChannel::class.java)
        val history = Mockito.mock(net.dv8tion.jda.api.entities.MessageHistory::class.java)
        @Suppress("UNCHECKED_CAST")
        val rest = Mockito.mock(RestAction::class.java) as RestAction<List<Message>>

        val m1 = mockMessage("alice", false, "hi")

        `when`(event.deferReply(true)).thenReturn(replyAction)
        `when`(event.hook).thenReturn(hook)
        `when`(event.channel).thenReturn(union)
        `when`(union.asGuildMessageChannel()).thenReturn(channel)
        `when`(channel.name).thenReturn("chat")
        // Force fallback to history by making iterableHistory fail
        `when`(channel.iterableHistory).thenThrow(RuntimeException("no iterable"))
        `when`(channel.history).thenReturn(history)
        `when`(history.retrievePast(Mockito.anyInt())).thenReturn(rest)
        `when`(rest.complete()).thenReturn(listOf(m1))
        `when`(hook.sendMessage(Mockito.anyString())).thenReturn(messageAction)
        `when`(messageAction.setEphemeral(true)).thenReturn(messageAction)

        `when`(svc.generateText(Mockito.anyString())).thenThrow(RuntimeException("boom"))

        cmd.execute(event)

        val msgCaptor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(hook).sendMessage(msgCaptor.capture())
        assertEquals("Error while summarizing: boom", msgCaptor.value)
        Mockito.verify(messageAction).setEphemeral(true)
        Mockito.verify(messageAction).queue()
    }

    // Helpers
    private fun mockMessage(username: String, isBot: Boolean, content: String): Message {
        val msg = Mockito.mock(Message::class.java)
        val user = Mockito.mock(User::class.java)
        `when`(user.isBot).thenReturn(isBot)
        `when`(user.name).thenReturn(username)
        `when`(msg.author).thenReturn(user)
        `when`(msg.contentStripped).thenReturn(content)
        return msg
    }
}
