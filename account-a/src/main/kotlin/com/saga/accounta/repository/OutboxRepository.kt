package com.saga.accounta.repository

import com.saga.common.OutboxEvent
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface OutboxRepository : JpaRepository<OutboxEvent, UUID> {
    fun findByPublishedAtIsNull(): List<OutboxEvent>
}