package fr.codetics.ev_server

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication(proxyBeanMethods = false)
@ConfigurationPropertiesScan
class EvServerApplication

fun main(args: Array<String>) {
    runApplication<EvServerApplication>(*args)
}
