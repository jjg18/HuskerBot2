package org.j3y.HuskerBot2.model

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "t_social_embed_replacement")
class SocialEmbedReplacementEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    /** Substring to match against the lowercased URL (e.g. "://twitter.com/") */
    var matchPattern: String = "",

    /** Regex pattern applied to the original URL for replacement (e.g. "^(https?://)(?:www\\.)?(?:twitter|x)\\.com") */
    var replaceRegex: String = "",

    /** Replacement string used with the regex (e.g. "\$1fxtwitter.com"), or a full URL prefix for wrap-style replacements */
    var replaceWith: String = "",

    /**
     * Strategy for how the replacement is applied:
     *   REGEX  - apply replaceRegex -> replaceWith on the original URL
     *   WRAP   - prepend replaceWith to the URL-encoded original URL (e.g. embedez style)
     *   SKIP   - if the URL contains matchPattern, skip it (already fixed domain)
     */
    var strategy: String = "REGEX",

    /** Human-readable label, e.g. "Twitter/X -> fxtwitter" */
    var label: String = "",

    /** Order in which rules are evaluated (lower = earlier) */
    var sortOrder: Int = 0,
)
