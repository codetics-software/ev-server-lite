package fr.codetics.ev_server.api

data class RecordInput(
    val name: String,
    val data: Map<String, Any?> = emptyMap(),
    val revision: Long? = null
)
