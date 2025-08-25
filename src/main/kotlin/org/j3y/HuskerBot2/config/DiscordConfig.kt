package org.j3y.HuskerBot2.config

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.MemberCachePolicy
import org.j3y.HuskerBot2.commands.CommandListener
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.j3y.HuskerBot2.commands.SlashCommand
import org.j3y.HuskerBot2.service.DefaultEspnService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import java.util.EnumSet

@Configuration
class DiscordConfig {

    private final val log = LoggerFactory.getLogger(DefaultEspnService::class.java)


    @Component
    @ConfigurationProperties("discord.commands")
    class CommandGuilds {
        lateinit var guilds: List<String>
    }

    @Bean
    fun getDiscordClient(
        @Value("\${discord.token}") token: String,
        slashCommands: Array<SlashCommand>,
        listeners: Array<ListenerAdapter>,
    ): JDA {
        val jda = JDABuilder.createLight(token, EnumSet.of(
                GatewayIntent.MESSAGE_CONTENT,
                GatewayIntent.GUILD_MESSAGES,
                GatewayIntent.GUILD_MEMBERS,
                GatewayIntent.GUILD_EXPRESSIONS,
                GatewayIntent.GUILD_MESSAGE_REACTIONS
            ))
            .addEventListeners(*listeners)
            .setMemberCachePolicy(MemberCachePolicy.ALL)
            .build()

        jda.awaitReady().guilds.forEach { guild ->
            log.info("Registering slash commands for guild: ${guild.name}")
            val commands = guild.updateCommands()
            commands.addCommands(
                slashCommands
                    .filter({ command -> !command.isSubcommand()})
                    .map {
                        Commands.slash(it.getCommandKey(), it.getDescription())
                            .addOptions(it.getOptions())
                            .setDefaultPermissions(it.getPermissions())
                            .addSubcommands(it.getSubcommandData())
                    }
            ).queue()
        }

        return jda
    }

}