package com.saga.accounta.service

import com.saga.accounta.dto.CreateTransferRequest
import com.saga.accounta.repository.AccountRepository
import com.saga.accounta.repository.OutboxRepository
import com.saga.accounta.repository.ProcessedEventRepository
import com.saga.common.Debited
import com.saga.common.DebitReverted
import com.saga.common.OutboxEvent
import com.saga.common.ProcessedEvent
import com.saga.common.TransferRequested
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
    fun debit(sagaId: UUID, accountId: UUID, targetAccountId: UUID, amount: Long, eventId: UUID) {
        if (alreadyProcessed(eventId)) return

        val account = loadAccount(accountId)

        if (account.balance < amount) {
            publish(sagaId, "DebitFailed", mapOf("sagaId" to sagaId, "reason" to "saldo insuficiente"))
            markProcessed(eventId)
            return
        }

        account.balance -= amount
        accountRepository.save(account)

        publish(sagaId, "Debited", Debited(account.id, targetAccountId, amount))
        markProcessed(eventId)
    }

    @Transactional
    fun revert(sagaId: UUID, accountId: UUID, amount: Long, eventId: UUID) {
        if (alreadyProcessed(eventId)) return

        val account = loadAccount(accountId)

        // compensação: devolve o dinheiro
        account.balance += amount
        accountRepository.save(account)

        publish(sagaId, "DebitReverted", DebitReverted(account.id, amount))
        markProcessed(eventId)
    }

    @Transactional
    fun startTransfer(request: CreateTransferRequest): UUID {
        val sagaId = UUID.randomUUID()
        val payload = TransferRequested(
            request.sourceAccountId,
            request.targetAccountId,
            request.amount
        )
        publish(sagaId, "TransferRequested", payload)
        return sagaId
    }

    // ---------- privados ----------

    private fun alreadyProcessed(eventId: UUID): Boolean =
        processedEventRepository.existsById(eventId)

    private fun markProcessed(eventId: UUID) {
        processedEventRepository.save(ProcessedEvent(eventId))
    }

    private fun loadAccount(accountId: UUID) =
        accountRepository.findById(accountId)
            .orElseThrow { IllegalArgumentException("Conta não encontrada") }

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