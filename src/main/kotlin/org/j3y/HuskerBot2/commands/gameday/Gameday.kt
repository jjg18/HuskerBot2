package org.j3y.HuskerBot2.commands.gameday

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import org.j3y.HuskerBot2.commands.SlashCommand
import org.j3y.HuskerBot2.commands.images.AddImage
import org.j3y.HuskerBot2.commands.images.DeleteImage
import org.j3y.HuskerBot2.commands.images.ListImages
import org.j3y.HuskerBot2.commands.images.ShowImage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class Gameday : SlashCommand() {
    @Autowired lateinit var gamedayOn: GamedayOn
    @Autowired lateinit var gamedayOff: GamedayOff

    override fun getCommandKey(): String = "gameday"
    override fun getDescription(): String = "Gameday commands"
    override fun getPermissions(): DefaultMemberPermissions = DefaultMemberPermissions.enabledFor(Permission.MESSAGE_MANAGE)
    override fun getSubcommands(): List<SlashCommand> = listOf(
        gamedayOn, gamedayOff
    )
}
