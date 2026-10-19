package fr.codetics.ev_server.api

data class PageResponse<T>(val items: List<T>, val total: Long)

data class MutationResponse(val success: Boolean = true)
