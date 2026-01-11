package org.j3y.HuskerBot2.commands.other

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.utils.FileUpload
import org.j3y.HuskerBot2.commands.SlashCommand
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

@Component
class OsrsStats : SlashCommand() {

    private val client = RestTemplate()

    private val skills = listOf(
        "Attack", "Defence", "Strength", "HP", "Ranged", "Prayer", "Magic", "Cooking",
        "Woodcutting", "Fletching", "Fishing", "Firemaking", "Crafting", "Smithing", "Mining",
        "Herblore", "Agility", "Thieving", "Slayer", "Farming", "Runecrafting", "Hunter", "Construction",
        "Sailing"
    )

    override fun getCommandKey(): String = "osrs"
    override fun getDescription(): String = "Get the high scores and XP for a OSRS player"
    override fun getOptions(): List<OptionData> = listOf(
        OptionData(OptionType.STRING, "player", "The player username you'd like to retrieve stats for", true),
    )

    override fun execute(commandEvent: SlashCommandInteractionEvent) {
        commandEvent.deferReply().queue()

        val player = commandEvent.getOption("player")?.asString ?: ""

        val url = UriComponentsBuilder
            .fromUriString("https://secure.runescape.com/m=hiscore_oldschool/index_lite.ws")
            .queryParam("player", player)
            .build().toUri()

        val response: String
        try {
            response = client.getForObject(url, String::class.java) ?: ""
        } catch (hsce: RestClientException) {
            commandEvent.hook.sendMessage("High scores were not found for player: $player").queue()
            return
        }

        val respTokens = response.split("\n").filter { it.isNotBlank() }

        val image = generateStatsImage(player, respTokens)
        val baos = ByteArrayOutputStream()
        ImageIO.write(image, "png", baos)
        val imageBytes = baos.toByteArray()

        commandEvent.hook.sendFiles(FileUpload.fromData(imageBytes, "osrs_stats.png")).queue()
    }

    private fun generateStatsImage(player: String, respTokens: List<String>): BufferedImage {
        val cellWidth = 200
        val cellHeight = 50
        val columns = 3
        val rows = 8
        val headerHeight = 60
        val width = cellWidth * columns
        val height = cellHeight * rows + headerHeight

        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        // Header
        g.color = Color.ORANGE
        g.font = Font("Arial", Font.BOLD, 24)
        g.drawString("OSRS Stats: $player", 20, 40)

        // Draw Overall in header (top right)
        if (respTokens.isNotEmpty()) {
            val overallStats = respTokens[0].split(',')
            val overallLvl = overallStats.getOrNull(1)?.toLongOrNull() ?: 0L
            val overallXp = overallStats.getOrNull(2)?.toLongOrNull() ?: 0L

            val headerX = width - 180
            val headerY = 5

            // Icon
            val iconPath = "/images/osrs/skills/overall.png"
            val iconStream = javaClass.getResourceAsStream(iconPath)
            if (iconStream != null) {
                val icon = ImageIO.read(iconStream)
                g.drawImage(icon, headerX, headerY + 10, 32, 32, null)
            }

            g.color = Color.WHITE
            g.font = Font("Arial", Font.BOLD, 14)
            g.drawString("Overall", headerX + 40, headerY + 20)

            g.color = Color.YELLOW
            g.font = Font("Arial", Font.BOLD, 12)
            g.drawString("Lvl: $overallLvl", headerX + 40, headerY + 35)

            g.color = Color.LIGHT_GRAY
            g.font = Font("Arial", Font.PLAIN, 10)
            val xpFormatted = String.format("%,d XP", overallXp)
            g.drawString(xpFormatted, headerX + 40, headerY + 48)
        }

        // Skills grid
        skills.forEachIndexed { i, skill ->
            val tokenIndex = i + 1 // Offset by 1 because Overall is the first token
            if (tokenIndex < respTokens.size) {
                val stats = respTokens[tokenIndex].split(',')
                val lvl = stats.getOrNull(1)?.toLongOrNull() ?: 0L
                val xp = stats.getOrNull(2)?.toLongOrNull() ?: 0L

                val col = i % columns
                val row = i / columns
                val x = col * cellWidth
                val y = row * cellHeight + headerHeight

                // Draw icon
                val iconPath = "/images/osrs/skills/${skill.lowercase()}.png"
                val iconStream = javaClass.getResourceAsStream(iconPath)
                if (iconStream != null) {
                    val icon = ImageIO.read(iconStream)
                    g.drawImage(icon, x + 10, y + 10, 32, 32, null)
                }

                // Skill name and Level
                g.color = Color.WHITE
                g.font = Font("Arial", Font.BOLD, 16)
                g.drawString(skill, x + 50, y + 25)

                g.color = Color.YELLOW
                g.font = Font("Arial", Font.BOLD, 14)
                g.drawString("Lvl: $lvl", x + 50, y + 42)

                // XP
                g.color = Color.LIGHT_GRAY
                g.font = Font("Arial", Font.PLAIN, 10)
                val xpFormatted = String.format("%,d XP", xp)
                val xpWidth = g.fontMetrics.stringWidth(xpFormatted)
                g.drawString(xpFormatted, x + cellWidth - xpWidth - 10, y + 42)
            }
        }

        g.dispose()
        return image
    }
}
