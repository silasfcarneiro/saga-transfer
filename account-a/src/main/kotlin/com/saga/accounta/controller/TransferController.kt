package com.saga.accounta.controller

import com.saga.accounta.dto.CreateTransferRequest
import com.saga.accounta.service.AccountService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/transfers")
class TransferController(
    private val accountService: AccountService
) {
    @PostMapping
    fun create(@RequestBody request: CreateTransferRequest): ResponseEntity<Map<String, UUID>> {
        val sagaId = accountService.startTransfer(request)
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(mapOf("sagaId" to sagaId))
    }
}