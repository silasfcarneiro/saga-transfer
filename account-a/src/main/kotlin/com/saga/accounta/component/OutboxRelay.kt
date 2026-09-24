package com.saga.accounta.component

import com.saga.accounta.repository.OutboxRepository
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
class OutboxRelay(
    private val outboxRepository: OutboxRepository,
    private val kafkaTemplate: KafkaTemplate<String, String>
) {

    @Scheduled(fixedDelay = 500)
    @Transactional
    fun publishPending() {
        val pending = outboxRepository.findByPublishedAtIsNull()
        if (pending.isNotEmpty()) println(">>> RELAY: publicando ${pending.size} eventos")

        pending.forEach { event ->
            val envelope = """{"eventId":"${event.id}","eventType":"${event.eventType}","sagaId":"${event.sagaId}","payload":${event.payload}}"""
            kafkaTemplate.send(event.topic, event.sagaId.toString(), envelope)
            event.publishedAt = Instant.now()
        }
    }
}