package org.j3y.HuskerBot2.model

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "t_schedule")
class ScheduleEntity(
    @Id
    var id: Long = 0,
    var opponent: String = "",
    var opponentLogo: String = "",
    var location: String = "",
    var isConference: Boolean = false,
    var venueType: String = "",
    var venue: String = "",
    var dateTime: Instant = Instant.now(),
    var season: Int = 2025,
    var week: Int = 0,
    var huskersScore: Int? = 0,
    var opponentScore: Int? = 0,
    var didNebraskaBeatSpread: Boolean? = null,
    var spread: String = "",
    var overUnder: Double? = null,
    var completed: Boolean? = false,
    var isDome: Boolean? = false,
)
