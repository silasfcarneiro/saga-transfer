package com.saga.common

import java.time.Instant
import java.util.UUID

/**
 * Envelope comum a todo evento da saga.
 * Padroniza os metadados; o conteúdo específico vai em [payload].
 */
data class EventEnvelope<T>(
    val eventId: UUID = UUID.randomUUID(),   // idempotência: identifica o evento unicamente
    val eventType: String,                    // ex.: "Debited", "CreditFailed"
    val sagaId: UUID,                         // correlaciona todos os eventos de UMA transferência
    val occurredAt: Instant = Instant.now(),
    val payload: T                            // o conteúdo específico do evento
)