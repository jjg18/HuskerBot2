package org.j3y.HuskerBot2.commands.other

import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.Mockito.`when`

class MarkovTest {

    private fun mockMessage(contentRaw: String, contentStripped: String = contentRaw, isBot: Boolean = false): Message {
        val msg = Mockito.mock(Message::class.java)
        val user = Mockito.mock(User::class.java)
        `when`(user.isBot).thenReturn(isBot)
        `when`(msg.author).thenReturn(user)
        `when`(msg.contentRaw).thenReturn(contentRaw)
        `when`(msg.contentStripped).thenReturn(contentStripped)
        return msg
    }

    private fun baseEventMocks(): Triple<SlashCommandInteractionEvent, ReplyCallbackAction, InteractionHook> {
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val reply = Mockito.mock(ReplyCallbackAction::class.java)
        val hook = Mockito.mock(InteractionHook::class.java)
        `when`(event.deferReply()).thenReturn(reply)
        `when`(event.hook).thenReturn(hook)
        return Triple(event, reply, hook)
    }

    @Test
    fun `metadata and options are correct`() {
        val cmd = Markov()
        assertEquals("markov", cmd.getCommandKey())
        assertEquals("Generate text from recent channel messages using a Markov chain", cmd.getDescription())
        val opts: List<OptionData> = cmd.getOptions()
        assertEquals(3, opts.size)
        assertEquals("messages", opts[0].name)
        assertEquals(OptionType.INTEGER, opts[0].type)
        assertEquals("order", opts[1].name)
        assertEquals(OptionType.INTEGER, opts[1].type)
        assertEquals("seed", opts[2].name)
        assertEquals(OptionType.STRING, opts[2].type)
    }

    @Test
    fun `execute replies when not in guild channel`() {
        val cmd = Markov()
        val (event, reply, hook) = baseEventMocks()
        @Suppress("UNCHECKED_CAST")
        val msgAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>

        // Simulate failure converting channel to GuildMessageChannel
        val channelUnion = Mockito.mock(net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion::class.java)
        `when`(event.channel).thenReturn(channelUnion)
        `when`(channelUnion.asGuildMessageChannel()).thenThrow(IllegalStateException("not guild"))
        `when`(hook.sendMessage(Mockito.anyString())).thenReturn(msgAction)

        cmd.execute(event)

        Mockito.verify(reply).queue()
        val captor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(hook).sendMessage(captor.capture())
        assertEquals("This command can only be used in guild text channels.", captor.value)
        Mockito.verify(msgAction).queue()
    }

    @Test
    fun `execute replies when no usable text`() {
        val cmd = Markov()
        val (event, reply, hook) = baseEventMocks()
        @Suppress("UNCHECKED_CAST")
        val msgAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>

        // Mock channel and history fallback path
        val channelUnion = Mockito.mock(net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion::class.java)
        val channel = Mockito.mock(GuildMessageChannel::class.java, Mockito.RETURNS_DEEP_STUBS)
        `when`(event.channel).thenReturn(channelUnion)
        `when`(channelUnion.asGuildMessageChannel()).thenReturn(channel)

        // Cause iterableHistory to throw and use fallback
        `when`(channel.iterableHistory).thenThrow(RuntimeException("iterable not available"))
        @Suppress("UNCHECKED_CAST")
        val rest0 = Mockito.mock(RestAction::class.java) as RestAction<List<Message>>
        `when`(channel.history.retrievePast(Mockito.anyInt())).thenReturn(rest0)
        val list0 = listOf(
            mockMessage("/help"),
            mockMessage("https://example.com"),
            mockMessage("   ", "", isBot = false),
            mockMessage("bot says hi", isBot = true)
        )
        `when`(rest0.complete()).thenReturn(list0)

        val user = Mockito.mock(User::class.java)
        `when`(user.asTag).thenReturn("tester#0001")
        `when`(event.user).thenReturn(user)

        `when`(hook.sendMessage(Mockito.anyString())).thenReturn(msgAction)

        cmd.execute(event)

        Mockito.verify(reply).queue()
        val captor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(hook).sendMessage(captor.capture())
        assertEquals("I couldn't find enough usable text in recent messages.", captor.value)
        Mockito.verify(msgAction).queue()
    }

