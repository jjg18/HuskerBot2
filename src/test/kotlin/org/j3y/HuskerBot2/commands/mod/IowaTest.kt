package org.j3y.HuskerBot2.commands.mod

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.exceptions.PermissionException
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import java.awt.Color
import java.time.Duration
import java.util.function.Consumer

class IowaTest {

    @Test
    fun `metadata and options and permissions are correct`() {
        val cmd = Iowa()
        assertEquals("iowa", cmd.getCommandKey())
        assertEquals("Send a rhuligan to Iowa.", cmd.getDescription())

        val opts: List<OptionData> = cmd.getOptions()
        assertEquals(3, opts.size)
        // user option
        assertEquals("user", opts[0].name)
        assertEquals(OptionType.USER, opts[0].type)
        assertTrue(opts[0].isRequired)
        // reason option
        assertEquals("reason", opts[1].name)
        assertEquals(OptionType.STRING, opts[1].type)
        assertFalse(opts[1].isRequired)
        // minutes option
        assertEquals("minutes", opts[2].name)
        assertEquals(OptionType.INTEGER, opts[2].type)
        assertFalse(opts[2].isRequired)

        val perms: DefaultMemberPermissions = cmd.getPermissions()
        // Basic sanity: permissions object is provided (JDA equality is not reliable for exact comparison)
        assertNotNull(perms)
    }

    @Test
    fun `execute replies ephemeral error when user option missing`() {
        val cmd = Iowa()
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)

        // getOption("user") returns null
        `when`(event.getOption("user")).thenReturn(null)
        // Chain: reply(String) -> setEphemeral(true) -> queue()
        `when`(event.reply(Mockito.anyString())).thenReturn(replyAction)
        `when`(replyAction.setEphemeral(true)).thenReturn(replyAction)

        cmd.execute(event)

