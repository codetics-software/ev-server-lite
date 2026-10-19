package fr.codetics.ev_server.core

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ApiErrorsTests {
    private val errors = ApiErrors()

    @Test
    fun `seuls les messages de validation sont transmis au client`() {
        assertEquals(
            "Renseignez le nom.",
            errors.invalid(InvalidRequest("Renseignez le nom.")).detail,
        )
        assertEquals(
            "Demande invalide.",
            errors.malformedArgument(IllegalArgumentException("Invalid UUID string: x")).detail,
        )
        assertEquals(
            "Demande invalide.",
            errors.malformedArgument(IllegalArgumentException("Required value was null.")).detail,
        )
    }

    @Test
    fun `une ressource absente renvoie une erreur 404`() {
        assertEquals(404, errors.notFound(NotFound("Borne introuvable.")).status)
    }
}
