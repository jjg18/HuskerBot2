package org.j3y.HuskerBot2.commands.mod

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import org.j3y.HuskerBot2.commands.SlashCommand
import org.j3y.HuskerBot2.model.SocialEmbedReplacementEntity
import org.j3y.HuskerBot2.service.SocialEmbedReplacementService
import org.springframework.stereotype.Component

@Component
class SocialEmbedFixer(
    private val service: SocialEmbedReplacementService,
) : SlashCommand() {

    override fun getCommandKey(): String = "socialembed"
    override fun getDescription(): String = "Manage social embed URL replacement rules."
    override fun getPermissions(): DefaultMemberPermissions = DefaultMemberPermissions.enabledFor(Permission.MESSAGE_MANAGE)
    override fun getSubcommands(): List<SlashCommand> = listOf(
        SocialEmbedList(),
        SocialEmbedAdd(),
        SocialEmbedUpdate(),
        SocialEmbedDelete(),
    )

    // ── list ──────────────────────────────────────────────────────────────────

    inner class SocialEmbedList : SlashCommand() {
        override fun getCommandKey(): String = "list"
        override fun isSubcommand(): Boolean = true
        override fun getDescription(): String = "List all social embed replacement rules."

        override fun execute(commandEvent: SlashCommandInteractionEvent) {
            val rules = service.getRules()
            if (rules.isEmpty()) {
                commandEvent.reply("No rules configured.").setEphemeral(true).queue()
                return
            }
            val sb = StringBuilder("**Social Embed Replacement Rules:**\n```\n")
            rules.forEach { r ->
                sb.append("ID=${r.id}  order=${r.sortOrder}  strategy=${r.strategy}\n")
                sb.append("  label      : ${r.label}\n")
                sb.append("  matchPattern: ${r.matchPattern}\n")
                if (r.strategy.uppercase() != "SKIP") {
                    if (r.replaceRegex.isNotBlank()) sb.append("  replaceRegex: ${r.replaceRegex}\n")
                    sb.append("  replaceWith : ${r.replaceWith}\n")
                }
                sb.append("\n")
            }
            sb.append("```")
            commandEvent.reply(sb.toString()).setEphemeral(true).queue()
        }
    }

    // ── add ───────────────────────────────────────────────────────────────────

    inner class SocialEmbedAdd : SlashCommand() {
        override fun getCommandKey(): String = "add"
        override fun isSubcommand(): Boolean = true
        override fun getDescription(): String = "Add a new social embed replacement rule."
        override fun getOptions(): List<OptionData> = listOf(
            OptionData(OptionType.STRING, "label", "Human-readable label for this rule.", true),
            OptionData(OptionType.STRING, "match-pattern", "Substring to match in the lowercased URL.", true),
            OptionData(
                OptionType.STRING, "strategy",
                "SKIP = ignore URL, REGEX = regex replace, WRAP = prepend prefix to encoded URL.", true
            ).addChoice("SKIP", "SKIP").addChoice("REGEX", "REGEX").addChoice("WRAP", "WRAP"),
            OptionData(OptionType.STRING, "replace-with", "Replacement string or URL prefix (not needed for SKIP).", false),
            OptionData(OptionType.STRING, "replace-regex", "Regex pattern to apply to the URL (REGEX strategy only).", false),
            OptionData(OptionType.INTEGER, "sort-order", "Evaluation order — lower runs first (default 100).", false),
        )

        override fun execute(commandEvent: SlashCommandInteractionEvent) {
            val label = commandEvent.getOption("label")!!.asString
            val matchPattern = commandEvent.getOption("match-pattern")!!.asString
            val strategy = commandEvent.getOption("strategy")!!.asString.uppercase()
            val replaceWith = commandEvent.getOption("replace-with")?.asString ?: ""
            val replaceRegex = commandEvent.getOption("replace-regex")?.asString ?: ""
            val sortOrder = commandEvent.getOption("sort-order")?.asInt ?: 100

            if (strategy == "REGEX" && replaceRegex.isBlank()) {
                commandEvent.reply("REGEX strategy requires a `replace-regex` value.").setEphemeral(true).queue()
                return
            }
            if ((strategy == "REGEX" || strategy == "WRAP") && replaceWith.isBlank()) {
                commandEvent.reply("REGEX and WRAP strategies require a `replace-with` value.").setEphemeral(true).queue()
                return
            }

            val entity = SocialEmbedReplacementEntity(
                label = label,
                matchPattern = matchPattern,
                strategy = strategy,
                replaceWith = replaceWith,
                replaceRegex = replaceRegex,
                sortOrder = sortOrder,
            )
            val saved = service.addRule(entity)
            commandEvent.reply("✅ Rule added with ID **${saved.id}**: `$label`").setEphemeral(true).queue()
        }
    }

    // ── update ────────────────────────────────────────────────────────────────

    inner class SocialEmbedUpdate : SlashCommand() {
        override fun getCommandKey(): String = "update"
        override fun isSubcommand(): Boolean = true
        override fun getDescription(): String = "Update an existing social embed replacement rule by ID."
        override fun getOptions(): List<OptionData> = listOf(
            OptionData(OptionType.INTEGER, "id", "ID of the rule to update.", true),
            OptionData(OptionType.STRING, "label", "New human-readable label.", false),
            OptionData(OptionType.STRING, "match-pattern", "New substring to match in the lowercased URL.", false),
            OptionData(
                OptionType.STRING, "strategy",
                "SKIP = ignore URL, REGEX = regex replace, WRAP = prepend prefix to encoded URL.", false
            ).addChoice("SKIP", "SKIP").addChoice("REGEX", "REGEX").addChoice("WRAP", "WRAP"),
            OptionData(OptionType.STRING, "replace-with", "New replacement string or URL prefix.", false),
            OptionData(OptionType.STRING, "replace-regex", "New regex pattern (REGEX strategy only).", false),
            OptionData(OptionType.INTEGER, "sort-order", "New evaluation order.", false),
        )

        override fun execute(commandEvent: SlashCommandInteractionEvent) {
            val id = commandEvent.getOption("id")!!.asLong
            val existing = service.getRules().find { it.id == id }
            if (existing == null) {
                commandEvent.reply("❌ No rule found with ID **$id**.").setEphemeral(true).queue()
                return
            }

            val updated = SocialEmbedReplacementEntity(
                id = existing.id,
                label = commandEvent.getOption("label")?.asString ?: existing.label,
                matchPattern = commandEvent.getOption("match-pattern")?.asString ?: existing.matchPattern,
                strategy = commandEvent.getOption("strategy")?.asString?.uppercase() ?: existing.strategy,
                replaceWith = commandEvent.getOption("replace-with")?.asString ?: existing.replaceWith,
                replaceRegex = commandEvent.getOption("replace-regex")?.asString ?: existing.replaceRegex,
                sortOrder = commandEvent.getOption("sort-order")?.asInt ?: existing.sortOrder,
            )

            service.updateRule(id, updated)
            commandEvent.reply("✅ Rule **$id** updated.").setEphemeral(true).queue()
        }
    }

    // ── delete ────────────────────────────────────────────────────────────────

    inner class SocialEmbedDelete : SlashCommand() {
        override fun getCommandKey(): String = "delete"
        override fun isSubcommand(): Boolean = true
        override fun getDescription(): String = "Delete a social embed replacement rule by ID."
        override fun getOptions(): List<OptionData> = listOf(
            OptionData(OptionType.INTEGER, "id", "ID of the rule to delete.", true),
        )

        override fun execute(commandEvent: SlashCommandInteractionEvent) {
            val id = commandEvent.getOption("id")!!.asLong
            if (service.deleteRule(id)) {
                commandEvent.reply("✅ Rule **$id** deleted.").setEphemeral(true).queue()
            } else {
                commandEvent.reply("❌ No rule found with ID **$id**.").setEphemeral(true).queue()
            }
        }
    }
}
