package org.j3y.HuskerBot2.commands.gameday

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.channel.attribute.IPermissionContainer
import net.dv8tion.jda.api.entities.channel.concrete.Category
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.requests.restaction.PermissionOverrideAction
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`

class GamedayBaseTest {

    private fun mockChannelWithOverride(role: Role): Pair<GuildChannel, PermissionOverrideAction> {
        val channel = Mockito.mock(GuildChannel::class.java)
        val container = Mockito.mock(IPermissionContainer::class.java)
        val action = Mockito.mock(PermissionOverrideAction::class.java, Mockito.RETURNS_SELF)

        `when`(channel.permissionContainer).thenReturn(container)
        `when`(container.upsertPermissionOverride(role)).thenReturn(action)
        // queue() has void return; just stub to no-op
        Mockito.doAnswer { null }.`when`(action).queue()
        return Pair(channel, action)
    }

    @Test
    fun `setGameday throws when executed outside a guild`() {
        val base = GamedayBase()
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        `when`(event.guild).thenReturn(null)
        // set fields to avoid lateinit access (though not used in this branch)
        base.gamedayCategoryId = "gcat"
        base.generalCategoryId = "gen"

        assertThrows(RuntimeException::class.java) {
            base.setGameday(event, true)
        }
    }

    @Test
    fun `setGameday on - denies general send, grants gameday send and view`() {
        val base = GamedayBase()
        base.gamedayCategoryId = "gcat"
        base.generalCategoryId = "gen"

        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val guild = Mockito.mock(Guild::class.java)
        val everyone = Mockito.mock(Role::class.java)
        `when`(event.guild).thenReturn(guild)
        `when`(guild.publicRole).thenReturn(everyone)

        // Categories
        val gamedayCat = Mockito.mock(Category::class.java)
        val generalCat = Mockito.mock(Category::class.java)
        `when`(guild.getCategoryById("gcat")).thenReturn(gamedayCat)
        `when`(guild.getCategoryById("gen")).thenReturn(generalCat)

        // Channels for each category
        val (gamedayCh1, gamedayOv1) = mockChannelWithOverride(everyone)
        val (gamedayCh2, gamedayOv2) = mockChannelWithOverride(everyone)
        val (generalCh1, generalOv1) = mockChannelWithOverride(everyone)
        val (generalCh2, generalOv2) = mockChannelWithOverride(everyone)

        `when`(gamedayCat.channels).thenReturn(listOf(gamedayCh1, gamedayCh2))
        `when`(generalCat.channels).thenReturn(listOf(generalCh1, generalCh2))

        assertDoesNotThrow { base.setGameday(event, true) }

        // Verify general channels denied MESSAGE_SEND
        Mockito.verify(generalOv1).deny(Permission.MESSAGE_SEND)
        Mockito.verify(generalOv2).deny(Permission.MESSAGE_SEND)
        Mockito.verify(generalOv1).queue()
        Mockito.verify(generalOv2).queue()

        // Verify gameday channels granted both perms
        Mockito.verify(gamedayOv1).grant(Permission.MESSAGE_SEND, Permission.VIEW_CHANNEL)
        Mockito.verify(gamedayOv2).grant(Permission.MESSAGE_SEND, Permission.VIEW_CHANNEL)
        Mockito.verify(gamedayOv1).queue()
        Mockito.verify(gamedayOv2).queue()
    }

    @Test
    fun `setGameday off - grants general send, denies gameday send and view`() {
        val base = GamedayBase()
        base.gamedayCategoryId = "gcat"
        base.generalCategoryId = "gen"

        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val guild = Mockito.mock(Guild::class.java)
        val everyone = Mockito.mock(Role::class.java)
        `when`(event.guild).thenReturn(guild)
        `when`(guild.publicRole).thenReturn(everyone)

        val gamedayCat = Mockito.mock(Category::class.java)
        val generalCat = Mockito.mock(Category::class.java)
        `when`(guild.getCategoryById("gcat")).thenReturn(gamedayCat)
        `when`(guild.getCategoryById("gen")).thenReturn(generalCat)

        val (gamedayCh1, gamedayOv1) = mockChannelWithOverride(everyone)
        val (generalCh1, generalOv1) = mockChannelWithOverride(everyone)
        `when`(gamedayCat.channels).thenReturn(listOf(gamedayCh1))
        `when`(generalCat.channels).thenReturn(listOf(generalCh1))

        base.setGameday(event, false)

        // general: grant MESSAGE_SEND
        Mockito.verify(generalOv1).grant(Permission.MESSAGE_SEND)
        Mockito.verify(generalOv1).queue()

        // gameday: deny both
        Mockito.verify(gamedayOv1).deny(Permission.MESSAGE_SEND, Permission.VIEW_CHANNEL)
        Mockito.verify(gamedayOv1).queue()
    }

    @Test
    fun `setGameday handles missing categories by treating as empty lists`() {
        val base = GamedayBase()
        base.gamedayCategoryId = "gcat"
        base.generalCategoryId = "gen"

        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val guild = Mockito.mock(Guild::class.java)
        val everyone = Mockito.mock(Role::class.java)
        `when`(event.guild).thenReturn(guild)
        `when`(guild.publicRole).thenReturn(everyone)

        // Return null categories to exercise null-coalescing to empty lists
        `when`(guild.getCategoryById("gcat")).thenReturn(null)
        `when`(guild.getCategoryById("gen")).thenReturn(null)

        assertDoesNotThrow { base.setGameday(event, true) }

        // Since no categories, no permission overrides should be queued
        // We verify there were no interactions with upsert at all by ensuring no more interactions with guild aside from looked-up methods
        Mockito.verify(guild).getCategoryById("gcat")
        Mockito.verify(guild).getCategoryById("gen")
        Mockito.verify(guild).publicRole
        Mockito.verifyNoMoreInteractions(guild)
    }
}
