package fr.codetics.ev_server

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(proxyBeanMethods = false) class EvServerApplication

fun main(args: Array<String>) {
    runApplication<EvServerApplication>(*args)
}
