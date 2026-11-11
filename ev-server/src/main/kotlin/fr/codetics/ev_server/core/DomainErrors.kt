package fr.codetics.ev_server.core

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import org.springframework.http.HttpStatus

class InvalidRequest(override val message: String) : IllegalArgumentException(message)

class CodedProblem(val status: HttpStatus, override val message: String, val code: String) : RuntimeException(message)

class NotFound(override val message: String = msg("error.not_found")) : RuntimeException(message)

@OptIn(ExperimentalContracts::class)
inline fun ensure(condition: Boolean, message: () -> String) {
    contract { returns() implies condition }
    if (!condition) {
        throw InvalidRequest(message())
    }
}

fun invalid(message: String): Nothing = throw InvalidRequest(message)
