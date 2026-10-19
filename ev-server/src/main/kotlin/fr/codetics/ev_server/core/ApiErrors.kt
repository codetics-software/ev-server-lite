package fr.codetics.ev_server.core

import com.stripe.exception.StripeException
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
    @ExceptionHandler(
        org.springframework.http.converter.HttpMessageNotReadableException::class,
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException::class,
        org.springframework.web.bind.MissingRequestHeaderException::class,
    )
    fun malformed(): ProblemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "Les paramètres de la demande sont invalides ou incomplets.",
        )

    @ExceptionHandler(ResponseStatusException::class)
    fun status(error: ResponseStatusException): ProblemDetail {
        return ProblemDetail.forStatusAndDetail(
            error.statusCode,
            error.reason ?: "Demande refusée.",
        )
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun invalid(error: IllegalArgumentException): ProblemDetail {
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            error.message?.takeUnless {
                it == "Failed requirement." || it.startsWith("For input string")
            } ?: "Demande invalide.",
        )
    }

    @ExceptionHandler(
        DataIntegrityViolationException::class,
        ObjectOptimisticLockingFailureException::class,
    )
    fun conflict(): ProblemDetail {
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT,
            "Cette opération entre en conflit avec un élément ou une relation existante.",
        )
    }

    @ExceptionHandler(StripeException::class)
    fun stripe(): ProblemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_GATEWAY,
            "Stripe n’a pas pu traiter cette demande. Réessayez dans quelques instants.",
        )

    @ExceptionHandler(AccessDeniedException::class)
    fun forbidden(): ProblemDetail {
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.FORBIDDEN,
            "Vos droits ne permettent pas cette action.",
        )
    }
}
