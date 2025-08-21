package org.j3y.HuskerBot2.commands.other

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import org.j3y.HuskerBot2.commands.SlashCommand
import org.j3y.HuskerBot2.service.UrbanDictionaryService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.awt.Color

@Component
class UrbanDictionary : SlashCommand() {

    @Autowired
    lateinit var urbanService: UrbanDictionaryService

    private val log = LoggerFactory.getLogger(UrbanDictionary::class.java)

    override fun getCommandKey(): String = "ud"
    override fun getDescription(): String = "Search Urban Dictionary for a term"

    override fun getOptions(): List<OptionData> = listOf(
        OptionData(OptionType.STRING, "term", "The term to define", true)
    )

    override fun execute(commandEvent: SlashCommandInteractionEvent) {
        commandEvent.deferReply().queue()
        try {
            val term = commandEvent.getOption("term")?.asString?.trim()
            if (term.isNullOrEmpty()) {
                commandEvent.hook.sendMessage("Please provide a term to search.").queue()
                return
            }

            val defs = urbanService.defineAll(term)
            if (defs.isEmpty()) {
                commandEvent.hook.sendMessage("No results found for '$term'.").queue()
                return
            }

            val userId = commandEvent.user.id
            val index = 0
            val total = defs.size
            val embed = buildEmbed(defs[index], index, total)
            val buttons = buildButtons(term, index, total, userId)

            commandEvent.hook.sendMessageEmbeds(embed).setActionRow(buttons).queue()
        } catch (e: Exception) {
            log.error("Error executing /urban command", e)
            commandEvent.hook.sendMessage("Sorry, there was an error searching Urban Dictionary.").queue()
        }
    }

    override fun buttonEvent(event: ButtonInteractionEvent) {
        val id = event.componentId
        if (!id.startsWith("ud|")) return
        try {
            val parts = id.split("|")
            if (parts.size < 6) return
            val action = parts[1]
            val term = parts[2]
            val index = parts[3].toIntOrNull() ?: 0
            val totalFromId = parts[4].toIntOrNull() ?: 0
            val userId = parts[5]

            if (event.user.id != userId) {
                event.reply("Only the requester can use these buttons.").setEphemeral(true).queue()
                return
            }

            val defs = urbanService.defineAll(term)
            val total = if (defs.isNotEmpty()) defs.size else totalFromId
            if (defs.isEmpty()) {
                event.reply("No more results available.").setEphemeral(true).queue()
                return
            }

            val newIndex = when (action) {
                "first" -> 0
                "prev" -> (index - 1).coerceAtLeast(0)
                "next" -> (index + 1).coerceAtMost(total - 1)
                "last" -> total - 1
                else -> index
            }

            val def = defs[newIndex]

            val embed = net.dv8tion.jda.api.EmbedBuilder()
                .setTitle("Urban Dictionary: ${def.word}", def.permalink)
                .setColor(java.awt.Color(0x1D, 0xA1, 0xF2))
                .addField("Definition", truncate(def.definition, 1024), false)
                .apply {
                    val example = def.example?.takeIf { it.isNotBlank() }
                    if (example != null) {
                        addField("Example", truncate(example, 1024), false)
                    }
                    def.author?.let { addField("Author", it, true) }
                }
                .setFooter("Result ${newIndex + 1} of $total")
                .build()

            val buttons = listOf(
                net.dv8tion.jda.api.interactions.components.buttons.Button.secondary("ud|first|$term|$newIndex|$total|$userId", "⏮ First").withDisabled(newIndex <= 0),
                net.dv8tion.jda.api.interactions.components.buttons.Button.secondary("ud|prev|$term|$newIndex|$total|$userId", "◀ Prev").withDisabled(newIndex <= 0),
                net.dv8tion.jda.api.interactions.components.buttons.Button.secondary("ud|next|$term|$newIndex|$total|$userId", "Next ▶").withDisabled(newIndex >= total - 1),
                net.dv8tion.jda.api.interactions.components.buttons.Button.secondary("ud|last|$term|$newIndex|$total|$userId", "Last ⏭").withDisabled(newIndex >= total - 1),
            )

            event.editMessageEmbeds(embed).setActionRow(buttons).queue()
        } catch (e: Exception) {
            log.error("Error handling UD button interaction", e)
            event.reply("An error occurred processing that action.").setEphemeral(true).queue()
        }
    }

    private fun buildEmbed(def: UrbanDictionaryService.UrbanDefinition, index: Int, total: Int): net.dv8tion.jda.api.entities.MessageEmbed {
        val footer = "Result ${index + 1} of $total"
        return EmbedBuilder()
            .setTitle("Urban Dictionary: ${def.word}", def.permalink)
            .setColor(Color(0x1D, 0xA1, 0xF2))
            .addField("Definition", truncate(def.definition, 1024), false)
            .apply {
                val example = def.example?.takeIf { it.isNotBlank() }
                if (example != null) {
                    addField("Example", truncate(example, 1024), false)
                }
                def.author?.let { addField("Author", it, true) }
            }
            .setFooter(footer)
            .build()
    }

    private fun buildButtons(term: String, index: Int, total: Int, userId: String): List<net.dv8tion.jda.api.interactions.components.buttons.Button> {
        val firstId = "ud|first|$term|$index|$total|$userId"
        val prevId = "ud|prev|$term|$index|$total|$userId"
        val nextId = "ud|next|$term|$index|$total|$userId"
        val lastId = "ud|last|$term|$index|$total|$userId"
        val atStart = index <= 0
        val atEnd = index >= total - 1
        return listOf(
            net.dv8tion.jda.api.interactions.components.buttons.Button.secondary(firstId, "⏮ First").withDisabled(atStart),
            net.dv8tion.jda.api.interactions.components.buttons.Button.secondary(prevId, "◀ Prev").withDisabled(atStart),
            net.dv8tion.jda.api.interactions.components.buttons.Button.secondary(nextId, "Next ▶").withDisabled(atEnd),
            net.dv8tion.jda.api.interactions.components.buttons.Button.secondary(lastId, "Last ⏭").withDisabled(atEnd),
        )
    }



    private fun truncate(text: String, max: Int): String {
        if (text.length <= max) return text
        return text.substring(0, max - 3) + "..."
    }
}
