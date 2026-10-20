package fr.codetics.ev_server.ocpp

import java.time.Duration

object VehicleIds {
    const val PREFIX = "VID:"
    const val TOKEN_TYPE = "VID"
    val AUTHORIZE_WINDOW: Duration = Duration.ofSeconds(30)
    val START_WINDOW: Duration = Duration.ofMinutes(2)
    val ENROLLMENT_WINDOW: Duration = Duration.ofHours(24)
    const val MAXIMUM_PER_VEHICLE = 5
    val PLUGGED = setOf("Preparing", "Charging", "SuspendedEV", "SuspendedEVSE", "Finishing")

    private val MAC = Regex("[0-9A-F]{12}")

    fun isVid(uid: String) = uid.startsWith(PREFIX, ignoreCase = true)

    fun normalize(uid: String): String? {
        if (!isVid(uid)) return null
        val mac = uid.substring(PREFIX.length).replace(":", "").replace("-", "").uppercase()
        if (!mac.matches(MAC)) return null
        if (mac == "000000000000" || mac == "FFFFFFFFFFFF") return null
        if (mac.substring(0, 2).toInt(16) and 1 == 1) return null
        return PREFIX + mac
    }
}
