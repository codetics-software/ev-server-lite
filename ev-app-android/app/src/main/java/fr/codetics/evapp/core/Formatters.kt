// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.evapp.core

import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Locale

object Formatters {
    private const val NONE = "—"

    private fun zoned(instant: Instant) = instant.atZone(ZoneId.systemDefault())

    fun price(value: BigDecimal?, currency: String?): String {
        if (value == null) return NONE
        val format = NumberFormat.getCurrencyInstance(Locale.getDefault())
        format.currency = Currency.getInstance(currency ?: "EUR")
        format.minimumFractionDigits = 2
        format.maximumFractionDigits = 4
        return format.format(value)
    }

    fun minor(value: Int?, currency: String): String {
        if (value == null) return NONE
        val format = NumberFormat.getCurrencyInstance(Locale.getDefault())
        format.currency = Currency.getInstance(currency)
        return format.format(BigDecimal(value).movePointLeft(2))
    }

    private fun number(value: Double, fraction: Int): String =
        NumberFormat.getNumberInstance(Locale.getDefault()).apply { maximumFractionDigits = fraction }.format(value)

    fun energy(kwh: Double?) = kwh?.let { "${number(it, 2)} kWh" } ?: NONE

    fun power(kw: Double?) = kw?.let { "${number(it, 1)} kW" } ?: NONE

    fun duration(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        return if (hours > 0) "$hours h ${"%02d".format(minutes)} min" else "$minutes min"
    }

    fun distance(meters: Double): String =
        if (meters < 1000) "${meters.toInt()} m" else "${number(meters / 1000, 1)} km"

    fun date(instant: Instant): String = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(zoned(instant))

    fun dateTime(instant: Instant): String =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).format(zoned(instant))
}
