package fr.codetics.ev_server.core

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import java.time.Clock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

@Configuration(proxyBeanMethods = false)
@EnableScheduling
class AppConfiguration {
    @Bean fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun openApi(): OpenAPI {
        val contact = Contact().name("Kevin Vavelin — Codetics").url("https://codetics.fr")
        val information =
            Info()
                .title("EV Supervision Lite")
                .version("v1")
                .description("Interface de programmation de la supervision Codetics")
                .contact(contact)

        return OpenAPI().info(information)
    }
}
