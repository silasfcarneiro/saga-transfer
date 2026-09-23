package com.saga.common

import java.util.UUID

/**
 * Eventos da saga de transferência entre bancos.
 * Cada um é o payload que viaja dentro do EventEnvelope.
 * O sagaId (no envelope) correlaciona todos os eventos de uma transferência.
 */

// 1. Início: alguém solicita a transferência
data class TransferRequested(
    val sourceAccountId: UUID,   // conta no Banco A
    val targetAccountId: UUID,   // conta no Banco B
    val amount: Long             // centavos
)

// 2. Banco A debitou com sucesso
data class Debited(
    val sourceAccountId: UUID,
    val targetAccountId: UUID,
    val amount: Long
)

// 3a. Banco B creditou com sucesso -> saga completa
data class Credited(
    val targetAccountId: UUID,
    val amount: Long
)

// 3b. Banco B NÃO conseguiu creditar -> dispara compensação
data class CreditFailed(
    val sourceAccountId: UUID,   // pra saber quem estornar
    val amount: Long,
    val reason: String
)

// 4. Banco A estornou o débito -> saga compensada
data class DebitReverted(
    val sourceAccountId: UUID,
    val amount: Long
)