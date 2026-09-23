package com.saga.accountb.component

import com.saga.accountb.service.AccountService
import com.saga.common.Debited
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.util.UUID

@Component
class SagaConsumer(
    private val accountService: AccountService,
    private val objectMapper: ObjectMapper
) {
    @KafkaListener(
        topics = ["saga-events"],
        groupId = "account-b",
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun onEvent(message: String) {
        val envelope = objectMapper.readTree(message)
        val eventType = envelope.get("eventType")?.asString() ?: return
        val eventId = UUID.fromString(envelope.get("eventId").asString())
        val sagaId = UUID.fromString(envelope.get("sagaId").asString())
        val payload = envelope.get("payload")

        when (eventType) {
            "Debited" -> {
                val e = objectMapper.treeToValue(payload, Debited::class.java)
                accountService.credit(sagaId, e.targetAccountId, e.sourceAccountId, e.amount, eventId)
            }
            // os outros eventos o Banco B ignora
        }
    }
}