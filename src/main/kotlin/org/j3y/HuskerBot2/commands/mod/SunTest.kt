package org.j3y.HuskerBot2.commands.mod

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import org.j3y.HuskerBot2.commands.SlashCommand
import org.j3y.HuskerBot2.scheduler.SunriseSunsetScheduler
import org.springframework.stereotype.Component

@Component
class SunTest(
    private val sunriseSunsetScheduler: SunriseSunsetScheduler
) : SlashCommand() {
    override fun getCommandKey(): String = "suntest"
    override fun getDescription(): String = "Manually trigger a sunrise or sunset message for testing."
    override fun getPermissions(): DefaultMemberPermissions = DefaultMemberPermissions.enabledFor(Permission.MESSAGE_MANAGE)

    override fun getOptions(): List<OptionData> = listOf(
        OptionData(OptionType.STRING, "type", "Which message to send", true)
            .addChoice("sunrise", "sunrise")
            .addChoice("sunset", "sunset"),
        OptionData(OptionType.STRING, "channel-id", "Channel id to send the message to (defaults to the configured general channel)", false)
    )

    override fun execute(commandEvent: SlashCommandInteractionEvent) {
        val type = commandEvent.getOption("type")?.asString ?: ""
        val channelIdOption = commandEvent.getOption("channel-id")?.asString

        val channelId = channelIdOption?.toLongOrNull()
        if (channelIdOption != null && channelId == null) {
            commandEvent.reply("Invalid channel-id provided.").setEphemeral(true).queue()
            return
        }

        if (type != "sunrise" && type != "sunset") {
            commandEvent.reply("Invalid type. Use 'sunrise' or 'sunset'.").setEphemeral(true).queue()
            return
        }

        // Acknowledge the interaction immediately since generating the message (Gemini call)
        // can take longer than Discord's 3-second interaction acknowledgement window.
        commandEvent.deferReply().setEphemeral(true).queue()

        Thread {
            try {
                when (type) {
                    "sunrise" -> {
                        if (channelId != null) sunriseSunsetScheduler.sendSunriseMessageNow(channelId) else sunriseSunsetScheduler.sendSunriseMessageNow()
                        commandEvent.hook.sendMessage("Sunrise message triggered!").setEphemeral(true).queue()
                    }
                    "sunset" -> {
                        if (channelId != null) sunriseSunsetScheduler.sendSunsetMessageNow(channelId) else sunriseSunsetScheduler.sendSunsetMessageNow()
                        commandEvent.hook.sendMessage("Sunset message triggered!").setEphemeral(true).queue()
                    }
                }
            } catch (e: Exception) {
                commandEvent.hook.sendMessage("Failed to trigger $type message: ${e.message}").setEphemeral(true).queue()
            }
        }.start()
    }
}
