package org.j3y.HuskerBot2.commands.other

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.Mockito.`when`

class SmmsTest {

    @Test
    fun `metadata and options are correct`() {
        val cmd = Smms()
        assertEquals("smms", cmd.getCommandKey())
        assertEquals("Tee hee", cmd.getDescription())

        // Permissions
        val perms: DefaultMemberPermissions = cmd.getPermissions()
        // Basic sanity: permissions object provided
        assertNotNull(perms)

        val opts: List<OptionData> = cmd.getOptions()
        assertEquals(2, opts.size)
        // destination
        assertEquals("destination", opts[0].name)
        assertEquals(OptionType.STRING, opts[0].type)
        assertTrue(opts[0].isRequired)
        val choices = opts[0].choices.associate { it.name to it.asString }
        assertEquals(mapOf("General" to "general", "Recruiting" to "recruiting", "Admin" to "admin"), choices)
        // message
        assertEquals("message", opts[1].name)
        assertEquals(OptionType.STRING, opts[1].type)
        assertTrue(opts[1].isRequired)
    }

    @Test
    fun `replies channel not found when destination channel cannot be resolved`() {
        val cmd = Smms().apply {
            generalChannelId = "gen"
            recruitingChannelId = "rec"
            adminChannelId = "adm"
        }

        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val jda = Mockito.mock(JDA::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)
        val hook = Mockito.mock(InteractionHook::class.java)
        @Suppress("UNCHECKED_CAST")
        val messageAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>

        // options
        val optDest = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        val optMsg = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        `when`(optDest.asString).thenReturn("general")
        `when`(optMsg.asString).thenReturn("hi")
        `when`(event.getOption("destination")).thenReturn(optDest)
        `when`(event.getOption("message")).thenReturn(optMsg)

        `when`(event.deferReply(true)).thenReturn(replyAction)
        `when`(event.hook).thenReturn(hook)
        `when`(hook.sendMessage(Mockito.anyString())).thenReturn(messageAction)

        `when`(event.jda).thenReturn(jda)
        `when`(jda.getTextChannelById("gen")).thenReturn(null)

        cmd.execute(event)

        Mockito.verify(replyAction).queue()
        val msgCaptor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(hook).sendMessage(msgCaptor.capture())
        assertEquals("Channel not found.", msgCaptor.value)
        Mockito.verify(messageAction).queue()
    }

    @Test
    fun `happy path sends embed to recruiting channel and acknowledges via hook`() {
        val cmd = Smms().apply {
            generalChannelId = "gen"
            recruitingChannelId = "rec"
            adminChannelId = "adm"
        }

        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val jda = Mockito.mock(JDA::class.java)
        val channel = Mockito.mock(TextChannel::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)
        val hook = Mockito.mock(InteractionHook::class.java)
        @Suppress("UNCHECKED_CAST")
        val messageAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>

        // Options
        val optDest = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        val optMsg = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        `when`(optDest.asString).thenReturn("recruiting")
        `when`(optMsg.asString).thenReturn("Top secret intel")
        `when`(event.getOption("destination")).thenReturn(optDest)
        `when`(event.getOption("message")).thenReturn(optMsg)

        // Defer and hook
        `when`(event.deferReply(true)).thenReturn(replyAction)
        `when`(event.hook).thenReturn(hook)
        `when`(hook.sendMessage(Mockito.anyString())).thenReturn(messageAction)

        // JDA channel
        `when`(event.jda).thenReturn(jda)
        `when`(jda.getTextChannelById("rec")).thenReturn(channel)
        `when`(channel.asMention).thenReturn("<#rec>")

        // Capture embed
        val embedCaptor = ArgumentCaptor.forClass(MessageEmbed::class.java)
        @Suppress("UNCHECKED_CAST")
        val channelMsgAction = Mockito.mock(net.dv8tion.jda.api.requests.restaction.MessageCreateAction::class.java)
        `when`(channel.sendMessageEmbeds(embedCaptor.capture())).thenReturn(channelMsgAction)

        cmd.execute(event)

        // Verify embed content
        val embed = embedCaptor.value
        assertNotNull(embed)
        assertEquals("Secret Mammal Message System (SMMS)", embed.title)
        assertEquals("These messages have no way to be verified to be accurate.", embed.description)
        // Exactly one field with the provided message
        assertEquals(1, embed.fields.size)
        assertEquals("Back Channel Communication", embed.fields[0].name)
        assertEquals("Top secret intel", embed.fields[0].value)
        assertEquals(false, embed.fields[0].isInline)
        // Footer and thumbnail present
        assertTrue(embed.footer?.text?.contains("anonymous") == true)
        assertTrue(embed.thumbnail?.url?.contains("i.imgur.com/EGC1qNt.jpg") == true)

        // Verify acknowledgement message
        val ackCaptor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(hook).sendMessage(ackCaptor.capture())
        assertEquals("Back channel communication successfully sent to <#rec>", ackCaptor.value)

        // Verify queues called
        Mockito.verify(replyAction).queue()
        Mockito.verify(channel).sendMessageEmbeds(Mockito.any(MessageEmbed::class.java))
        Mockito.verify(channelMsgAction).queue()
        Mockito.verify(messageAction).queue()
    }

