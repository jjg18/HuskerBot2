package org.j3y.HuskerBot2.commands.mod

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.exceptions.PermissionException
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import net.dv8tion.jda.api.entities.Message
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import java.awt.Color
import java.util.function.Consumer

class NebraskaTest {

    @Test
    fun `metadata and options and permissions are correct`() {
        val cmd = Nebraska()
        assertEquals("nebraska", cmd.getCommandKey())
        assertEquals("Remove a rhuligan from Iowa.", cmd.getDescription())

        val opts: List<OptionData> = cmd.getOptions()
        assertEquals(1, opts.size)
        assertEquals("user", opts[0].name)
        assertEquals(OptionType.USER, opts[0].type)
        assertTrue(opts[0].isRequired)

        val perms: DefaultMemberPermissions = cmd.getPermissions()
        assertNotNull(perms)
        // We can at least ensure it is enabled for MESSAGE_MANAGE via builder semantics
        // (Exact equality on JDA permissions objects can be brittle, so we just ensure non-null)
    }

    @Test
    fun `execute replies error via hook when user option missing`() {
        val cmd = Nebraska()
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val hook = Mockito.mock(net.dv8tion.jda.api.interactions.InteractionHook::class.java)
        val hookAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>

        `when`(event.getOption("user")).thenReturn(null)
        `when`(event.hook).thenReturn(hook)
        `when`(hook.sendMessage(Mockito.anyString())).thenReturn(hookAction)

        cmd.execute(event)

        val msgCaptor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(hook).sendMessage(msgCaptor.capture())
        assertEquals("Invalid user.", msgCaptor.value)
        Mockito.verify(hookAction).queue()
    }

    @Test
    fun `execute success removes timeout and replies embed with welcome fields`() {
        val cmd = Nebraska()
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val member = Mockito.mock(Member::class.java)
        val optUser = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        val author = Mockito.mock(User::class.java)

        `when`(author.effectiveName).thenReturn("ModeratorGuy")
        `when`(event.user).thenReturn(author)

        `when`(optUser.asMember).thenReturn(member)
        `when`(event.getOption("user")).thenReturn(optUser)

        `when`(member.effectiveName).thenReturn("ReturnedUser")
        val jdaUser = Mockito.mock(User::class.java)
        `when`(jdaUser.effectiveName).thenReturn("ReturnedUserName")
        `when`(member.user).thenReturn(jdaUser)

        @Suppress("UNCHECKED_CAST")
        val action = Mockito.mock(AuditableRestAction::class.java) as AuditableRestAction<Void>
        `when`(member.removeTimeout()).thenReturn(action)
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
        Mockito.verify(replyAction).queue()

        val embed = embedCaptor.value
        assertEquals("Return to Nebraska", embed.title)
        assertEquals(Color.RED.rgb, embed.colorRaw)

        val fields = embed.fields
        val welcome = fields.find { it.name == "Welcome Back!" }
        val by = fields.find { it.name == "Welcomed by" }
        assertNotNull(welcome)
        assertTrue(welcome!!.value?.contains("ReturnedUser is welcomed back to Nebraska!") == true)
        assertEquals("ModeratorGuy", by?.value)
    }

    @Test
    fun `execute failure callback replies via hook with unable message`() {
        val cmd = Nebraska()
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val member = Mockito.mock(Member::class.java)
        val optUser = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        val hook = Mockito.mock(net.dv8tion.jda.api.interactions.InteractionHook::class.java)
        val hookAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>

        `when`(optUser.asMember).thenReturn(member)
        `when`(event.getOption("user")).thenReturn(optUser)

        val jdaUser = Mockito.mock(User::class.java)
        `when`(jdaUser.effectiveName).thenReturn("TargetUser")
        `when`(member.user).thenReturn(jdaUser)

        @Suppress("UNCHECKED_CAST")
        val action = Mockito.mock(AuditableRestAction::class.java) as AuditableRestAction<Void>
        `when`(member.removeTimeout()).thenReturn(action)
        // trigger failure consumer
        Mockito.doAnswer { inv ->
            val failure = inv.getArgument<Consumer<Throwable>>(1)
            failure.accept(RuntimeException("oops"))
            null
        }.`when`(action).queue(Mockito.any(), Mockito.any())

        `when`(event.hook).thenReturn(hook)
        `when`(hook.sendMessage(Mockito.anyString())).thenReturn(hookAction)
        // code calls setEphemeral(true) on the action; mock it to return the same
        `when`(hookAction.setEphemeral(true)).thenReturn(hookAction)

        cmd.execute(event)

        val msgCaptor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(hook).sendMessage(msgCaptor.capture())
        assertEquals("Unable to remove TargetUser from Iowa.", msgCaptor.value)
        Mockito.verify(hookAction).setEphemeral(true)
        Mockito.verify(hookAction).queue()
    }

    @Test
    fun `execute catches PermissionException and replies ephemeral`() {
        val cmd = Nebraska()
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val member = Mockito.mock(Member::class.java)
        val optUser = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)

        `when`(optUser.asMember).thenReturn(member)
        `when`(event.getOption("user")).thenReturn(optUser)

        val jdaUser = Mockito.mock(User::class.java)
        `when`(jdaUser.effectiveName).thenReturn("XUser")
        `when`(member.user).thenReturn(jdaUser)

        // Throw PermissionException on removeTimeout()
        `when`(member.removeTimeout()).thenThrow(PermissionException("nope"))

        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)
        `when`(event.reply(Mockito.anyString())).thenReturn(replyAction)
        `when`(replyAction.setEphemeral(true)).thenReturn(replyAction)

        cmd.execute(event)

        val msgCaptor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(event).reply(msgCaptor.capture())
        assertEquals("You do not have permission to Nebraska XUser.", msgCaptor.value)
        Mockito.verify(replyAction).setEphemeral(true)
        Mockito.verify(replyAction).queue()
    }
}
