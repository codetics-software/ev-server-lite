package fr.codetics.ev_server.services.billing

import java.time.Duration

object ChargingTimeouts {
    val START_WINDOW: Duration = Duration.ofMinutes(5)
    val METER_CLOCK_SKEW: Duration = Duration.ofMinutes(5)
    val MAX_SESSION: Duration = Duration.ofHours(24)
}
