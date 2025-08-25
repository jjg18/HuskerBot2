package org.j3y.HuskerBot2.model

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Lob
import jakarta.persistence.Table
import java.time.Instant

/**
 * Entity used only to let Hibernate/JPA create the db-scheduler table if it doesn't exist.
 * This mirrors the default schema used by com.github.kagkarlsson:db-scheduler.
 */
@Entity
@Table(name = "scheduled_tasks")
class ScheduledTaskEntity(
    @EmbeddedId
    var id: ScheduledTaskId = ScheduledTaskId(),

    @Lob
    @Column(name = "task_data")
    var taskData: ByteArray? = null,

    @Column(name = "execution_time", nullable = false)
    var executionTime: Instant = Instant.now(),

    @Column(name = "picked", nullable = false)
    var picked: Boolean = false,

    @Column(name = "picked_by")
    var pickedBy: String? = null,

    @Column(name = "last_success")
    var lastSuccess: Instant? = null,

    @Column(name = "last_failure")
    var lastFailure: Instant? = null,

    @Column(name = "consecutive_failures")
    var consecutiveFailures: Int? = null,

    @Column(name = "last_heartbeat")
    var lastHeartbeat: Instant? = null,

    @Column(name = "version", nullable = false)
    var version: Long = 0,

    @Column(name = "created", nullable = true)
    var created: Instant? = Instant.now(),

    // Column name as used by db-scheduler >= 12
    @Column(name = "last_reschedule")
    var lastReschedule: Instant? = null,
)

@Embeddable
class ScheduledTaskId(
    @Column(name = "task_name", length = 100, nullable = false)
    var taskName: String = "",

    @Column(name = "task_instance", length = 100, nullable = false)
    var taskInstance: String = "",
) : java.io.Serializable
