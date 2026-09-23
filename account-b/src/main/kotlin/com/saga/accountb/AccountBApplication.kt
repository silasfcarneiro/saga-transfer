package com.saga.accountb

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
@EntityScan(basePackages = ["com.saga.accountb", "com.saga.common"])
class AccountBApplication

fun main(args: Array<String>) {
    runApplication<AccountBApplication>(*args)
}