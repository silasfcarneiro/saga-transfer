package com.saga.accountb.service

import com.saga.accountb.domain.Account
import com.saga.accountb.repository.AccountRepository
import com.saga.accountb.repository.OutboxRepository
import com.saga.accountb.repository.ProcessedEventRepository
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
    private val objectMapper = ObjectMapper()

    private lateinit var service: AccountService

    @BeforeEach
    fun setup() {
        service = AccountService(accountRepository, outboxRepository, processedEventRepository, objectMapper)
    }

    private fun conta(id: UUID, saldo: Long) = Account(id, "Titular", saldo, 0)

    @Test
    fun `credito com conta existente credita e publica Credited`() {
        val destino = UUID.randomUUID()
        val origem = UUID.randomUUID()
        val eventId = UUID.randomUUID()

        whenever(processedEventRepository.existsById(eventId)).thenReturn(false)
        whenever(accountRepository.findById(destino)).thenReturn(Optional.of(conta(destino, 20000)))

        service.credit(UUID.randomUUID(), destino, origem, 30000, eventId)

        verify(accountRepository).save(any())        // creditou
        verify(outboxRepository).save(any())         // publicou Credited
        verify(processedEventRepository).save(any())
    }

    @Test
    fun `credito com conta inexistente publica CreditFailed e nao credita`() {
        val destino = UUID.randomUUID()
        val eventId = UUID.randomUUID()

        whenever(processedEventRepository.existsById(eventId)).thenReturn(false)
        whenever(accountRepository.findById(destino)).thenReturn(Optional.empty())  // não existe

        service.credit(UUID.randomUUID(), destino, UUID.randomUUID(), 30000, eventId)

        verify(accountRepository, never()).save(any())  // NÃO creditou
        verify(outboxRepository).save(any())            // publicou CreditFailed (dispara compensação)
    }

    @Test
    fun `evento ja processado e ignorado (idempotencia)`() {
        val eventId = UUID.randomUUID()
        whenever(processedEventRepository.existsById(eventId)).thenReturn(true)

        service.credit(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 30000, eventId)

        verify(accountRepository, never()).findById(any())
        verify(accountRepository, never()).save(any())
    }
}