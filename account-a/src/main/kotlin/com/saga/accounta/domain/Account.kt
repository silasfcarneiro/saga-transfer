package com.saga.accounta.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.util.UUID

@Entity
@Table(name = "account")
class Account(

    @Id
    val id: UUID,

    @Column(name = "owner_name", nullable = false)
    var ownerName: String,

    @Column(nullable = false)
    var balance: Long,

    @Version
    @Column(nullable = false)
    var version: Long = 0
)