    @Test
    fun `invalid destination value results in channel not found`() {
        val cmd = Smms().apply {
            generalChannelId = "gen"
            recruitingChannelId = "rec"
            adminChannelId = "adm"
        }
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)
        val hook = Mockito.mock(InteractionHook::class.java)
        @Suppress("UNCHECKED_CAST")
        val messageAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>

        val optDest = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        val optMsg = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        `when`(optDest.asString).thenReturn("unknown")
        `when`(optMsg.asString).thenReturn("whatever")
        `when`(event.getOption("destination")).thenReturn(optDest)
        `when`(event.getOption("message")).thenReturn(optMsg)

        `when`(event.deferReply(true)).thenReturn(replyAction)
        `when`(event.hook).thenReturn(hook)
        `when`(hook.sendMessage(Mockito.anyString())).thenReturn(messageAction)

        cmd.execute(event)

        Mockito.verify(replyAction).queue()
        val msgCaptor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(hook).sendMessage(msgCaptor.capture())
        assertEquals("Channel not found.", msgCaptor.value)
        Mockito.verify(messageAction).queue()
    }

    @Test
    fun `missing message option still sends embed with empty content`() {
        val cmd = Smms().apply {
            generalChannelId = "gen"
            recruitingChannelId = "rec"
            adminChannelId = "adm"
        }

        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val jda = Mockito.mock(JDA::class.java)
        val channel = Mockito.mock(TextChannel::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)
        val hook = Mockito.mock(InteractionHook::class.java)
        @Suppress("UNCHECKED_CAST")
        val messageAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>

        val optDest = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        `when`(optDest.asString).thenReturn("admin")
        `when`(event.getOption("destination")).thenReturn(optDest)
        `when`(event.getOption("message")).thenReturn(null)

        `when`(event.deferReply(true)).thenReturn(replyAction)
        `when`(event.hook).thenReturn(hook)
        `when`(hook.sendMessage(Mockito.anyString())).thenReturn(messageAction)

        `when`(event.jda).thenReturn(jda)
        `when`(jda.getTextChannelById("adm")).thenReturn(channel)
        `when`(channel.asMention).thenReturn("<#adm>")

        val embedCaptor = ArgumentCaptor.forClass(MessageEmbed::class.java)
        @Suppress("UNCHECKED_CAST")
        val channelMsgAction = Mockito.mock(net.dv8tion.jda.api.requests.restaction.MessageCreateAction::class.java)
        `when`(channel.sendMessageEmbeds(embedCaptor.capture())).thenReturn(channelMsgAction)

        cmd.execute(event)

        val embed = embedCaptor.value
        assertEquals("Secret Mammal Message System (SMMS)", embed.title)
        assertEquals(1, embed.fields.size)
        assertEquals("Back Channel Communication", embed.fields[0].name)
        val v = embed.fields[0].value
        assertTrue(v == "" || v == "\u200E" || v == "\u200B")

        Mockito.verify(replyAction).queue()
        Mockito.verify(channelMsgAction).queue()
        Mockito.verify(messageAction).queue()
    }
}
