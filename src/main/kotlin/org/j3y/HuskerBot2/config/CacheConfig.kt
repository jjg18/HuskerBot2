package org.j3y.HuskerBot2.config

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import java.time.Duration

@Configuration
@EnableCaching
@EnableScheduling
class CacheConfig {

    @Bean
    fun cacheManager(): CacheManager {
        val cacheManager = CaffeineCacheManager(
            "cfbScoreboards",
            "nflScoreboards",
            "nhlScoreboards",
            "teamData",
            "urban-definitions"
        )
        cacheManager.setCaffeine(
            Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(1))
        )
        return cacheManager
    }
}
