package com.saga.common

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "outbox")
class OutboxEvent(

    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "saga_id", nullable = false)
    val sagaId: UUID,

    @Column(name = "event_type", nullable = false)
    val eventType: String,

    @Column(nullable = false)
    val topic: String,

    @Column(nullable = false, columnDefinition = "text")
    val payload: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "published_at")
    var publishedAt: Instant? = null
)