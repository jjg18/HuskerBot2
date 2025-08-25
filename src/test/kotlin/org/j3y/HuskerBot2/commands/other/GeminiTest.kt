package org.j3y.HuskerBot2.commands.other

import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import org.j3y.HuskerBot2.service.GoogleGeminiService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.Mockito.`when`

class GeminiTest {

    @Test
    fun `metadata and options are correct`() {
        val svc = Mockito.mock(GoogleGeminiService::class.java)
        val cmd = Gemini(svc)
        assertEquals("gemini", cmd.getCommandKey())
        assertEquals("Send a text prompt to Google Gemini (free tier)", cmd.getDescription())
        val opts: List<OptionData> = cmd.getOptions()
        assertEquals(1, opts.size)
        assertEquals("prompt", opts[0].name)
        assertEquals(OptionType.STRING, opts[0].type)
        assertTrue(opts[0].isRequired)
    }

    @Test
    fun `execute replies when prompt missing`() {
        val svc = Mockito.mock(GoogleGeminiService::class.java)
        val cmd = Gemini(svc)
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)
        val hook = Mockito.mock(InteractionHook::class.java)
        @Suppress("UNCHECKED_CAST")
        val messageAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>

        `when`(event.deferReply()).thenReturn(replyAction)
        `when`(event.hook).thenReturn(hook)
        `when`(hook.sendMessage(Mockito.anyString())).thenReturn(messageAction)
        `when`(messageAction.setEphemeral(true)).thenReturn(messageAction)
        // Missing option
        `when`(event.getOption("prompt")).thenReturn(null)

        cmd.execute(event)

