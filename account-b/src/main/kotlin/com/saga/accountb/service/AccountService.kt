package com.saga.accountb.service

import com.saga.accountb.repository.AccountRepository
import com.saga.accountb.repository.OutboxRepository
import com.saga.accountb.repository.ProcessedEventRepository
import com.saga.common.Credited
import com.saga.common.CreditFailed
import com.saga.common.OutboxEvent
import com.saga.common.ProcessedEvent
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.util.UUID

@Service
class AccountService(
    private val accountRepository: AccountRepository,
    private val outboxRepository: OutboxRepository,
    private val processedEventRepository: ProcessedEventRepository,
    private val objectMapper: ObjectMapper
) {

    private val topic = "saga-events"

    @Transactional
    fun credit(sagaId: UUID, targetAccountId: UUID, sourceAccountId: UUID, amount: Long, eventId: UUID) {
        if (alreadyProcessed(eventId)) return

        // busca a conta de destino
        val account = accountRepository.findById(targetAccountId).orElse(null)

        // se a conta de destino não existe -> falha, dispara compensação no Banco A
        if (account == null) {
            publish(sagaId, "CreditFailed", CreditFailed(sourceAccountId, amount, "conta de destino inexistente"))
            markProcessed(eventId)
            return
        }

        // credita
        account.balance += amount
        accountRepository.save(account)

        // sucesso -> saga completa
        publish(sagaId, "Credited", Credited(targetAccountId, amount))
        markProcessed(eventId)
    }

    // ---------- privados ----------

    private fun alreadyProcessed(eventId: UUID) =
        processedEventRepository.existsById(eventId)

    private fun markProcessed(eventId: UUID) {
        processedEventRepository.save(ProcessedEvent(eventId))
    }

    private fun publish(sagaId: UUID, eventType: String, payload: Any) {
        outboxRepository.save(
            OutboxEvent(
                sagaId = sagaId,
                eventType = eventType,
                topic = topic,
                payload = objectMapper.writeValueAsString(payload)
            )
        )
    }
}