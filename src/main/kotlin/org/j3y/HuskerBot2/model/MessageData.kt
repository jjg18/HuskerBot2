package org.j3y.HuskerBot2.model

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import java.io.Serializable

/**
 * Serializable representation of a message we can schedule and later convert to JDA MessageCreateData.
 */
data class MessageData(
    val content: String? = null,
    val embeds: List<SimpleEmbed> = emptyList()
) : Serializable {
    fun toMessageCreateData(): MessageCreateData {
        val builder = MessageCreateBuilder()
        if (!content.isNullOrBlank()) {
            builder.setContent(content)
        }
        if (embeds.isNotEmpty()) {
            val jdaEmbeds: List<MessageEmbed> = embeds.map { it.toJdaEmbed() }
            builder.setEmbeds(jdaEmbeds)
        }
        return builder.build()
    }
}

/**
 * Minimal, Serializable subset of an embed used by our scheduling use-cases.
 */
data class SimpleEmbed(
    val title: String? = null,
    val description: String? = null,
    val footer: String? = null,
    val footerIconUrl: String? = null,
    val thumbnailUrl: String? = null,
    val imageUrl: String? = null,
    val color: Int? = null
) : Serializable {
    fun toJdaEmbed(): MessageEmbed {
        val eb = EmbedBuilder()
        if (title != null) eb.setTitle(title)
        if (description != null) eb.setDescription(description)
        if (thumbnailUrl != null) eb.setThumbnail(thumbnailUrl)
        if (imageUrl != null) eb.setImage(imageUrl)
        if (footer != null) eb.setFooter(footer, footerIconUrl)
        if (color != null) eb.setColor(color)
        return eb.build()
    }
}