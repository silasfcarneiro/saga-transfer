package com.saga.common

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * Registro de eventos já processados por um consumer, para idempotência.
 * Antes de processar um evento, grava-se o eventId aqui na mesma transação.
 * Se já existe, o evento é ignorado (reentrega at-least-once).
 */
@Entity
@Table(name = "processed_event")
class ProcessedEvent(

    @Id
    val eventId: UUID,

    @Column(name = "processed_at", nullable = false)
    val processedAt: Instant = Instant.now()
)