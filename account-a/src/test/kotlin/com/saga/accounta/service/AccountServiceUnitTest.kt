package com.saga.accounta.service

import com.saga.accounta.domain.Account
import com.saga.accounta.repository.AccountRepository
import com.saga.accounta.repository.OutboxRepository
import com.saga.accounta.repository.ProcessedEventRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import tools.jackson.databind.ObjectMapper
import java.util.Optional
import java.util.UUID

class AccountServiceUnitTest {

    private val accountRepository: AccountRepository = mock()
    private val outboxRepository: OutboxRepository = mock()
    private val processedEventRepository: ProcessedEventRepository = mock()
    private val objectMapper = ObjectMapper()   // real, pra serializar de verdade

    private lateinit var service: AccountService

    @BeforeEach
    fun setup() {
        service = AccountService(accountRepository, outboxRepository, processedEventRepository, objectMapper)
    }

    private fun conta(id: UUID, saldo: Long) = Account(id, "Titular", saldo, 0)

    @Test
    fun `debito com saldo suficiente debita e publica Debited`() {
        val origem = UUID.randomUUID()
        val destino = UUID.randomUUID()
        val eventId = UUID.randomUUID()

        whenever(processedEventRepository.existsById(eventId)).thenReturn(false)
        whenever(accountRepository.findById(origem)).thenReturn(Optional.of(conta(origem, 100000)))

        service.debit(UUID.randomUUID(), origem, destino, 30000, eventId)

        verify(accountRepository).save(any())        // debitou
        verify(outboxRepository).save(any())         // publicou evento
        verify(processedEventRepository).save(any()) // marcou processado
    }

    @Test
    fun `debito com saldo insuficiente nao debita`() {
        val origem = UUID.randomUUID()
        val eventId = UUID.randomUUID()

        whenever(processedEventRepository.existsById(eventId)).thenReturn(false)
        whenever(accountRepository.findById(origem)).thenReturn(Optional.of(conta(origem, 1000)))

        service.debit(UUID.randomUUID(), origem, UUID.randomUUID(), 30000, eventId)

        verify(accountRepository, never()).save(any())  // NÃO debitou
        verify(outboxRepository).save(any())            // mas publicou DebitFailed
    }

    @Test
    fun `evento ja processado e ignorado (idempotencia)`() {
        val eventId = UUID.randomUUID()
        whenever(processedEventRepository.existsById(eventId)).thenReturn(true)

        service.debit(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 30000, eventId)

        verify(accountRepository, never()).findById(any())  // nem buscou a conta
        verify(accountRepository, never()).save(any())
    }

    @Test
    fun `revert devolve o dinheiro (compensacao)`() {
        val conta = UUID.randomUUID()
        val eventId = UUID.randomUUID()

        whenever(processedEventRepository.existsById(eventId)).thenReturn(false)
        whenever(accountRepository.findById(conta)).thenReturn(Optional.of(conta(conta, 50000)))

        service.revert(UUID.randomUUID(), conta, 30000, eventId)

        verify(accountRepository).save(any())   // creditou de volta
        verify(outboxRepository).save(any())    // publicou DebitReverted
    }
}