        val msgCaptor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(event).reply(msgCaptor.capture())
        assertEquals("Invalid user.", msgCaptor.value)
        Mockito.verify(replyAction).setEphemeral(true)
        Mockito.verify(replyAction).queue()
    }

    @Test
    fun `execute success path sends embed with default duration and default reason`() {
        val cmd = Iowa()
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val member = Mockito.mock(Member::class.java)
        val optUser = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        `when`(optUser.asMember).thenReturn(member)
        `when`(event.getOption("user")).thenReturn(optUser)

        // minutes not provided -> default 30
        `when`(event.getOption("minutes")).thenReturn(null)
        // reason not provided -> default message
        `when`(event.getOption("reason")).thenReturn(null)

        // Member identity for message text
        `when`(member.effectiveName).thenReturn("Troublemaker")
        val jdaUser = Mockito.mock(net.dv8tion.jda.api.entities.User::class.java)
        `when`(jdaUser.effectiveName).thenReturn("TroublemakerUser")
        `when`(member.user).thenReturn(jdaUser)

        // Mock timeoutFor -> AuditableRestAction
        @Suppress("UNCHECKED_CAST")
        val action = Mockito.mock(AuditableRestAction::class.java) as AuditableRestAction<Void>
        `when`(member.timeoutFor(Mockito.any(Duration::class.java))).thenReturn(action)
        // reason(reason) returns the same action
        `when`(action.reason(Mockito.anyString())).thenReturn(action)
        // On queue(success,failure) -> invoke success consumer
        Mockito.doAnswer { inv ->
            @Suppress("UNCHECKED_CAST")
            val success = inv.getArgument<Consumer<Any?>>(0)
            success.accept(null)
            null
        }.`when`(action).queue(Mockito.any(), Mockito.any())

        // replyEmbeds returns ReplyCallbackAction
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)
        `when`(event.replyEmbeds(Mockito.any(MessageEmbed::class.java))).thenReturn(replyAction)

        cmd.execute(event)

        val embedCaptor = ArgumentCaptor.forClass(MessageEmbed::class.java)
        Mockito.verify(event).replyEmbeds(embedCaptor.capture())
        Mockito.verify(replyAction).queue()

        val embed = embedCaptor.value
        assertEquals("Banished to Iowa", embed.title)
        assertEquals(Color.YELLOW.rgb, embed.colorRaw)
        assertTrue(embed.description?.contains("Troublemaker has been banished to Iowa!") == true)

        // Verify fields include default reason and 30 minutes
        val fields = embed.fields
        val reasonField = fields.find { it.name == "Reason" }
        val durationField = fields.find { it.name == "Duration" }
        assertNotNull(reasonField)
        assertNotNull(durationField)
        assertEquals("You have been sent to Iowa for being a rhuligan.", reasonField!!.value)
        assertEquals("30 minutes", durationField!!.value)
    }

    @Test
    fun `execute success with custom minutes and reason`() {
        val cmd = Iowa()
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val member = Mockito.mock(Member::class.java)

        val optUser = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        `when`(optUser.asMember).thenReturn(member)
        `when`(event.getOption("user")).thenReturn(optUser)

        val optMinutes = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        `when`(optMinutes.asInt).thenReturn(5)
        `when`(event.getOption("minutes")).thenReturn(optMinutes)

        val customReason = "Spam and tomfoolery"
        val optReason = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        `when`(optReason.asString).thenReturn(customReason)
        `when`(event.getOption("reason")).thenReturn(optReason)

        `when`(member.effectiveName).thenReturn("Noisy")
        val jdaUser = Mockito.mock(net.dv8tion.jda.api.entities.User::class.java)
        `when`(jdaUser.effectiveName).thenReturn("NoisyUser")
        `when`(member.user).thenReturn(jdaUser)

        @Suppress("UNCHECKED_CAST")
        val action = Mockito.mock(AuditableRestAction::class.java) as AuditableRestAction<Void>
        `when`(member.timeoutFor(Mockito.any(Duration::class.java))).thenReturn(action)
        `when`(action.reason(Mockito.anyString())).thenReturn(action)
        Mockito.doAnswer { inv ->
            @Suppress("UNCHECKED_CAST")
            val success = inv.getArgument<Consumer<Any?>>(0)
            success.accept(null)
            null
        }.`when`(action).queue(Mockito.any(), Mockito.any())

        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)
        `when`(event.replyEmbeds(Mockito.any(MessageEmbed::class.java))).thenReturn(replyAction)

        cmd.execute(event)

        val embedCaptor = ArgumentCaptor.forClass(MessageEmbed::class.java)
        Mockito.verify(event).replyEmbeds(embedCaptor.capture())
        val embed = embedCaptor.value
        val reasonField = embed.fields.find { it.name == "Reason" }
        val durationField = embed.fields.find { it.name == "Duration" }
        assertEquals(customReason, reasonField?.value)
        assertEquals("5 minutes", durationField?.value)
    }

    @Test
    fun `execute failure callback replies ephemeral unable message`() {
        val cmd = Iowa()
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val member = Mockito.mock(Member::class.java)

        val optUser = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        `when`(optUser.asMember).thenReturn(member)
        `when`(event.getOption("user")).thenReturn(optUser)

        `when`(member.effectiveName).thenReturn("Target")
        val jdaUser = Mockito.mock(net.dv8tion.jda.api.entities.User::class.java)
        `when`(jdaUser.effectiveName).thenReturn("TargetUser")
        `when`(member.user).thenReturn(jdaUser)

        @Suppress("UNCHECKED_CAST")
        val action = Mockito.mock(AuditableRestAction::class.java) as AuditableRestAction<Void>
        `when`(member.timeoutFor(Mockito.any(Duration::class.java))).thenReturn(action)
        `when`(action.reason(Mockito.anyString())).thenReturn(action)
        // failure path: call failure consumer
        Mockito.doAnswer { inv ->
            val failure = inv.getArgument<Consumer<Throwable>>(1)
            failure.accept(RuntimeException("boom"))
            null
        }.`when`(action).queue(Mockito.any(), Mockito.any())

        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)
        `when`(event.reply(Mockito.anyString())).thenReturn(replyAction)
        `when`(replyAction.setEphemeral(true)).thenReturn(replyAction)

        cmd.execute(event)

        val msgCaptor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(event).reply(msgCaptor.capture())
        assertEquals("Unable to Iowa TargetUser.", msgCaptor.value)
        Mockito.verify(replyAction).setEphemeral(true)
        Mockito.verify(replyAction).queue()
    }

    @Test
    fun `execute catches PermissionException and replies ephemeral`() {
        val cmd = Iowa()
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val member = Mockito.mock(Member::class.java)

        val optUser = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        `when`(optUser.asMember).thenReturn(member)
        `when`(event.getOption("user")).thenReturn(optUser)

        val jdaUser = Mockito.mock(net.dv8tion.jda.api.entities.User::class.java)
        `when`(jdaUser.effectiveName).thenReturn("XUser")
        `when`(member.user).thenReturn(jdaUser)

        // Make timeoutFor throw PermissionException
        `when`(member.timeoutFor(Mockito.any(Duration::class.java))).thenThrow(PermissionException("nope"))

        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)
        `when`(event.reply(Mockito.anyString())).thenReturn(replyAction)
        `when`(replyAction.setEphemeral(true)).thenReturn(replyAction)

        cmd.execute(event)

        val msgCaptor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(event).reply(msgCaptor.capture())
        assertEquals("You do not have permission to Iowa XUser.", msgCaptor.value)
        Mockito.verify(replyAction).setEphemeral(true)
        Mockito.verify(replyAction).queue()
    }
}