    @Test
    fun `execute generates embed with scanned count and order`() {
        val cmd = Markov()
        val (event, reply, hook) = baseEventMocks()
        @Suppress("UNCHECKED_CAST")
        val embedAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>

        val channelUnion = Mockito.mock(net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion::class.java)
        val channel = Mockito.mock(GuildMessageChannel::class.java, Mockito.RETURNS_DEEP_STUBS)
        `when`(event.channel).thenReturn(channelUnion)
        `when`(channelUnion.asGuildMessageChannel()).thenReturn(channel)
        `when`(channel.name).thenReturn("general")

        // Force fallback path for easier mocking
        `when`(channel.iterableHistory).thenThrow(RuntimeException("iterable not available"))
        @Suppress("UNCHECKED_CAST")
        val rest1 = Mockito.mock(RestAction::class.java) as RestAction<List<Message>>
        `when`(channel.history.retrievePast(Mockito.anyInt())).thenReturn(rest1)
        val list1 = listOf(
            mockMessage("Hello world.", "Hello world."),
            mockMessage("This is a test.", "This is a test."),
            mockMessage("Markov chains are fun.", "Markov chains are fun."),
            mockMessage("/slash should be ignored")
        )
        `when`(rest1.complete()).thenReturn(list1)

        val user = Mockito.mock(User::class.java)
        `when`(user.asTag).thenReturn("tester#0001")
        `when`(event.user).thenReturn(user)

        // Options: order=2, messages=50, seed="Markov"
        val optOrder = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        val optMsgs = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        val optSeed = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        `when`(optOrder.asLong).thenReturn(2L)
        `when`(optMsgs.asLong).thenReturn(50L)
        `when`(optSeed.asString).thenReturn("Markov")
        `when`(event.getOption("order")).thenReturn(optOrder)
        `when`(event.getOption("messages")).thenReturn(optMsgs)
        `when`(event.getOption("seed")).thenReturn(optSeed)

        `when`(hook.sendMessageEmbeds(Mockito.any(MessageEmbed::class.java))).thenReturn(embedAction)

        cmd.execute(event)

        Mockito.verify(reply).queue()
        val captor = ArgumentCaptor.forClass(MessageEmbed::class.java)
        Mockito.verify(hook).sendMessageEmbeds(captor.capture())
        val embed = captor.value
        assertEquals("Markov Chain", embed.title)
        assertTrue(!embed.description.isNullOrBlank())
        // Fields
        val fields = embed.fields
        assertTrue(fields.any { it.name == "Source" && it.value == "#general" })
        assertTrue(fields.any { it.name == "Messages scanned" && it.value == "3" })
        assertTrue(fields.any { it.name == "Order" && it.value == "2" })
        Mockito.verify(embedAction).queue()
    }

    @Test
    fun `messages option coerces to min 10 and fallback retrievePast called with 10`() {
        val cmd = Markov()
        val (event, reply, hook) = baseEventMocks()
        @Suppress("UNCHECKED_CAST")
        val embedAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>

        val channelUnion = Mockito.mock(net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion::class.java)
        val channel = Mockito.mock(GuildMessageChannel::class.java, Mockito.RETURNS_DEEP_STUBS)
        `when`(event.channel).thenReturn(channelUnion)
        `when`(channelUnion.asGuildMessageChannel()).thenReturn(channel)
        `when`(channel.name).thenReturn("general")

        // iterable path throws; fallback path used
        `when`(channel.iterableHistory).thenThrow(RuntimeException("iterable not available"))
        val messages = (1..15).map { mockMessage("m$it.") }
        @Suppress("UNCHECKED_CAST")
                val rest2 = Mockito.mock(RestAction::class.java) as RestAction<List<Message>>
                `when`(channel.history.retrievePast(Mockito.anyInt())).thenReturn(rest2)
                `when`(rest2.complete()).thenReturn(messages)

        val optMsgs = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        `when`(optMsgs.asLong).thenReturn(5L) // should coerce to 10
        `when`(event.getOption("messages")).thenReturn(optMsgs)

        val user = Mockito.mock(User::class.java)
        `when`(user.asTag).thenReturn("tester#0001")
        `when`(event.user).thenReturn(user)

        `when`(hook.sendMessageEmbeds(Mockito.any(MessageEmbed::class.java))).thenReturn(embedAction)

        cmd.execute(event)

        // Verify we requested exactly 10 in fallback
        Mockito.verify(channel.history).retrievePast(10)
        Mockito.verify(reply).queue()
        Mockito.verify(embedAction).queue()
    }

