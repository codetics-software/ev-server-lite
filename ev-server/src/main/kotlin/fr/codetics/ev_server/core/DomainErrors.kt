package fr.codetics.ev_server.core

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

class InvalidRequest(override val message: String) : IllegalArgumentException(message)

class NotFound(override val message: String = "Ressource introuvable.") : RuntimeException(message)

@OptIn(ExperimentalContracts::class)
inline fun ensure(condition: Boolean, message: () -> String) {
    contract { returns() implies condition }
    if (!condition) {
        throw InvalidRequest(message())
    }
}

fun invalid(message: String): Nothing = throw InvalidRequest(message)
