package org.j3y.HuskerBot2.chat

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import java.awt.Color
import java.net.URLEncoder

@Component
class MusicShareListener : ListenerAdapter() {
    private val log = LoggerFactory.getLogger(MusicShareListener::class.java)
    private val client = RestTemplate()

    private val urlRegex = Regex("\\bhttps?://\\S+", RegexOption.IGNORE_CASE)

    // Identify supported music provider URLs
    private fun isSupportedMusicUrl(url: String): Boolean {
        val u = url.lowercase()
        return (
            // Spotify track or open link
            (u.contains("open.spotify.com/track/") || (u.contains("open.spotify.com/") && u.contains("?si="))) ||
            // Apple Music song link
            u.contains("music.apple.com/") ||
            // YouTube Music or YouTube link (many shares are youtube.com/watch or youtu.be)
            u.contains("music.youtube.com/") || u.contains("youtube.com/watch") || u.contains("youtu.be/")
        )
    }

    override fun onMessageReceived(event: MessageReceivedEvent) {
        val message = event.message
        val author = message.author
        if (author.isBot || author.isSystem || message.isWebhookMessage) return

        val raw = message.contentRaw
        if (raw.isBlank()) return

        val urls = urlRegex.findAll(raw).map { it.value }.toList()
        if (urls.isEmpty()) return

        val sourceUrl = urls.firstOrNull { isSupportedMusicUrl(it) } ?: return

        try {
            val odesli = fetchOdesli(sourceUrl) ?: return

            val (title, artist, thumb) = extractTitleArtistThumb(odesli)
            val album = extractAlbumName(odesli)
            val links = extractPlatformLinks(odesli)
            val linksOnlyYoutube = links.all { it.key.contains("youtube", ignoreCase = true) }
            if (links.isEmpty() || linksOnlyYoutube) return

            // Suppress embeds on the original message so only our embed shows
            message.suppressEmbeds(true).queue({ /* ok */ }, { ex ->
                log.debug("Failed to suppress embeds: ${ex.message}")
            })

            val description = buildString {
                if (!album.isNullOrBlank()) {
                    append("Album: ").append(album).append('\n')
                }
                append("Shared by ").append(author.effectiveName)
            }

            val embed = EmbedBuilder()
                .setColor(Color(0x5865F2))
                .setTitle(buildTitle(title, artist))
                .setDescription(description)
                .apply { if (!thumb.isNullOrBlank()) setThumbnail(thumb) }
                .build()

            val buttons = mutableListOf<Button>()

            links["spotify"]?.let {
                buttons += Button.link(it, "Spotify")
            }
            links["appleMusic"]?.let {
                buttons += Button.link(it, "Apple Music")
            }
            links["youtubeMusic"]?.let {
                buttons += Button.link(it, "Youtube Music")
            }
            links["youtube"]?.let {
                buttons += Button.link(it, "Youtube")
            }

            // If somehow none of the preferred labels exist, add any others for visibility
            if (buttons.isEmpty()) {
                links.entries.take(5).forEach { (platform, url) ->
                    buttons += Button.link(url, platform.replaceFirstChar { c -> c.titlecase() })
                }
            }

            val actionRows = if (buttons.isNotEmpty()) listOf(ActionRow.of(buttons)) else emptyList()

            message.replyEmbeds(embed)
                .setComponents(actionRows)
                .queue({ /* sent */ }, { ex ->
                    log.warn("Failed to send music cross-links embed", ex)
                })
        } catch (e: Exception) {
            log.error("Error processing music share", e)
        }
    }

    private fun buildTitle(title: String?, artist: String?): String {
        val safeTitle = title?.takeIf { it.isNotBlank() } ?: "Song"
        val safeArtist = artist?.takeIf { it.isNotBlank() } ?: "Unknown Artist"
        return "$safeTitle — $safeArtist"
    }

    private fun extractTitleArtistThumb(resp: OdesliResponse): Triple<String?, String?, String?> {
        val entity = resp.entityUniqueId?.let { id -> resp.entitiesByUniqueId?.get(id) }
            ?: resp.entitiesByUniqueId?.values?.firstOrNull()
        return Triple(entity?.title, entity?.artistName, entity?.thumbnailUrl ?: entity?.thumbnailUrl)
    }

    private fun extractAlbumName(resp: OdesliResponse): String? {
        val entity = resp.entityUniqueId?.let { id -> resp.entitiesByUniqueId?.get(id) }
            ?: resp.entitiesByUniqueId?.values?.firstOrNull()
        return entity?.albumName
    }

    private fun extractPlatformLinks(resp: OdesliResponse): Map<String, String> {
        return resp.linksByPlatform?.mapValues { it.value.url }?.filterValues { !it.isNullOrBlank() }?.mapValues { it.value!! }
            ?: emptyMap()
    }

    private fun fetchOdesli(url: String): OdesliResponse? {
        val encoded = URLEncoder.encode(url, Charsets.UTF_8)
        val endpoint = "https://api.song.link/v1-alpha.1/links?url=$encoded"
        return try {
            client.getForObject(endpoint, OdesliResponse::class.java)
        } catch (e: Exception) {
            log.debug("Odesli fetch failed: ${e.message}")
            null
        }
    }
}

// Minimal subset of Odesli response models that we care about
private data class OdesliResponse(
    val entityUniqueId: String? = null,
    val entitiesByUniqueId: Map<String, OdesliEntity>? = null,
    val linksByPlatform: Map<String, OdesliPlatformLink>? = null,
)

private data class OdesliEntity(
    val title: String? = null,
    val artistName: String? = null,
    val thumbnailUrl: String? = null,
    val albumName: String? = null,
)

private data class OdesliPlatformLink(
    val url: String? = null,
)