package com.jihyeong.lab.concurrency

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ConcurrencyLabApplication

fun main(args: Array<String>) {
	runApplication<ConcurrencyLabApplication>(*args)
}

