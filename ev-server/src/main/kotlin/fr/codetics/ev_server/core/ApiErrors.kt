package fr.codetics.ev_server.core

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException

@RestControllerAdvice
class ApiErrors {
    @ExceptionHandler(ResponseStatusException::class)
    fun status(error: ResponseStatusException): ProblemDetail {
        return ProblemDetail.forStatusAndDetail(
            error.statusCode,
            error.reason ?: "Request rejected"
        )
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun invalid(error: IllegalArgumentException): ProblemDetail {
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            error.message ?: "Invalid request"
        )
    }

    @ExceptionHandler(DataIntegrityViolationException::class, ObjectOptimisticLockingFailureException::class)
    fun conflict(): ProblemDetail {
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT,
            "This operation conflicts with an existing record or relationship"
        )
    }

    @ExceptionHandler(AccessDeniedException::class)
    fun forbidden(): ProblemDetail {
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.FORBIDDEN,
            "Insufficient permission"
        )
    }
}
