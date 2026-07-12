package org.j3y.HuskerBot2.service

import jakarta.annotation.PostConstruct
import org.j3y.HuskerBot2.model.SocialEmbedReplacementEntity
import org.j3y.HuskerBot2.repository.SocialEmbedReplacementRepo
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.net.URLEncoder

@Service
class SocialEmbedReplacementService(
    private val repo: SocialEmbedReplacementRepo,
) {
    private val log = LoggerFactory.getLogger(SocialEmbedReplacementService::class.java)

    @PostConstruct
    fun seedDefaults() {
        if (repo.count() > 0) return

        val defaults = listOf(
            SocialEmbedReplacementEntity(
                matchPattern = "twitter.com/",
                replaceRegex = "^(https?://)(?:www\\.)?(?:twitter|x)\\.com",
                replaceWith = "\$1fxtwitter.com",
                strategy = "REGEX",
                label = "Twitter/X -> fxtwitter",
                sortOrder = 10,
            ),
            SocialEmbedReplacementEntity(
                matchPattern = "x.com/",
                replaceRegex = "^(https?://)(?:www\\.)?(?:twitter|x)\\.com",
                replaceWith = "\$1fxtwitter.com",
                strategy = "REGEX",
                label = "X.com -> fxtwitter",
                sortOrder = 11,
            ),
            SocialEmbedReplacementEntity(
                matchPattern = "tiktok.com/",
                replaceRegex = "^(https?://)(?:www\\.)?tiktok\\.com",
                replaceWith = "\$1tnktok.com",
                strategy = "REGEX",
                label = "TikTok -> tnktok",
                sortOrder = 20,
            ),
            SocialEmbedReplacementEntity(
                matchPattern = "instagram.com/",
                replaceRegex = "^(https?://)(?:www\\.)?instagram\\.com",
                replaceWith = "\$1vxinstagram.com",
                strategy = "REGEX",
                label = "Instagram -> vxinstagram",
                sortOrder = 30,
            ),
            SocialEmbedReplacementEntity(
                matchPattern = "bsky.app/",
                replaceRegex = "^(https?://)(?:www\\.)?bsky\\.app",
                replaceWith = "\$1fxbsky.app",
                strategy = "REGEX",
                label = "Bluesky -> fxbsky",
                sortOrder = 40,
            ),
            SocialEmbedReplacementEntity(
                matchPattern = "reddit.com/",
                replaceRegex = "^(https?://)(?:www\\.|old\\.|m\\.|np\\.)?reddit\\.com",
                replaceWith = "\$1vxreddit.com",
                strategy = "REGEX",
                label = "Reddit -> vxreddit",
                sortOrder = 60,
            ),
        )

        repo.saveAll(defaults)
        log.info("Seeded ${defaults.size} default social embed replacement rules.")
    }

    fun getRules(): List<SocialEmbedReplacementEntity> = repo.findAllByOrderBySortOrderAsc()

    /**
     * Given a URL, returns the replacement URL according to the stored rules,
     * or null if no rule matches (or the URL should be skipped).
     * Returns the string "SKIP" sentinel if a SKIP rule matched.
     */
    fun applyRules(url: String): ApplyResult {
        val lower = url.lowercase()
        for (rule in getRules()) {
            if (!lower.contains(rule.matchPattern)) continue
            return when (rule.strategy.uppercase()) {
                "SKIP" -> ApplyResult.Skip
                "WRAP" -> ApplyResult.Replaced(rule.replaceWith + URLEncoder.encode(url, "UTF-8"))
                "REGEX" -> {
                    val replaced = url.replace(
                        Regex(rule.replaceRegex, RegexOption.IGNORE_CASE),
                        rule.replaceWith,
                    )
                    ApplyResult.Replaced(replaced)
                }
                else -> continue
            }
        }
        return ApplyResult.NoMatch
    }

    fun addRule(entity: SocialEmbedReplacementEntity): SocialEmbedReplacementEntity = repo.save(entity)

    fun updateRule(id: Long, entity: SocialEmbedReplacementEntity): SocialEmbedReplacementEntity? {
        if (!repo.existsById(id)) return null
        entity.id = id
        return repo.save(entity)
    }

    fun deleteRule(id: Long): Boolean {
        if (!repo.existsById(id)) return false
        repo.deleteById(id)
        return true
    }

    sealed class ApplyResult {
        object Skip : ApplyResult()
        object NoMatch : ApplyResult()
        data class Replaced(val url: String) : ApplyResult()
    }
}
