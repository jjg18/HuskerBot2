package org.j3y.HuskerBot2.commands.other

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.Mockito.`when`

class PossumTest {

    @Test
    fun `metadata and options are correct`() {
        val cmd = Possum()
        assertEquals("possum", cmd.getCommandKey())
        assertEquals("Share possum droppings for the server", cmd.getDescription())

        val opts: List<OptionData> = cmd.getOptions()
        assertEquals(1, opts.size)
        assertEquals("message", opts[0].name)
        assertEquals(OptionType.STRING, opts[0].type)
        assertTrue(opts[0].isRequired)
    }

    @Test
    fun `replies ephemerally when possum channel not found`() {
        val cmd = Possum().apply { possumChannelId = "12345" }
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val jda = Mockito.mock(JDA::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)

        `when`(event.jda).thenReturn(jda)
        `when`(jda.getTextChannelById("12345")).thenReturn(null)
        `when`(event.reply("Possum channel not found.")).thenReturn(replyAction)
        `when`(replyAction.setEphemeral(true)).thenReturn(replyAction)

        cmd.execute(event)

        Mockito.verify(event).reply("Possum channel not found.")
        Mockito.verify(replyAction).setEphemeral(true)
        Mockito.verify(replyAction).queue()
    }

    @Test
    fun `happy path sends embed to possum channel and replies success`() {
        val cmd = Possum().apply { possumChannelId = "abc" }
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val jda = Mockito.mock(JDA::class.java)
        val channel = Mockito.mock(TextChannel::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)
        @Suppress("UNCHECKED_CAST")
        val msgAction = Mockito.mock(MessageCreateAction::class.java) as MessageCreateAction
        val opt = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)

        `when`(opt.asString).thenReturn("Hello from the possum")
        `when`(event.getOption("message")).thenReturn(opt)

        `when`(event.jda).thenReturn(jda)
        `when`(jda.getTextChannelById("abc")).thenReturn(channel)

        // capture the embed sent
        val embedCaptor = ArgumentCaptor.forClass(MessageEmbed::class.java)
        `when`(channel.sendMessageEmbeds(embedCaptor.capture())).thenReturn(msgAction)

        `when`(event.reply("Possum droppings sent!")).thenReturn(replyAction)
        `when`(replyAction.setEphemeral(true)).thenReturn(replyAction)

        cmd.execute(event)

        // Verify embed contents
        val embed = embedCaptor.value
        assertNotNull(embed)
        assertEquals("Possum Droppings", embed.title)
        // One field named "Dropping" with the provided message
        val fields = embed.fields
        assertEquals(1, fields.size)
        assertEquals("Dropping", fields[0].name)
        assertEquals("Hello from the possum", fields[0].value)
        assertEquals(false, fields[0].isInline)
        // Footer and thumbnail present
        assertEquals("Created by a sneaky possum", embed.footer?.text)
        assertTrue(embed.thumbnail?.url?.contains("unknown.jpeg") == true)

        // Verify actions
        Mockito.verify(channel).sendMessageEmbeds(Mockito.any(MessageEmbed::class.java))
        Mockito.verify(event).reply("Possum droppings sent!")
        Mockito.verify(replyAction).setEphemeral(true)
        Mockito.verify(replyAction).queue()
    }

    @Test
    fun `missing option defaults to empty string but still sends embed when channel exists`() {
        val cmd = Possum().apply { possumChannelId = "chan" }
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val jda = Mockito.mock(JDA::class.java)
        val channel = Mockito.mock(TextChannel::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)
        @Suppress("UNCHECKED_CAST")
        val msgAction = Mockito.mock(MessageCreateAction::class.java) as MessageCreateAction

        // Option missing
        `when`(event.getOption("message")).thenReturn(null)
        `when`(event.jda).thenReturn(jda)
        `when`(jda.getTextChannelById("chan")).thenReturn(channel)

        val embedCaptor = ArgumentCaptor.forClass(MessageEmbed::class.java)
        `when`(channel.sendMessageEmbeds(embedCaptor.capture())).thenReturn(msgAction)
        `when`(event.reply("Possum droppings sent!")).thenReturn(replyAction)
        `when`(replyAction.setEphemeral(true)).thenReturn(replyAction)

        cmd.execute(event)

        val embed = embedCaptor.value
        assertEquals("Possum Droppings", embed.title)
        assertEquals(1, embed.fields.size)
        assertEquals("Dropping", embed.fields[0].name)
        val v = embed.fields[0].value
        assertTrue(v == "" || v == "\u200E" || v == "\u200B") // JDA may coerce empty to zero-width space

        Mockito.verify(replyAction).setEphemeral(true)
        Mockito.verify(replyAction).queue()
    }
}
