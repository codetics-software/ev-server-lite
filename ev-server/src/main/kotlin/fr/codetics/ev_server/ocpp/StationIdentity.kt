package fr.codetics.ev_server.ocpp

import java.util.UUID

data class StationIdentity(val tenant: UUID, val station: String)
