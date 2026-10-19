package fr.codetics.ev_server.core

import com.stripe.exception.StripeException
import org.slf4j.LoggerFactory
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
    private val log = LoggerFactory.getLogger(javaClass)

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

    @ExceptionHandler(NotFound::class)
    fun notFound(error: NotFound): ProblemDetail {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, error.message)
    }

    @ExceptionHandler(InvalidRequest::class)
    fun invalid(error: InvalidRequest): ProblemDetail {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, error.message)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun malformedArgument(error: IllegalArgumentException): ProblemDetail {
        log.debug("Demande refusée", error)
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Demande invalide.")
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
