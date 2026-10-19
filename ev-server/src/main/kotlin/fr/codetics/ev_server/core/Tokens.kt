package fr.codetics.ev_server.core

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

fun sha256(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
    return digest.joinToString("") { byte ->
        "%02x".format(byte)
    }
}

fun randomToken(): String {
    val bytes = ByteArray(32)
    SecureRandom().nextBytes(bytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
