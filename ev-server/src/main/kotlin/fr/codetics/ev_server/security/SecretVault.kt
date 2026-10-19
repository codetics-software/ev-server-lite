package fr.codetics.ev_server.security

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class SecretVault(@param:Value("\${app.encryption-key}") private val key: String) {
    val configured
        get() = runCatching { Base64.getDecoder().decode(key).size == 32 }.getOrDefault(false)

    private fun secret(): SecretKeySpec {
        val bytes =
            try {
                Base64.getDecoder().decode(key)
            } catch (_: IllegalArgumentException) {
                byteArrayOf()
            }
        check(bytes.size == 32) {
            "Configurez APP_ENCRYPTION_KEY avec une clé aléatoire de 32 octets encodée en Base64."
        }

        return SecretKeySpec(bytes, "AES")
    }

    fun encrypt(value: String): String {
        val nonce = ByteArray(12)
        SecureRandom().nextBytes(nonce)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secret(), GCMParameterSpec(128, nonce))

        return Base64.getEncoder().encodeToString(nonce + cipher.doFinal(value.toByteArray()))
    }

    fun decrypt(value: String): String {
        val bytes = Base64.getDecoder().decode(value)
        require(bytes.size >= 28)
        val nonce = bytes.copyOfRange(0, 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secret(), GCMParameterSpec(128, nonce))

        return String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)))
    }
}
