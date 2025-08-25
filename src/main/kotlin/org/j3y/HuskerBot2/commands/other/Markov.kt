package org.j3y.HuskerBot2.commands.other

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel
import org.j3y.HuskerBot2.commands.SlashCommand
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.awt.Color
import java.time.OffsetDateTime
import kotlin.random.Random

@Component
class Markov : SlashCommand() {
    private val log = LoggerFactory.getLogger(Markov::class.java)

    override fun getCommandKey(): String = "markov"
    override fun getDescription(): String = "Generate text from recent channel messages using a Markov chain"

    override fun getOptions(): List<OptionData> = listOf(
        OptionData(OptionType.INTEGER, "messages", "How many recent messages to scan (10-1000)", false),
        OptionData(OptionType.INTEGER, "order", "Markov order (1-3), default 2", false),
        OptionData(OptionType.STRING, "seed", "Optional starting word(s)", false)
    )

    override fun execute(commandEvent: SlashCommandInteractionEvent) {
        commandEvent.deferReply().queue()
        try {
            val channel = try {
                commandEvent.channel.asGuildMessageChannel()
            } catch (e: Exception) {
                commandEvent.hook.sendMessage("This command can only be used in guild text channels.").queue()
                return
            }

            val requestedMessages = (commandEvent.getOption("messages")?.asLong ?: 200L).toInt()
            val limit = requestedMessages.coerceIn(10, 1000)
            val order = ((commandEvent.getOption("order")?.asLong ?: 2L).toInt()).coerceIn(1, 3)
            val seed = commandEvent.getOption("seed")?.asString?.trim()?.takeIf { it.isNotEmpty() }

            // Fetch messages in pages of up to 100
            val messages = fetchRecentMessages(channel, limit)
                .filter { shouldIncludeMessage(it) }

            val corpus = messages.joinToString("\n") { it.contentStripped }
            if (corpus.isBlank()) {
                commandEvent.hook.sendMessage("I couldn't find enough usable text in recent messages.").queue()
                return
            }

            val generator = MarkovChain(order)
            generator.train(corpus)

            val generated = generator.generate(seed = seed, maxWords = 120)
                .ifBlank { generator.generate(maxWords = 60) }
                .take(3900) // keep room for extra embed content

            val embed = EmbedBuilder()
                .setTitle("Markov Chain")
                .setColor(Color(0xD9, 0x2D, 0x20))
                .setDescription(generated)
                .addField("Source", "#${channel.name}", true)
                .addField("Messages scanned", messages.size.toString(), true)
                .addField("Order", order.toString(), true)
                .setFooter("Requested by ${commandEvent.user.asTag}")
                .setTimestamp(OffsetDateTime.now())
                .build()

            commandEvent.hook.sendMessageEmbeds(embed).queue()
        } catch (e: Exception) {
            log.error("Error executing /markov command", e)
            commandEvent.hook.sendMessage("Sorry, there was an error generating the Markov chain.").queue()
        }
    }

    private fun fetchRecentMessages(channel: GuildMessageChannel, limit: Int): List<Message> {
        return try {
            channel.iterableHistory
                .cache(false)
                .takeAsync(limit)
                .get()
        } catch (e: Exception) {
            // Fallback to single request (up to 100)
            channel.history.retrievePast(limit.coerceAtMost(100)).complete()
        }
    }

    private fun shouldIncludeMessage(message: Message): Boolean {
        if (message.author.isBot) return false
        val content = message.contentRaw.trim()
        if (content.isBlank()) return false
        if (content.startsWith("/")) return false // slash command echo or text
        // Remove URLs-only messages
        val noUrls = content.replace(Regex("https?://\\S+"), "").trim()
        if (noUrls.isBlank()) return false
        return true
    }
}

private class MarkovChain(private val order: Int = 2) {
    // Map of prefix (list of words) to list of possible next words
    private val transitions: MutableMap<List<String>, MutableList<String>> = HashMap()
    private val starters: MutableList<List<String>> = mutableListOf()

    fun train(text: String) {
        val tokens = tokenize(text)
        if (tokens.size <= order) return

        for (i in 0..tokens.size - order - 1) {
            val prefix = tokens.subList(i, i + order)
            val next = tokens[i + order]
            transitions.computeIfAbsent(prefix) { mutableListOf() }.add(next)
            if (i == 0 || tokens[i - 1].endsWith(".")) {
                starters.add(prefix)
            }
        }
        if (starters.isEmpty()) {
            // Fallback: any prefix can be a starter
            starters.addAll(transitions.keys)
        }
    }

    fun generate(seed: String? = null, maxWords: Int = 100): String {
        if (transitions.isEmpty()) return ""

        val rnd = Random(System.currentTimeMillis())
        var current: MutableList<String> = when {
            seed != null -> seedToPrefix(seed)
            starters.isNotEmpty() -> starters.random(rnd).toMutableList()
            else -> transitions.keys.random(rnd).toMutableList()
        }

        val out = mutableListOf<String>()
        out.addAll(current)

        for (i in 0 until maxWords.coerceAtLeast(1)) {
            val nexts = transitions[current] ?: break
            val next = nexts.random(rnd)
            out.add(next)
            current = (current.drop(1) + next).toMutableList()
            // stop if we end a sentence and have enough words
            if (out.size >= (maxWords / 2) && next.endsWith('.') && rnd.nextDouble() < 0.35) break
        }

        // Clean up spacing
        return postProcess(out.joinToString(" "))
    }

    private fun seedToPrefix(seed: String): MutableList<String> {
        val seedTokens = tokenize(seed).takeLast(order)
        val key = transitions.keys.firstOrNull { it.endsWith(seedTokens) }
        return (key ?: transitions.keys.first()).toMutableList()
    }

    private fun List<String>.endsWith(suffix: List<String>): Boolean {
        if (suffix.isEmpty() || suffix.size > this.size) return false
        return this.takeLast(suffix.size) == suffix
    }

    private fun tokenize(text: String): List<String> {
        // Remove URLs and mentions, collapse whitespace, keep punctuation as part of tokens
        val cleaned = text
            .replace(Regex("https?://\\S+"), " ")
            .replace(Regex("<@!?\\d+>"), " ") // mentions
            .replace(Regex("<#\\d+>"), " ") // channel mentions
            .replace(Regex("&lt;.*?&gt;"), " ")
        return cleaned
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun postProcess(text: String): String {
        // Ensure capitalization at start and fix spaces before punctuation
        var t = text
            .replace(Regex("\\s+([,.;:!?])"), "$1")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (t.isNotEmpty()) {
            t = t[0].uppercaseChar() + t.substring(1)
        }
        return t
    }
}
