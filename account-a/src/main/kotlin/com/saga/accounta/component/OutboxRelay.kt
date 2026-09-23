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
        for (event in pending) {
            kafkaTemplate.send(event.topic, event.sagaId.toString(), event.payload)
            event.publishedAt = Instant.now()
        }
    }
}