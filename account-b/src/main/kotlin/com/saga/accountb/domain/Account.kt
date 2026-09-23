package com.saga.accountb.domain

import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "account")
class Account(
    @Id val id: UUID,
    @Column(name = "owner_name", nullable = false) var ownerName: String,
    @Column(nullable = false) var balance: Long,
    @Version @Column(nullable = false) var version: Long = 0
)