package org.j3y.HuskerBot2.repository

import org.j3y.HuskerBot2.model.SocialEmbedReplacementEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface SocialEmbedReplacementRepo : JpaRepository<SocialEmbedReplacementEntity, Long> {
    fun findAllByOrderBySortOrderAsc(): List<SocialEmbedReplacementEntity>
}
