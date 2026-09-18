package org.j3y.HuskerBot2.service

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DecadeSongServiceTest {

    @Test
    fun `randomSong should distribute picks across all decades`() {
        val service = DecadeSongService()
        val counts = mutableMapOf<String, Int>()
        repeat(6000) {
            val (decade, _) = service.randomSong()!!
            counts[decade] = (counts[decade] ?: 0) + 1
        }
        assertTrue(counts.size > 1, "Expected picks to be spread across multiple decades, but got: $counts")
        assertTrue(counts.values.all { it in 700..1300 }, "Expected roughly uniform distribution across decades, but got: $counts")
    }
}