        Mockito.verify(replyAction).queue()
        val msgCaptor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(hook).sendMessage(msgCaptor.capture())
        assertEquals("Please provide a prompt.", msgCaptor.value)
        Mockito.verify(messageAction).setEphemeral(true)
        Mockito.verify(messageAction).queue()
    }

    @Test
    fun `execute happy path builds single embed and sanitizes mentions`() {
        val svc = Mockito.mock(GoogleGeminiService::class.java)
        val cmd = Gemini(svc)
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)
        val hook = Mockito.mock(InteractionHook::class.java)
        @Suppress("UNCHECKED_CAST")
        val messageAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>

        val opt = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        `when`(opt.asString).thenReturn("Say hi to @everyone and @here")

        `when`(event.deferReply()).thenReturn(replyAction)
        `when`(event.hook).thenReturn(hook)
        `when`(event.getOption("prompt")).thenReturn(opt)
        @Suppress("UNCHECKED_CAST")
        `when`(hook.sendMessageEmbeds(Mockito.anyList<MessageEmbed>())).thenReturn(messageAction)

        // Service should return a response; capture argument via verify after execution
        @Suppress("UNCHECKED_CAST")
        `when`(svc.generateText(Mockito.anyString())).thenReturn("Hello @here world")

        cmd.execute(event)

        // Verify interactions continued
        Mockito.verify(replyAction).queue()
        val embedsCaptor = ArgumentCaptor.forClass(MutableList::class.java as Class<MutableList<MessageEmbed>>)
        Mockito.verify(hook).sendMessageEmbeds(embedsCaptor.capture())
        Mockito.verify(messageAction).queue()

        val embeds = embedsCaptor.value
        assertEquals(1, embeds.size)
        val embed = embeds[0]
        assertEquals("Gemini AI", embed.title)
        // It should include both fields: Prompt and Response
        val fields = embed.fields
        assertEquals(2, fields.size)
        assertEquals("Prompt", fields[0].name)
        assertEquals("Say hi to @everyone and @here", fields[0].value)
        assertEquals("Response", fields[1].name)
        // Mentions should be sanitized with zero-width space
        assertTrue(fields[1].value?.contains("@\u200Bhere") == true)
    }

    @Test
    fun `execute chunks long response and caps to 3 embeds with ellipsis`() {
        val svc = Mockito.mock(GoogleGeminiService::class.java)
        val cmd = Gemini(svc)
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)
        val hook = Mockito.mock(InteractionHook::class.java)
        @Suppress("UNCHECKED_CAST")
        val messageAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>

        val opt = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        `when`(opt.asString).thenReturn("Long prompt")

        `when`(event.deferReply()).thenReturn(replyAction)
        `when`(event.hook).thenReturn(hook)
        `when`(event.getOption("prompt")).thenReturn(opt)
        @Suppress("UNCHECKED_CAST")
        `when`(hook.sendMessageEmbeds(Mockito.anyList<MessageEmbed>())).thenReturn(messageAction)

        val longText = "A".repeat(1024 * 3 + 10)
        `when`(svc.generateText(Mockito.anyString())).thenReturn(longText)

        cmd.execute(event)

        Mockito.verify(replyAction).queue()
        val embedsCaptor = ArgumentCaptor.forClass(MutableList::class.java as Class<MutableList<MessageEmbed>>)
        Mockito.verify(hook).sendMessageEmbeds(embedsCaptor.capture())
        Mockito.verify(messageAction).queue()

        val embeds = embedsCaptor.value
        assertEquals(3, embeds.size) // capped at 3
        // First embed has 2 fields (Prompt + Response)
        assertEquals(2, embeds[0].fields.size)
        // Subsequent embeds have 1 field each
        assertEquals(1, embeds[1].fields.size)
        assertEquals(1, embeds[2].fields.size)
        // Last field should end with ellipsis to indicate truncation
        val lastFieldValue = embeds[2].fields[0].value
        assertTrue(lastFieldValue?.endsWith("...") == true)
        // Length should be exactly 1024 (original chunk length)
        assertEquals(1024, lastFieldValue?.length)
    }

    @Test
    fun `execute treats blank service response as no content`() {
        val svc = Mockito.mock(GoogleGeminiService::class.java)
        val cmd = Gemini(svc)
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)
        val hook = Mockito.mock(InteractionHook::class.java)
        @Suppress("UNCHECKED_CAST")
        val messageAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>

        val opt = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        `when`(opt.asString).thenReturn("Prompt")

        `when`(event.deferReply()).thenReturn(replyAction)
        `when`(event.hook).thenReturn(hook)
        `when`(event.getOption("prompt")).thenReturn(opt)
        @Suppress("UNCHECKED_CAST")
        `when`(hook.sendMessageEmbeds(Mockito.anyList<MessageEmbed>())).thenReturn(messageAction)

        `when`(svc.generateText(Mockito.anyString())).thenReturn("")

        cmd.execute(event)

        val embedsCaptor = ArgumentCaptor.forClass(MutableList::class.java as Class<MutableList<MessageEmbed>>)
        Mockito.verify(hook).sendMessageEmbeds(embedsCaptor.capture())
        val embeds = embedsCaptor.value
        val fields = embeds[0].fields
        assertEquals("(no content)", fields[1].value)
    }

    @Test
    fun `execute catches exception and replies error`() {
        val svc = Mockito.mock(GoogleGeminiService::class.java)
        val cmd = Gemini(svc)
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)
        val hook = Mockito.mock(InteractionHook::class.java)
        @Suppress("UNCHECKED_CAST")
        val messageAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>

        val opt = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        `when`(opt.asString).thenReturn("Prompt")

        `when`(event.deferReply()).thenReturn(replyAction)
        `when`(event.hook).thenReturn(hook)
        `when`(event.getOption("prompt")).thenReturn(opt)
        `when`(hook.sendMessage(Mockito.anyString())).thenReturn(messageAction)
        `when`(messageAction.setEphemeral(true)).thenReturn(messageAction)

        `when`(svc.generateText(Mockito.anyString())).thenThrow(RuntimeException("boom"))

        cmd.execute(event)

        Mockito.verify(replyAction).queue()
        val msgCaptor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(hook).sendMessage(msgCaptor.capture())
        assertEquals("Error while calling Gemini: boom", msgCaptor.value)
        Mockito.verify(messageAction).setEphemeral(true)
        Mockito.verify(messageAction).queue()
    }
}
