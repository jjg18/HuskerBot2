package org.j3y.HuskerBot2.commands

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`

class SlashCommandTest {

    private open class TestSubcommand(
        private val key: String,
        private val desc: String = "",
        private val opts: List<OptionData> = emptyList()
    ) : SlashCommand() {
        var executed = false
        var buttonHandled = false

        override fun getCommandKey(): String = key
        override fun getDescription(): String = desc
        override fun getOptions(): List<OptionData> = opts

        override fun execute(commandEvent: SlashCommandInteractionEvent) {
            executed = true
        }

        override fun buttonEvent(buttonEvent: ButtonInteractionEvent) {
            buttonHandled = true
        }
    }

    private open class ParentCommand(private val subs: List<SlashCommand>) : SlashCommand() {
        override fun getSubcommands(): List<SlashCommand> = subs
    }

    @Test
    fun `defaults are sensible`() {
        val base = SlashCommand()
        assertEquals("", base.getCommandKey())
        assertEquals(false, base.isSubcommand())
        assertTrue(base.getSubcommands().isEmpty())
        assertEquals("", base.getDescription())
        assertTrue(base.getOptions().isEmpty())
        assertEquals(DefaultMemberPermissions.ENABLED, base.getPermissions())
    }

    @Test
    fun `execute delegates to matching subcommand by subcommandName`() {
        val a = TestSubcommand("a")
        val b = TestSubcommand("b")
        val parent = ParentCommand(listOf(a, b))

        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        `when`(event.subcommandName).thenReturn("b")

        parent.execute(event)

        assertTrue(!a.executed)
        assertTrue(b.executed)
    }

    @Test
    fun `execute with null subcommandName does nothing`() {
        val a = TestSubcommand("a")
        val parent = ParentCommand(listOf(a))
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        `when`(event.subcommandName).thenReturn(null)

        parent.execute(event)

        assertTrue(!a.executed)
    }

    @Test
    fun `execute with unknown subcommand does nothing`() {
        val a = TestSubcommand("a")
        val parent = ParentCommand(listOf(a))
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        `when`(event.subcommandName).thenReturn("nope")

        parent.execute(event)

        assertTrue(!a.executed)
    }

    @Test
    fun `buttonEvent delegates to subcommand by componentId prefix`() {
        val a = TestSubcommand("alpha")
        val b = TestSubcommand("beta")
        val parent = ParentCommand(listOf(a, b))

        val event = Mockito.mock(ButtonInteractionEvent::class.java)
        `when`(event.componentId).thenReturn("beta|123|rest")

        parent.buttonEvent(event)

        assertTrue(!a.buttonHandled)
        assertTrue(b.buttonHandled)
    }

    @Test
    fun `buttonEvent with non-matching prefix does nothing`() {
        val a = TestSubcommand("alpha")
        val parent = ParentCommand(listOf(a))
        val event = Mockito.mock(ButtonInteractionEvent::class.java)
        `when`(event.componentId).thenReturn("other|42")

        parent.buttonEvent(event)

        assertTrue(!a.buttonHandled)
    }

    @Test
    fun `buttonEvent with bare componentId still splits and compares`() {
        val a = TestSubcommand("alpha")
        val parent = ParentCommand(listOf(a))
        val event = Mockito.mock(ButtonInteractionEvent::class.java)
        `when`(event.componentId).thenReturn("alpha")

        parent.buttonEvent(event)

        assertTrue(a.buttonHandled)
    }

    @Test
    fun `getSubcommandData builds SubcommandData for each subcommand with options`() {
        val sub1 = TestSubcommand(
            key = "first",
            desc = "First command",
            opts = listOf(
                OptionData(OptionType.STRING, "name", "Name option", true),
                OptionData(OptionType.INTEGER, "count", "Count option", false)
            )
        )
        val sub2 = TestSubcommand(
            key = "second",
            desc = "Second command",
            opts = emptyList()
        )
        val parent = ParentCommand(listOf(sub1, sub2))

        val data = parent.getSubcommandData()

        assertEquals(2, data.size)
        assertEquals("first", data[0].name)
        assertEquals("First command", data[0].description)
        // OptionData instances are attached; sizes should match definitions
        assertEquals(2, data[0].options.size)
        assertEquals("second", data[1].name)
        assertEquals("Second command", data[1].description)
        assertEquals(0, data[1].options.size)
    }
}
