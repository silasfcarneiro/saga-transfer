package com.saga.accounta.consumer

import com.saga.accounta.service.AccountService
import com.saga.common.TransferRequested
import com.saga.common.CreditFailed
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
        groupId = "account-a",
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun onEvent(message: String) {
        val envelope = objectMapper.readTree(message)
        val eventType = envelope.get("eventType")?.asString() ?: return
        val eventId = UUID.fromString(envelope.get("eventId").asString())
        val sagaId = UUID.fromString(envelope.get("sagaId").asString())
        val payload = envelope.get("payload")

        when (eventType) {
            "TransferRequested" -> {
                val e = objectMapper.treeToValue(payload, TransferRequested::class.java)
                accountService.debit(sagaId, e.sourceAccountId, e.targetAccountId, e.amount, eventId)
            }
            "CreditFailed" -> {
                val e = objectMapper.treeToValue(payload, CreditFailed::class.java)
                accountService.revert(sagaId, e.sourceAccountId, e.amount, eventId)
            }
            // outros eventos o Banco A ignora (não são pra ele)
        }
    }
}