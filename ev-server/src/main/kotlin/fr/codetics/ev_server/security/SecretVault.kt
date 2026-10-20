package fr.codetics.ev_server.security

import fr.codetics.ev_server.core.AppProperties
import fr.codetics.ev_server.core.secureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException

@Component
class SecretVault(private val properties: AppProperties) {
    private val keyBytes =
        runCatching { Base64.getDecoder().decode(properties.encryptionKey) }
            .getOrDefault(byteArrayOf())

    val configured = keyBytes.size == 32

    private val secret by lazy {
        if (!configured) {
            throw ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Configurez APP_ENCRYPTION_KEY avec une clé aléatoire de 32 octets encodée en Base64.",
            )
        }
        SecretKeySpec(keyBytes, "AES")
    }

    fun encrypt(value: String): String {
        val nonce = ByteArray(12)
        secureRandom.nextBytes(nonce)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secret, GCMParameterSpec(128, nonce))

        return Base64.getEncoder().encodeToString(nonce + cipher.doFinal(value.toByteArray()))
    }

    fun decrypt(value: String): String {
        val bytes = Base64.getDecoder().decode(value)
        require(bytes.size >= 28)
        val nonce = bytes.copyOfRange(0, 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secret, GCMParameterSpec(128, nonce))

        return String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)))
    }
}
