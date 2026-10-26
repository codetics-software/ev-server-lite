package fr.codetics.ev_server.ocpp

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField

object OcppTime {
    private val local =
        DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .append(DateTimeFormatter.ISO_LOCAL_DATE)
            .optionalStart()
            .appendLiteral('T')
            .optionalEnd()
            .optionalStart()
            .appendLiteral(' ')
            .optionalEnd()
            .appendValue(ChronoField.HOUR_OF_DAY, 2)
            .appendLiteral(':')
            .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
            .optionalStart()
            .appendLiteral(':')
            .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
            .optionalStart()
            .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
            .optionalEnd()
            .optionalEnd()
            .toFormatter()

    private val offset =
        DateTimeFormatterBuilder()
            .append(local)
            .optionalStart()
            .appendOffset("+HH:MM", "Z")
            .optionalEnd()
            .optionalStart()
            .appendOffset("+HHMM", "Z")
            .optionalEnd()
            .optionalStart()
            .appendOffset("+HH", "Z")
            .optionalEnd()
            .toFormatter()

    fun parse(value: String): Instant {
        val text = value.trim()
        require(text.length in 10..40)

        runCatching { return Instant.parse(text) }
        runCatching { return OffsetDateTime.parse(text, offset).toInstant() }
        return LocalDateTime.parse(text, local).toInstant(ZoneOffset.UTC)
    }

    fun parseOrNull(value: String?): Instant? = value?.let { runCatching { parse(it) }.getOrNull() }
}
