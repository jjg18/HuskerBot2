package org.j3y.HuskerBot2.scheduler

import org.j3y.HuskerBot2.model.MessageData
import org.j3y.HuskerBot2.model.SimpleEmbed
import org.j3y.HuskerBot2.service.DecadeSong
import org.j3y.HuskerBot2.service.DecadeSongService
import org.j3y.HuskerBot2.service.GoogleGeminiService
import org.j3y.HuskerBot2.service.WeatherService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.RestClientException
import java.net.URLEncoder
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

@Component
class SunriseSunsetScheduler(
    private val weatherService: WeatherService,
    private val channelMessageSchedulerService: ChannelMessageSchedulerService,
    private val googleGeminiService: GoogleGeminiService,
    private val decadeSongService: DecadeSongService,
    @Value("\${discord.channels.general}") private val generalChannelId: String
) {
    private val log = LoggerFactory.getLogger(SunriseSunsetScheduler::class.java)
    private val zone: ZoneId = ZoneId.of("America/Chicago")
    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
    private val restTemplate = RestTemplate()

    // Lincoln, Nebraska approximate coordinates
    private val lincolnLat = 40.8136
    private val lincolnLon = -96.7026

    // Run shortly after midnight local time to schedule for the day
    @Scheduled(cron = "0 5 0 * * *", zone = "America/Chicago")
    fun scheduleDailySunMessages() {
        try {
            val today = LocalDate.now(zone)
            scheduleForDate(today)
        } catch (e: Exception) {
            log.error("Error scheduling daily sunrise/sunset messages", e)
        }
    }

    private fun scheduleForDate(date: LocalDate) {
        val times = weatherService.getSunriseSunsetTimes(lincolnLat, lincolnLon, date, zone)
        if (times == null) {
            log.warn("Could not determine sunrise/sunset for {}", date)
            return
        }

        val (sunrise, sunset) = times //Pair(ZonedDateTime.now().plusSeconds(5), ZonedDateTime.now().plusSeconds(20)) //times
        val now = ZonedDateTime.now(zone).toInstant()
        val channelIdLong = generalChannelId.toLongOrNull()
        if (channelIdLong == null) {
            log.warn("Invalid general channel id: {}", generalChannelId)
            return
        }

        if (sunrise.toInstant().isAfter(now)) {
            val message = buildSunriseMessage(sunrise)
            channelMessageSchedulerService.scheduleMessage(
                channelIdLong,
                message,
                sunrise.toInstant(),
                instanceId = buildInstanceId(date, "sunrise")
            )
            log.info("Scheduled Morning Gang at {}", sunrise)
        } else {
            log.info("Sunrise for {} already passed at {}", date, sunrise)
        }

        if (sunset.toInstant().isAfter(now)) {
            val message = buildSunsetMessage(sunset)
            channelMessageSchedulerService.scheduleMessage(
                channelIdLong,
                message,
                sunset.toInstant(),
                instanceId = buildInstanceId(date, "sunset")
            )
            log.info("Scheduled Night Gang at {}", sunset)
        } else {
            log.info("Sunset for {} already passed at {}", date, sunset)
        }
    }

    /**
     * Builds the Morning Gang embed message, calling Gemini for a fresh description/song each time.
     */
    fun buildSunriseMessage(sunrise: ZonedDateTime): MessageData {
        val defaultSunriseDescription = "Rise and whine. Shoutout to the morning people who woke up humming like it’s a musical—please lower your perkiness to a reasonable volume."
        val picked = decadeSongService.randomSong()
        val description = if (picked != null) {
            val (decade, song) = picked
            buildSongDescription(
                song = song,
                decade = decade,
                greeting = "Greet the users to the morning",
                defaultDescription = defaultSunriseDescription
            )
        } else {
            defaultSunriseDescription
        }

        val (fixedDescription, thumbnailUrl) = validateAndFixSongLink(description)

        return MessageData(
            embeds = listOf(
                SimpleEmbed(
                    title = "☀\uFE0F Morning Gang",
                    description = fixedDescription,
                    footer = "Sunrise @ ${sunrise.format(timeFormatter)}",
                    footerIconUrl = "https://cdn.discordapp.com/emojis/991309655185817690.webp",
                    thumbnailUrl = thumbnailUrl
                )
            )
        )
    }

    /**
     * Builds the Night Gang embed message, calling Gemini for a fresh description/song each time.
     */
    fun buildSunsetMessage(sunset: ZonedDateTime): MessageData {
        val defaultSunsetDescription = "Night owls, congrats on surviving another 3am ‘grind.’ Please keep your chaotic sleep schedule and bragging at arm’s length from the rest of our circadian rhythms."
        val picked = decadeSongService.randomSong()
        val sunsetDescription = if (picked != null) {
            val (decade, song) = picked
            buildSongDescription(
                song = song,
                decade = decade,
                greeting = "Invite the users to enjoy their night",
                defaultDescription = defaultSunsetDescription
            )
        } else {
            defaultSunsetDescription
        }

        val (fixedSunsetDescription, sunsetThumbnailUrl) = validateAndFixSongLink(sunsetDescription)

        return MessageData(
            embeds = listOf(
                SimpleEmbed(
                    title = "\uD83C\uDF1B Night Gang",
                    description = fixedSunsetDescription,
                    footer = "Sunset @ ${sunset.format(timeFormatter)}",
                    footerIconUrl = "https://cdn.discordapp.com/emojis/1104950612371701864.webp",
                    thumbnailUrl = sunsetThumbnailUrl
                )
            )
        )
    }

    /**
     * Asks Gemini to write a short description around a specific, already-chosen real song (pulled
     * from our curated per-decade CSV lists), formatted with a Markdown link to the song's YouTube
     * link. Falls back to a simple default description embedding the real song/link if Gemini fails.
     */
    private fun buildSongDescription(
        song: DecadeSong,
        decade: String,
        greeting: String,
        defaultDescription: String
    ): String {
        val songMarkdown = "[${song.title} - ${song.artist}](${song.youtubeLink})"
        val prompt = """
            The song "${song.title}" by ${song.artist} (from the $decade) was picked.
            $greeting, and explain briefly the song. Keep this short and sweet to under 250 characters.
            Do not invent a different song or link; simply write the description text (do not include the song link yourself, it will be appended separately).
        """.trimIndent()
        val geminiTextRaw = try { googleGeminiService.generateText(prompt, temperature = 1.3) } catch (e: Exception) {
            log.warn("Gemini text generation failed for song description", e)
            ""
        }
        val blurb = when {
            geminiTextRaw.isBlank() -> null
            geminiTextRaw.contains("Gemini is not configured", ignoreCase = true) -> null
            geminiTextRaw.startsWith("Error", ignoreCase = true) -> null
            geminiTextRaw.contains("No response from Gemini", ignoreCase = true) -> null
            else -> geminiTextRaw.trim().take(400)
        }

        return if (blurb != null) {
            "🎵 $songMarkdown\n\n$blurb"
        } else {
            "🎵 $songMarkdown\n\n$defaultDescription"
        }
    }

    /**
     * Our curated song links are YouTube search-query URLs (not direct video links), and Gemini can
     * also hallucinate YouTube video ids that don't actually exist/are unavailable. This resolves the
     * Markdown song link found in the given text to a real, direct YouTube video link: if it's already
     * a watch/youtu.be link, it's validated against YouTube's public oEmbed endpoint (no API key
     * required); otherwise (e.g. a search-query link) it's resolved by searching YouTube for the song
     * title/artist and taking the first real result. If no real video can be resolved, the link is left
     * as a safe YouTube search URL and no thumbnail is returned.
     *
     * @return a pair of (possibly-fixed description text, thumbnail url or null)
     */
    private fun validateAndFixSongLink(text: String): Pair<String, String?> {
        val linkRegex = Regex("""\[([^\]]+)]\(([^)]+)\)""")
        val match = linkRegex.find(text) ?: return text to null
        val songTitle = match.groupValues[1]
        val linkUrl = match.groupValues[2]

        val directVideoIdRegex = Regex("""(?:https?://)?(?:www\.)?(?:youtube\.com/watch\?v=|youtu\.be/)([A-Za-z0-9_-]{6,})""")
        val directVideoId = directVideoIdRegex.find(linkUrl)?.groupValues?.get(1)

        if (directVideoId != null && isYoutubeVideoAvailable(directVideoId)) {
            return text to "https://img.youtube.com/vi/$directVideoId/hqdefault.jpg"
        }

        if (directVideoId != null) {
            log.warn("Song link returned an unavailable/hallucinated YouTube video id '{}' for song '{}'; searching for a real video instead", directVideoId, songTitle)
        }

        val realVideoId = findFirstSearchResultVideoId(songTitle)
        if (realVideoId == null) {
            val searchUrl = "https://www.youtube.com/results?search_query=" + URLEncoder.encode(songTitle, "UTF-8")
            val fixedText = text.replaceRange(match.range, "[$songTitle]($searchUrl)")
            return fixedText to null
        }

        val fixedText = text.replaceRange(match.range, "[$songTitle](https://www.youtube.com/watch?v=$realVideoId)")
        return fixedText to "https://img.youtube.com/vi/$realVideoId/hqdefault.jpg"
    }

    /**
     * Searches YouTube for the given query (e.g. song title/artist) and returns the video id of the
     * first result found in the search results page, or null if none could be determined. This uses
     * YouTube's public search page (no API key required) since Gemini's own video id can be a
     * hallucination.
     */
    private fun findFirstSearchResultVideoId(query: String): String? {
        return try {
            val searchUrl = "https://www.youtube.com/results?search_query=" + URLEncoder.encode(query, "UTF-8")
            val html = restTemplate.getForObject(searchUrl, String::class.java) ?: return null
            val videoIdRegex = Regex(""""videoId":"([A-Za-z0-9_-]{11})"""")
            videoIdRegex.find(html)?.groupValues?.get(1)
        } catch (e: RestClientException) {
            log.warn("Failed to search YouTube for '{}': {}", query, e.message)
            null
        } catch (e: Exception) {
            log.warn("Unexpected error searching YouTube for '{}'", query, e)
            null
        }
    }

    /**
     * Checks whether a YouTube video id resolves to a real, available video using YouTube's public
     * oEmbed endpoint, which returns a successful response only for existing, embeddable videos.
     */
    private fun isYoutubeVideoAvailable(videoId: String): Boolean {
        return try {
            val oembedUrl = "https://www.youtube.com/oembed?url=" +
                URLEncoder.encode("https://www.youtube.com/watch?v=$videoId", "UTF-8") +
                "&format=json"
            val response = restTemplate.getForEntity(oembedUrl, String::class.java)
            response.statusCode.is2xxSuccessful
        } catch (e: RestClientException) {
            false
        } catch (e: Exception) {
            log.warn("Unexpected error validating YouTube video id {}", videoId, e)
            false
        }
    }

    /**
     * Sends a Morning Gang message immediately to the given channel (or the configured general channel).
     * Useful for manually testing the sunrise message via a slash command.
     */
    fun sendSunriseMessageNow(channelId: Long = generalChannelId.toLongOrNull() ?: 0L) {
        val message = buildSunriseMessage(ZonedDateTime.now(zone))
        channelMessageSchedulerService.scheduleMessage(
            channelId,
            message,
            Instant.now(),
            instanceId = "sun-test-sunrise-${UUID.randomUUID()}"
        )
    }

    /**
     * Sends a Night Gang message immediately to the given channel (or the configured general channel).
     * Useful for manually testing the sunset message via a slash command.
     */
    fun sendSunsetMessageNow(channelId: Long = generalChannelId.toLongOrNull() ?: 0L) {
        val message = buildSunsetMessage(ZonedDateTime.now(zone))
        channelMessageSchedulerService.scheduleMessage(
            channelId,
            message,
            Instant.now(),
            instanceId = "sun-test-sunset-${UUID.randomUUID()}"
        )
    }

    private fun buildInstanceId(date: LocalDate, type: String): String = "sun-${date}-$type"
}