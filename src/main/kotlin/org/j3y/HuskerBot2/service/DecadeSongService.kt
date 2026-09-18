package org.j3y.HuskerBot2.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

data class DecadeSong(
    val artist: String,
    val title: String,
    val youtubeLink: String
)

/**
 * Loads curated per-decade song lists (Artist, Song Title, Youtube Link) from CSV files bundled as
 * classpath resources, and provides a way to randomly pick a decade and then a random song from that
 * decade's list. This replaces relying on Gemini to hallucinate a song recommendation and link.
 */
@Service
class DecadeSongService {
    private val log = LoggerFactory.getLogger(DecadeSongService::class.java)

    private val decadeFiles = linkedMapOf(
        "70s" to "music/70s.csv",
        "80s" to "music/80s.csv",
        "90s" to "music/90s.csv",
        "00s" to "music/00s.csv",
        "2010s" to "music/2010s.csv",
        "2020s" to "music/2020s.csv"
    )

    private val songsByDecade: Map<String, List<DecadeSong>> = decadeFiles.mapValues { (decade, path) ->
        loadSongs(path, decade)
    }.filterValues { it.isNotEmpty() }

    // Precomputed as a List so decade selection uses simple, unambiguous index-based random access
    // instead of Set.random(), and so every decade load is logged once at startup for visibility.
    private val decadeKeys: List<String> = songsByDecade.keys.toList().also { keys ->
        log.info("Loaded {} decade song list(s): {}", keys.size, keys.joinToString { "$it (${songsByDecade[it]?.size ?: 0} songs)" })
    }

    private fun loadSongs(resourcePath: String, decade: String): List<DecadeSong> {
        return try {
            val stream = this::class.java.classLoader.getResourceAsStream(resourcePath)
                ?: run {
                    log.warn("Could not find song list resource for decade {} at {}", decade, resourcePath)
                    return emptyList()
                }
            BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { reader ->
                reader.readLine() // header
                reader.lineSequence()
                    .filter { it.isNotBlank() }
                    .mapNotNull { line ->
                        val parts = line.split(",")
                        if (parts.size < 3) {
                            null
                        } else {
                            val artist = parts[0].trim()
                            val youtubeLink = parts.last().trim()
                            val title = parts.subList(1, parts.size - 1).joinToString(",").trim()
                            DecadeSong(artist, title, youtubeLink)
                        }
                    }
                    .toList()
            }
        } catch (e: Exception) {
            log.warn("Failed to load song list for decade {} from {}", decade, resourcePath, e)
            emptyList()
        }
    }

    /**
     * Randomly picks a decade (from the ones with a loaded song list) and then a random song from
     * that decade's list.
     */
    fun randomSong(): Pair<String, DecadeSong>? {
        if (decadeKeys.isEmpty()) return null
        val decade = decadeKeys[kotlin.random.Random.nextInt(decadeKeys.size)]
        val songs = songsByDecade[decade] ?: return null
        if (songs.isEmpty()) return null
        val song = songs[kotlin.random.Random.nextInt(songs.size)]
        log.info("Picked decade '{}' and song '{}' by '{}'", decade, song.title, song.artist)
        return decade to song
    }
}
