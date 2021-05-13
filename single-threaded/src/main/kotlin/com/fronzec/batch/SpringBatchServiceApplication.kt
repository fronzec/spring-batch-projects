package com.fronzec.batch

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class SpringBatchServiceApplication

fun main(args: Array<String>) {
    runApplication<SpringBatchServiceApplication>(*args)
}
