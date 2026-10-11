package org.j3y.HuskerBot2.commands.other

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import org.j3y.HuskerBot2.commands.SlashCommand
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.client.RestTemplate
import java.awt.Color
import java.net.URLEncoder
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap

@Component
class Trivia(
    @Value("\${discord.channels.bot-spam}") private val botSpamChannelId: String,
) : SlashCommand() {
    private val log = LoggerFactory.getLogger(Trivia::class.java)
    private val rest = RestTemplate()

    override fun getCommandKey(): String = "trivia"
    override fun getDescription(): String = "Start a multiplayer trivia game (questions from OpenTDB)."

    override fun getOptions(): List<OptionData> {
        val amount = OptionData(OptionType.INTEGER, "amount", "Number of questions (5-10)", false)
        val category = OptionData(OptionType.INTEGER, "category", "OpenTDB category (optional)", false)
        val difficulty = OptionData(OptionType.STRING, "difficulty", "Question difficulty (optional)", false)
            .addChoice("Easy", "easy")
            .addChoice("Medium", "medium")
            .addChoice("Hard", "hard")
        // Populate category choices dynamically from OpenTDB
        val cats = fetchOpenTdbCategories()
        // Discord allows up to 25 choices; OpenTDB currently has ~24 categories
        cats.take(25).forEach { c ->
            category.addChoice(c.name, c.id.toLong())
        }
        return listOf(amount, category, difficulty)
    }

    private val games = ConcurrentHashMap<String, GameState>()

    override fun execute(commandEvent: SlashCommandInteractionEvent) {
        // Enforce usage only in the bot-spam channel
        if (commandEvent.channel.id != botSpamChannelId) {
            val mention = commandEvent.jda.getTextChannelById(botSpamChannelId)?.asMention
            val where = mention ?: "the #bot-spam channel"
            commandEvent.reply("Please use this command in $where.").setEphemeral(true).queue()
            return
        }
        commandEvent.deferReply().queue()

        val userId = commandEvent.user.id
        val channelId = commandEvent.channel.id
        val amountOpt = commandEvent.getOption("amount")?.asInt ?: 5
        val amount = amountOpt.coerceIn(5, 10)
        val categoryId = commandEvent.getOption("category")?.asInt
        val difficulty = commandEvent.getOption("difficulty")?.asString?.lowercase()

        // Build API URL
        val params = mutableListOf("amount=$amount", "type=multiple")
        if (categoryId != null) params += "category=$categoryId"
        if (!difficulty.isNullOrBlank()) params += "difficulty=$difficulty"
        val url = "https://opentdb.com/api.php?${params.joinToString("&")}"

        val resp = try {
            rest.getForObject(url, OpenTdbResponse::class.java)
        } catch (e: Exception) {
            log.warn("Failed to fetch trivia questions", e)
            null
        }

        val results = resp?.results?.filter { it.type == null || it.type == "multiple" } ?: emptyList()
        if (results.isEmpty()) {
            val msg = if (categoryId != null) {
                "Couldn't fetch trivia for category $categoryId. Try a different category or omit it."
            } else "Couldn't fetch trivia questions right now. Please try again later."
            commandEvent.hook.sendMessage(msg).queue()
            return
        }

        val gameId = UUID.randomUUID().toString().substring(0, 8)
        val questions = results.mapIndexed { idx, q -> q.toQuestion(idx + 1) }
        val state = GameState(
            id = gameId,
            hostUserId = userId,
            channelId = channelId,
            created = Instant.now(),
            questions = questions.toMutableList()
        )
        games[gameId] = state

        // Determine readable category name for announcement (if a category was selected)
        val categoryName = if (categoryId != null) questions.firstOrNull()?.category else null

        // Announce and send first question
        commandEvent.hook.sendMessage("Starting trivia with ${questions.size} questions${categoryName?.let { " (category $it)" } ?: ""}! Host can advance with Next.")
            .queue { _ ->
                sendCurrentQuestion(commandEvent, state)
            }
    }

    override fun buttonEvent(buttonEvent: ButtonInteractionEvent) {
        val parts = buttonEvent.componentId.split("|")
        if (parts.size < 3) return
        // structure: trivia|gameId|action|extra
        val action = parts[2]
        val gameId = parts[1]
        val state = games[gameId]
        if (state == null) {
            buttonEvent.reply("This game has ended or no longer exists.").setEphemeral(true).queue()
            return
        }
        when (action) {
            "answer" -> handleAnswer(buttonEvent, state)
            "next" -> handleNext(buttonEvent, state)
            "end" -> handleEnd(buttonEvent, state)
        }
    }

    private fun handleAnswer(event: ButtonInteractionEvent, state: GameState) {
        val parts = event.componentId.split("|")
        val q = state.currentQuestion() ?: run {
            event.reply("No active question.").setEphemeral(true).queue(); return
        }
        val currentQNumber = q.number
        val clickedQNumber = parts.getOrNull(3)?.toIntOrNull()
        // New button format: trivia|gameId|answer|qNumber|choiceIndex
        if (clickedQNumber == null) {
            event.reply("That question has ended. Please answer the current question.").setEphemeral(true).queue()
            return
        }
        if (clickedQNumber != currentQNumber) {
            event.reply("That question has ended. Please answer the current question.").setEphemeral(true).queue()
            return
        }
        val choiceIndex = parts.getOrNull(4)?.toIntOrNull() ?: return
        val userId = event.user.id
        // Record the user's answer for the current question (no changes allowed once answered)
        val answersForQ = state.answersByQuestion.computeIfAbsent(q.number) { ConcurrentHashMap<String, Int>() }
        if (answersForQ.containsKey(userId)) {
            event.reply("You've already answered this question.").setEphemeral(true).queue()
            return
        }
        answersForQ[userId] = choiceIndex

        val correct = choiceIndex == q.correctIndex
        if (correct) state.scores.merge(userId, 1) { a, b -> a + b }

        val response = if (correct) "Correct! ✅" else "Incorrect. ❌ Correct answer: ${q.choices[q.correctIndex]}"
        // Send ephemeral feedback
        event.reply(response).setEphemeral(true).queue()
        // Update the question message to reflect the latest answered count
        try {
            event.message.editMessageEmbeds(buildQuestionEmbed(state)).setComponents(buildAnswerRows(state)).queue()
        } catch (ignored: Exception) {
            // If we cannot edit (e.g., message missing), ignore silently
        }
    }

    private fun handleNext(event: ButtonInteractionEvent, state: GameState) {
        if (event.user.id != state.hostUserId) {
            event.reply("Only the host can advance the game.").setEphemeral(true).queue(); return
        }
        state.index += 1
        val channel = event.channel
        if (state.index >= state.questions.size) {
            // End game
            games.remove(state.id)
            val leaderboard = buildLeaderboard(state)
            channel.sendMessageEmbeds(leaderboard).queue()
            event.deferEdit().queue()
            return
        }
        event.deferEdit().queue() // ack the button press
        // Send next question
        channel.sendMessageEmbeds(buildQuestionEmbed(state)).setComponents(buildAnswerRows(state)).queue()
    }

    private fun handleEnd(event: ButtonInteractionEvent, state: GameState) {
        if (event.user.id != state.hostUserId) {
            event.reply("Only the host can end the game.").setEphemeral(true).queue(); return
        }
        games.remove(state.id)
        val leaderboard = buildLeaderboard(state)
        event.channel.sendMessageEmbeds(leaderboard).queue()
        event.deferEdit().queue()
    }

    private fun sendCurrentQuestion(event: SlashCommandInteractionEvent, state: GameState) {
        state.index = 0
        state.answersSeen.clear()
        event.hook.sendMessageEmbeds(buildQuestionEmbed(state))
            .setComponents(buildAnswerRows(state))
            .queue()
    }

    private fun buildQuestionEmbed(state: GameState): net.dv8tion.jda.api.entities.MessageEmbed {
        val q = state.currentQuestion()!!
        val title = "Trivia Q${q.number}/${state.questions.size}"
        val answeredMentions = state.answersByQuestion[q.number]?.keys?.map { "<@${it}>" }?.joinToString(", ") ?: ""
        val answeredText = if (answeredMentions.isNotBlank()) answeredMentions else "None"
        val eb = EmbedBuilder()
            .setTitle(title)
            .setColor(Color(0x5865F2))
            .setDescription(unescape(q.question))
            .addField("Category", q.category ?: "General", true)
            .addField("Difficulty", (q.difficulty ?: "").replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }, true)
            .addField("Answered", answeredText, true)
            .setFooter("Game ${state.id} • Host can press Next or End", null)
        return eb.build()
    }

    private fun buildAnswerRows(state: GameState): List<ActionRow> {
        val q = state.currentQuestion()!!
        val buttons = q.choices.mapIndexed { idx, label ->
            // Include the question number in the component ID so we can reject late answers on old messages
            Button.primary("${getCommandKey()}|${state.id}|answer|${q.number}|$idx", unescape(label).take(80))
        }.toMutableList()
        val controls = mutableListOf<Button>()
        controls += Button.secondary("${getCommandKey()}|${state.id}|next", "Next")
        controls += Button.danger("${getCommandKey()}|${state.id}|end", "End")
        return listOf(ActionRow.of(buttons), ActionRow.of(controls))
    }

    private fun buildLeaderboard(state: GameState): net.dv8tion.jda.api.entities.MessageEmbed {
        val total = state.questions.size
        val lines = if (state.scores.isEmpty()) listOf("No correct answers this game.") else state.scores.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .mapIndexed { idx, (userId, score) -> "${idx + 1}. <@${userId}> — $score/$total" }
        val eb = EmbedBuilder()
            .setTitle("Trivia Leaderboard")
            .setColor(Color(0x57F287))
            .setDescription(lines.joinToString("\n"))
        return eb.build()
    }

    // ===== Helpers / Models =====

    data class GameState(
        val id: String,
        val hostUserId: String,
        val channelId: String,
        val created: Instant,
        val questions: MutableList<Question>,
        var index: Int = 0,
        val scores: MutableMap<String, Int> = ConcurrentHashMap(),
        val answersSeen: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap()),
        val answersByQuestion: MutableMap<Int, MutableMap<String, Int>> = ConcurrentHashMap()
    ) {
        fun currentQuestion(): Question? = questions.getOrNull(index)
    }

    data class Question(
        val number: Int,
        val category: String?,
        val difficulty: String?,
        val question: String,
        val choices: List<String>,
        val correctIndex: Int
    )

    data class OpenTdbResponse(
        val response_code: Int? = null,
        val results: List<OpenTdbQuestion>? = null
    )

    data class OpenTdbQuestion(
        val category: String? = null,
        val type: String? = null,
        val difficulty: String? = null,
        val question: String = "",
        val correct_answer: String = "",
        val incorrect_answers: List<String> = emptyList()
    ) {
        fun toQuestion(number: Int): Question {
            val choices = (incorrect_answers + correct_answer).shuffled()
            val correctIndex = choices.indexOf(correct_answer)
            return Question(
                number = number,
                category = category,
                difficulty = difficulty,
                question = question,
                choices = choices,
                correctIndex = correctIndex
            )
        }
    }

    private fun unescape(text: String?): String {
        if (text == null) return ""
        return text
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&apos;", "'")
            .replace("&ldquo;", "\"")
            .replace("&rdquo;", "\"")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }
    // ===== OpenTDB Categories Fetch =====
    private fun fetchOpenTdbCategories(): List<OpenTdbCategory> {
        return try {
            val resp = rest.getForObject("https://opentdb.com/api_category.php", OpenTdbCategoryResponse::class.java)
            resp?.trivia_categories ?: emptyList()
        } catch (e: Exception) {
            log.debug("Failed to fetch OpenTDB categories: ${'$'}{e.message}")
            emptyList()
        }
    }

    private data class OpenTdbCategoryResponse(
        val trivia_categories: List<OpenTdbCategory> = emptyList()
    )

    private data class OpenTdbCategory(
        val id: Int = 0,
        val name: String = ""
    )
}