    @Test
    fun `order option coerces to bounds 1-3`() {
        val cmd = Markov()
        val (event, reply, hook) = baseEventMocks()
        @Suppress("UNCHECKED_CAST")
        val embedAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>

        val channelUnion = Mockito.mock(net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion::class.java)
        val channel = Mockito.mock(GuildMessageChannel::class.java, Mockito.RETURNS_DEEP_STUBS)
        `when`(event.channel).thenReturn(channelUnion)
        `when`(channelUnion.asGuildMessageChannel()).thenReturn(channel)
        `when`(channel.name).thenReturn("general")

        // iterable path throws; fallback path used
        `when`(channel.iterableHistory).thenThrow(RuntimeException("iterable not available"))
        @Suppress("UNCHECKED_CAST")
        val rest3 = Mockito.mock(RestAction::class.java) as RestAction<List<Message>>
        `when`(channel.history.retrievePast(Mockito.anyInt())).thenReturn(rest3)
        val list3 = listOf(
            mockMessage("One two three four five six seven eight nine ten.")
        )
        `when`(rest3.complete()).thenReturn(list3)

        val user = Mockito.mock(User::class.java)
        `when`(user.asTag).thenReturn("tester#0001")
        `when`(event.user).thenReturn(user)

        // order too high
        val optOrderHigh = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        `when`(optOrderHigh.asLong).thenReturn(10L)
        `when`(event.getOption("order")).thenReturn(optOrderHigh)

        `when`(hook.sendMessageEmbeds(Mockito.any(MessageEmbed::class.java))).thenReturn(embedAction)

        cmd.execute(event)

        val embCaptor = ArgumentCaptor.forClass(MessageEmbed::class.java)
        Mockito.verify(hook).sendMessageEmbeds(embCaptor.capture())
        assertTrue(embCaptor.value.fields.any { it.name == "Order" && it.value == "3" })
        Mockito.verify(embedAction).queue()
    }

    @Test
    fun `execute catches exception and replies error`() {
        val cmd = Markov()
        val (event, reply, hook) = baseEventMocks()
        @Suppress("UNCHECKED_CAST")
        val msgAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>

        val channelUnion = Mockito.mock(net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion::class.java)
        val channel = Mockito.mock(GuildMessageChannel::class.java, Mockito.RETURNS_DEEP_STUBS)
        `when`(event.channel).thenReturn(channelUnion)
        `when`(channelUnion.asGuildMessageChannel()).thenReturn(channel)

        // Force iterableHistory failure then fallback failure
        `when`(channel.iterableHistory).thenThrow(RuntimeException("iterable fail"))
        @Suppress("UNCHECKED_CAST")
                val rest4 = Mockito.mock(RestAction::class.java) as RestAction<List<Message>>
                `when`(channel.history.retrievePast(Mockito.anyInt())).thenReturn(rest4)
                `when`(rest4.complete()).thenThrow(RuntimeException("fetch fail"))

        `when`(hook.sendMessage(Mockito.anyString())).thenReturn(msgAction)

        cmd.execute(event)

        Mockito.verify(reply).queue()
        val captor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(hook).sendMessage(captor.capture())
        assertEquals("Sorry, there was an error generating the Markov chain.", captor.value)
        Mockito.verify(msgAction).queue()
    }
}
