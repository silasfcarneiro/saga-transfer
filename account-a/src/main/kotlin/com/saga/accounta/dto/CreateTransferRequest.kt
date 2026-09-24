package com.saga.accounta.dto

import java.util.UUID

data class CreateTransferRequest(
    val sourceAccountId: UUID,
    val targetAccountId: UUID,
    val amount: Long
)