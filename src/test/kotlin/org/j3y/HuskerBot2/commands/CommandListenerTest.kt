package org.j3y.HuskerBot2.commands

import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`

class CommandListenerTest {

    private fun buildListenerWithCommands(vararg cmds: SlashCommand): CommandListener {
        val listener = CommandListener()
        val field = CommandListener::class.java.getDeclaredField("allCommands")
        field.isAccessible = true
        field.set(listener, cmds.toList())
        listener.init()
        return listener
    }

    @Test
    fun `onSlashCommandInteraction dispatches to matching command`() {
        val foo = Mockito.mock(SlashCommand::class.java)
        `when`(foo.getCommandKey()).thenReturn("foo")
        val bar = Mockito.mock(SlashCommand::class.java)
        `when`(bar.getCommandKey()).thenReturn("bar")

        val listener = buildListenerWithCommands(foo, bar)

        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val user = Mockito.mock(User::class.java)
        `when`(user.effectiveName).thenReturn("Alice")
        `when`(event.user).thenReturn(user)
        `when`(event.name).thenReturn("foo")
        `when`(event.subcommandName).thenReturn(null)
        `when`(event.options).thenReturn(emptyList())

        listener.onSlashCommandInteraction(event)

        Mockito.verify(foo).execute(event)
        Mockito.verify(bar, Mockito.never()).execute(event)
    }

    @Test
    fun `onSlashCommandInteraction ignores unknown command`() {
        val foo = Mockito.mock(SlashCommand::class.java)
        `when`(foo.getCommandKey()).thenReturn("foo")

        val listener = buildListenerWithCommands(foo)

        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val user = Mockito.mock(User::class.java)
        `when`(user.effectiveName).thenReturn("Bob")
        `when`(event.user).thenReturn(user)
        `when`(event.name).thenReturn("unknown")
        `when`(event.subcommandName).thenReturn(null)
        `when`(event.options).thenReturn(emptyList())

        listener.onSlashCommandInteraction(event)

        Mockito.verify(foo, Mockito.never()).execute(event)
    }

    @Test
    fun `onButtonInteraction dispatches based on componentId prefix`() {
        val bar = Mockito.mock(SlashCommand::class.java)
        `when`(bar.getCommandKey()).thenReturn("bar")
        val listener = buildListenerWithCommands(bar)

        val event = Mockito.mock(ButtonInteractionEvent::class.java)
        `when`(event.componentId).thenReturn("bar|123|extra")

        listener.onButtonInteraction(event)

        Mockito.verify(bar).buttonEvent(event)
    }

    @Test
    fun `onButtonInteraction ignores when no matching command`() {
        val foo = Mockito.mock(SlashCommand::class.java)
        `when`(foo.getCommandKey()).thenReturn("foo")
        val listener = buildListenerWithCommands(foo)

        val event = Mockito.mock(ButtonInteractionEvent::class.java)
        `when`(event.componentId).thenReturn("nope|42")

        listener.onButtonInteraction(event)

        Mockito.verify(foo, Mockito.never()).buttonEvent(event)
    }
}
