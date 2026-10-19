package fr.codetics.ev_server.security

import java.time.Duration
import org.springframework.http.ResponseCookie

fun sessionCookie(value: String, secure: Boolean, clear: Boolean = false): ResponseCookie {
    val duration = if (clear) Duration.ZERO else Duration.ofHours(24)

    return ResponseCookie.from("EV_SESSION", value)
        .httpOnly(true)
        .secure(secure)
        .sameSite("Lax")
        .path("/")
        .maxAge(duration)
        .build()
}
