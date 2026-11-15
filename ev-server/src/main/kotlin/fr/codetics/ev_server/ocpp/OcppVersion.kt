package fr.codetics.ev_server.ocpp

import fr.codetics.ev_server.models.entities.StationEntity

enum class OcppVersion(val label: String, val subprotocol: String) {
    V16("1.6", "ocpp1.6"),
    V201("2.0.1", "ocpp2.0.1");

    companion object {
        const val AUTO = "AUTO"
        val PROTOCOLS = setOf(AUTO, V16.label, V201.label)

        fun fromLabel(label: String?): OcppVersion? = entries.firstOrNull { it.label == label }

        fun fromSubprotocol(name: String?): OcppVersion? = entries.firstOrNull { it.subprotocol.equals(name, true) }

        fun allowed(protocol: String?): List<OcppVersion> =
            fromLabel(protocol)?.let(::listOf) ?: listOf(V201, V16)

        fun negotiate(protocol: String?, offered: List<String>): OcppVersion? {
            val allowed = allowed(protocol)
            if (offered.isEmpty()) return fromLabel(protocol) ?: V16
            return offered.firstNotNullOfOrNull { name -> fromSubprotocol(name)?.takeIf { it in allowed } }
        }
    }
}

fun StationEntity.ocpp(): OcppVersion? =
    OcppVersion.fromLabel(ocppProtocol) ?: OcppVersion.fromLabel(ocppVersion).takeIf { lastSeen != null }
