package com.saga.accounta.repository

import com.saga.accounta.domain.Account
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AccountRepository : JpaRepository<Account, UUID>