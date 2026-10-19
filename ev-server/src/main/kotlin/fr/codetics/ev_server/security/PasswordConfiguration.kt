package fr.codetics.ev_server.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.DelegatingPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder

@Configuration(proxyBeanMethods = false)
class PasswordConfiguration {
    @Bean
    fun passwordEncoder(): PasswordEncoder {
        val legacy = BCryptPasswordEncoder(12)
        val encoders =
            mapOf<String, PasswordEncoder>(
                "pbkdf2@SpringSecurity_v5_8" to
                    Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8(),
                "bcrypt" to legacy,
            )
        val encoder = DelegatingPasswordEncoder("pbkdf2@SpringSecurity_v5_8", encoders)
        encoder.setDefaultPasswordEncoderForMatches(legacy)

        return encoder
    }
}
