package com.saga.accounta

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
@EntityScan(basePackages = ["com.saga.accounta", "com.saga.common"])
class AccountAApplication

fun main(args: Array<String>) {
    runApplication<AccountAApplication>(*args)
}