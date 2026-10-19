package fr.codetics.ev_server.ocpi

import fr.codetics.ev_server.core.AppProperties
import fr.codetics.ev_server.models.entities.PartnerEntity
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.time.Instant
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice
import tools.jackson.databind.JsonNode

fun ocpi(data: Any? = null, status: Int = 1000, message: String = "Success"): Map<String, Any?> {
    return mapOf(
        "data" to data,
        "status_code" to status,
        "status_message" to message,
        "timestamp" to Instant.now().toString(),
    )
}

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = ["fr.codetics.ev_server.ocpi"])
class OcpiErrors {
    @ExceptionHandler(OcpiFault::class)
    fun fault(error: OcpiFault): ResponseEntity<Any> {
        return ResponseEntity.status(error.http)
            .body(ocpi(status = error.status, message = error.message))
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun invalid(): ResponseEntity<Any> {
        return ResponseEntity.badRequest()
            .body(ocpi(status = 2001, message = "Paramètres OCPI invalides."))
    }
}

@RestController
@RequestMapping("/ocpi/{role}")
class OcpiController(
    private val transport: OcpiTransport,
    private val credentials: OcpiCredentials,
    private val catalog: OcpiCatalog,
    private val properties: AppProperties,
) {
    private fun partner(
        role: String,
        request: HttpServletRequest,
        registered: Boolean = true,
    ): PartnerEntity {
        if (role !in setOf("cpo", "emsp")) {
            throw OcpiFault(2001, "Rôle inconnu.", HttpStatus.NOT_FOUND)
        }
        val partner = transport.authenticate(request.getHeader("Authorization"), role)

        if (registered && !partner.registered) {
            throw OcpiFault(2001, "Le partenaire n’est pas enregistré.", HttpStatus.FORBIDDEN)
        }

        return partner
    }

    private fun base(role: String): String {
        return "${properties.ocpi.publicUrl.trimEnd('/')}/ocpi/$role"
    }

    @GetMapping("", "/", "/versions", "/versions/")
    fun versions(@PathVariable role: String, request: HttpServletRequest): Any {
        partner(role, request, false)
        return ocpi(listOf(mapOf("version" to "2.1.1", "url" to "${base(role)}/2.1.1/")))
    }

    @GetMapping("/2.1.1", "/2.1.1/")
    fun endpoints(@PathVariable role: String, request: HttpServletRequest): Any {
        partner(role, request, false)
        val endpoints =
            (catalog.modules + "credentials").sorted().map { module ->
                mapOf("identifier" to module, "url" to "${base(role)}/2.1.1/$module/")
            }

        return ocpi(mapOf("version" to "2.1.1", "endpoints" to endpoints))
    }

    @RequestMapping(
        "/2.1.1/credentials",
        "/2.1.1/credentials/",
        method = [RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE],
    )
    fun credentials(
        @PathVariable role: String,
        request: HttpServletRequest,
        @RequestBody(required = false) body: JsonNode?,
    ): Any {
        val partner = partner(role, request, false)
        return credentials.exchange(partner, request.method, body)
    }

    @RequestMapping(
        "/2.1.1/{module}",
        "/2.1.1/{module}/",
        "/2.1.1/{module}/{*objectPath}",
        method =
            [
                RequestMethod.GET,
                RequestMethod.PUT,
                RequestMethod.PATCH,
                RequestMethod.POST,
                RequestMethod.DELETE,
            ],
    )
    fun objects(
        @PathVariable role: String,
        @PathVariable module: String,
        @PathVariable(required = false) objectPath: String?,
        request: HttpServletRequest,
        response: HttpServletResponse,
        @RequestBody(required = false) body: JsonNode?,
    ): Any {
        val partner = partner(role, request)
        return catalog.handle(partner, module, objectPath.orEmpty(), request, response, body)
    }
}
