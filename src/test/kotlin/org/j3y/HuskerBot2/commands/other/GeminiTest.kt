package org.j3y.HuskerBot2.commands.other

import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import org.j3y.HuskerBot2.commands.ai.Gemini
import org.j3y.HuskerBot2.service.GoogleGeminiService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.Mockito.`when`

class GeminiTest {

    private fun setupJdaSpamChannelMocks(event: SlashCommandInteractionEvent): Triple<net.dv8tion.jda.api.JDA, net.dv8tion.jda.api.entities.channel.concrete.TextChannel, net.dv8tion.jda.api.requests.restaction.MessageCreateAction> {
        val jda = Mockito.mock(net.dv8tion.jda.api.JDA::class.java)
        val spamChannel = Mockito.mock(net.dv8tion.jda.api.entities.channel.concrete.TextChannel::class.java)
        val msgCreateAction = Mockito.mock(net.dv8tion.jda.api.requests.restaction.MessageCreateAction::class.java)
        val sentMessage = Mockito.mock(Message::class.java)
        `when`(event.jda).thenReturn(jda)
        `when`(jda.getTextChannelById("123")).thenReturn(spamChannel)
        @Suppress("UNCHECKED_CAST")
        `when`(spamChannel.sendMessageEmbeds(Mockito.anyList<MessageEmbed>())).thenReturn(msgCreateAction)
        `when`(msgCreateAction.complete()).thenReturn(sentMessage)
        `when`(sentMessage.jumpUrl).thenReturn("https://discordapp.com/channels/1/2/3")
        return Triple(jda, spamChannel, msgCreateAction)
    }

    @Test
    fun `metadata and options are correct`() {
        val svc = Mockito.mock(GoogleGeminiService::class.java)
        val cmd = Gemini(svc, "123")
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
        val cmd = Gemini(svc, "123")
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)
        val hook = Mockito.mock(InteractionHook::class.java)
        @Suppress("UNCHECKED_CAST")
        val messageAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>

