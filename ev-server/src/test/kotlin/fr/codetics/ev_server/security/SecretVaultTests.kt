package fr.codetics.ev_server.security

import fr.codetics.ev_server.core.AppProperties
import java.util.Base64
import javax.crypto.AEADBadTagException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException

class SecretVaultTests {
    private val vault = SecretVault(AppProperties(encryptionKey = "A".repeat(43) + "="))

    @Test
    fun `le chiffrement est reversible et utilise un nonce unique`() {
        val first = vault.encrypt("secret du portail")
        val second = vault.encrypt("secret du portail")

        assertNotEquals(first, second)
        assertEquals("secret du portail", vault.decrypt(first))
        assertEquals("secret du portail", vault.decrypt(second))
    }

    @Test
    fun `un message altere est refuse`() {
        val bytes = Base64.getDecoder().decode(vault.encrypt("secret"))
        bytes[bytes.size - 1] = (bytes.last().toInt() xor 1).toByte()

        assertThrows(AEADBadTagException::class.java) {
            vault.decrypt(Base64.getEncoder().encodeToString(bytes))
        }
    }

    @Test
    fun `une cle absente ou mal formee desactive le coffre`() {
        listOf("", "pas-du-base64", Base64.getEncoder().encodeToString(ByteArray(16))).forEach {
            val unconfigured = SecretVault(AppProperties(encryptionKey = it))

            assertFalse(unconfigured.configured)
            assertThrows(ResponseStatusException::class.java) { unconfigured.encrypt("secret") }
        }
    }
}