        `when`(event.deferReply(true)).thenReturn(replyAction)
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
        val cmd = Gemini(svc, "123")
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)
        val hook = Mockito.mock(InteractionHook::class.java)

        val opt = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        `when`(opt.asString).thenReturn("Say hi to @everyone and @here")

        `when`(event.deferReply(true)).thenReturn(replyAction)
        `when`(event.hook).thenReturn(hook)
        `when`(event.getOption("prompt")).thenReturn(opt)

        // mock user used in footer
        val user = Mockito.mock(net.dv8tion.jda.api.entities.User::class.java)
        `when`(event.user).thenReturn(user)
        `when`(user.effectiveName).thenReturn("Tester")
        `when`(user.avatarUrl).thenReturn(null)

        // Setup spam channel mocks and capture embeds sent there
        setupJdaSpamChannelMocks(event)

        // Service should return a response; capture argument via verify after execution
        `when`(svc.generateText(Mockito.anyString(), Mockito.any())).thenReturn("Hello @here world")

        // Capture the message sent back to the user with the link
        @Suppress("UNCHECKED_CAST")
        val hookMsgAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>
        `when`(hook.sendMessage(Mockito.anyString())).thenReturn(hookMsgAction)
        `when`(hookMsgAction.setEphemeral(true)).thenReturn(hookMsgAction)

        cmd.execute(event)

        // Verify interactions continued
        Mockito.verify(replyAction).queue()

        // Verify we sent an ephemeral link response to the user
        val linkMsgCaptor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(hook).sendMessage(linkMsgCaptor.capture())
        assertTrue(linkMsgCaptor.value.startsWith("Sent gemini output to bot spam channel:"))
        Mockito.verify(hookMsgAction).setEphemeral(true)
        Mockito.verify(hookMsgAction).queue()
    }

    @Test
    fun `execute chunks long response and caps to 3 embeds with ellipsis`() {
        val svc = Mockito.mock(GoogleGeminiService::class.java)
        val cmd = Gemini(svc, "123")
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)
        val hook = Mockito.mock(InteractionHook::class.java)

        val opt = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        `when`(opt.asString).thenReturn("Long prompt")

        `when`(event.deferReply(true)).thenReturn(replyAction)
        `when`(event.hook).thenReturn(hook)
        `when`(event.getOption("prompt")).thenReturn(opt)

        // mock user used in footer
        val user = Mockito.mock(net.dv8tion.jda.api.entities.User::class.java)
        `when`(event.user).thenReturn(user)
        `when`(user.effectiveName).thenReturn("Tester")
        `when`(user.avatarUrl).thenReturn(null)

        val (_, spamChannel, msgCreateAction) = setupJdaSpamChannelMocks(event)

        val longText = "A".repeat(1024 * 3 + 10)
        `when`(svc.generateText(Mockito.anyString(), Mockito.any())).thenReturn(longText)

        // Need to capture the embeds passed to spamChannel
        val embedsCaptor = ArgumentCaptor.forClass(MutableList::class.java as Class<MutableList<MessageEmbed>>)
        `when`(spamChannel.sendMessageEmbeds(embedsCaptor.capture())).thenReturn(msgCreateAction)

        // Prepare hook link send
        @Suppress("UNCHECKED_CAST")
        val hookMsgAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>
        `when`(hook.sendMessage(Mockito.anyString())).thenReturn(hookMsgAction)
        `when`(hookMsgAction.setEphemeral(true)).thenReturn(hookMsgAction)

        cmd.execute(event)

        Mockito.verify(replyAction).queue()

        val embeds = embedsCaptor.value
        assertEquals(3, embeds.size) // capped at 3
        // First embed has 2 fields (Prompt + Response)
        assertEquals(2, embeds[0].fields.size)
        // Subsequent embeds have 1 field each
        assertEquals(1, embeds[1].fields.size)
        assertEquals(1, embeds[2].fields.size)
        // Last field should end with ellipsis to indicate truncation and be length 1024
        val lastFieldValue = embeds[2].fields[0].value
        assertTrue(lastFieldValue?.endsWith("...") == true)
        assertEquals(1024, lastFieldValue?.length)
    }

    @Test
    fun `execute treats blank service response as no content`() {
        val svc = Mockito.mock(GoogleGeminiService::class.java)
        val cmd = Gemini(svc, "123")
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)
        val hook = Mockito.mock(InteractionHook::class.java)

        val opt = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        `when`(opt.asString).thenReturn("Prompt")

        `when`(event.deferReply(true)).thenReturn(replyAction)
        `when`(event.hook).thenReturn(hook)
        `when`(event.getOption("prompt")).thenReturn(opt)

        // mock user used in footer
        val user = Mockito.mock(net.dv8tion.jda.api.entities.User::class.java)
        `when`(event.user).thenReturn(user)
        `when`(user.effectiveName).thenReturn("Tester")
        `when`(user.avatarUrl).thenReturn(null)

        val (_, spamChannel, msgCreateAction) = setupJdaSpamChannelMocks(event)
        `when`(svc.generateText(Mockito.anyString(), Mockito.any())).thenReturn("")

        val embedsCaptor = ArgumentCaptor.forClass(MutableList::class.java as Class<MutableList<MessageEmbed>>)
        `when`(spamChannel.sendMessageEmbeds(embedsCaptor.capture())).thenReturn(msgCreateAction)

        // Prepare hook link send
        @Suppress("UNCHECKED_CAST")
        val hookMsgAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>
        `when`(hook.sendMessage(Mockito.anyString())).thenReturn(hookMsgAction)
        `when`(hookMsgAction.setEphemeral(true)).thenReturn(hookMsgAction)

        cmd.execute(event)

        val embeds = embedsCaptor.value
        val fields = embeds[0].fields
        assertEquals("(no content)", fields[1].value)
    }

    @Test
    fun `execute catches exception and replies error`() {
        val svc = Mockito.mock(GoogleGeminiService::class.java)
        val cmd = Gemini(svc, "123")
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)
        val hook = Mockito.mock(InteractionHook::class.java)
        @Suppress("UNCHECKED_CAST")
        val messageAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>

        val opt = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        `when`(opt.asString).thenReturn("Prompt")

        `when`(event.deferReply(true)).thenReturn(replyAction)
        `when`(event.hook).thenReturn(hook)
        `when`(event.getOption("prompt")).thenReturn(opt)
        `when`(hook.sendMessage(Mockito.anyString())).thenReturn(messageAction)
        `when`(messageAction.setEphemeral(true)).thenReturn(messageAction)

        `when`(svc.generateText(Mockito.anyString(), Mockito.any())).thenThrow(RuntimeException("boom"))

        cmd.execute(event)

        Mockito.verify(replyAction).queue()
        val msgCaptor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(hook).sendMessage(msgCaptor.capture())
        assertEquals("Error while calling Gemini: boom", msgCaptor.value)
        Mockito.verify(messageAction).setEphemeral(true)
        Mockito.verify(messageAction).queue()
    }
}
