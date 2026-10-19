package fr.codetics.ev_server.ocpi

import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.models.entities.OcpiObjectEntity
import fr.codetics.ev_server.models.entities.OcpiObjectKey
import fr.codetics.ev_server.models.entities.PartnerEntity
import fr.codetics.ev_server.repositories.OcpiObjectRepository
import fr.codetics.ev_server.repositories.PartnerRepository
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.time.Instant
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ObjectNode

@Service
class OcpiCatalog(
    private val db: Database,
    private val objects: OcpiObjectRepository,
    private val partners: PartnerRepository,
    @param:Value("\${app.ocpi.public-url}") private val publicUrl: String,
) {
    val modules = setOf("locations", "sessions", "cdrs", "tariffs", "tokens")

    fun handle(
        partner: PartnerEntity,
        module: String,
        objectPath: String,
        request: HttpServletRequest,
        response: HttpServletResponse,
        body: JsonNode?,
    ): Any {
        if (module !in modules) {
            throw OcpiFault(2001, "Module non pris en charge.", HttpStatus.NOT_FOUND)
        }

        val path = objectPath.trim('/')
        val parts = path.split('/').filter(String::isNotBlank)
        require(parts.all { part -> part.length <= 64 && part.matches(Regex("[A-Za-z0-9_.:*-]+")) })
        val sender =
            (partner.localRole == "cpo" && module != "tokens") ||
                (partner.localRole == "emsp" && module == "tokens")
        val direction = if (sender) "SENDER" else "RECEIVER"

        if (request.method == "GET") {
            if (parts.isEmpty()) {
                if (!sender) {
                    throw OcpiFault(
                        2001,
                        "L’identifiant de l’objet est requis.",
                        HttpStatus.METHOD_NOT_ALLOWED,
                    )
                }
                return list(partner, module, direction, request, response)
            }

            if (!sender && module != "cdrs") {
                verifyOwner(partner, parts)
            }

            val rootPath =
                if (sender || module == "cdrs") parts.first() else parts.take(3).joinToString("/")
            val root =
                get(partner, module, direction, rootPath)
                    ?: throw OcpiFault(2003, "Objet introuvable.", HttpStatus.NOT_FOUND)
            val children =
                if (module == "locations") parts.drop(if (sender) 1 else 3) else emptyList()
            return ocpi(nested(root, children))
        }

        if (sender) {
            throw OcpiFault(
                2001,
                "Les objets reçus sont accessibles en lecture seule sur cette interface.",
                HttpStatus.METHOD_NOT_ALLOWED,
            )
        }

        if (module == "cdrs") {
            return cdr(partner, path, request.method, requireNotNull(body), response)
        }

        verifyOwner(partner, parts)

        if (request.method == "DELETE") {
            if (module != "tariffs") {
                throw OcpiFault(
                    2001,
                    "Ce module ne prend pas en charge la suppression.",
                    HttpStatus.METHOD_NOT_ALLOWED,
                )
            }
            objects.deleteById(key(partner, module, direction, path))
            return ocpi()
        }

        if (request.method !in setOf("PUT", "PATCH")) {
            throw OcpiFault(2001, "Méthode non prise en charge.", HttpStatus.METHOD_NOT_ALLOWED)
        }

        val input = requireNotNull(body)
        require(input.isObject && input.hasNonNull("last_updated"))
        val updatedAt = Instant.parse(input.path("last_updated").asText())
        val rootPath = parts.take(3).joinToString("/")

        return db.atomic {
            partners.findForUpdate(partner.id)
            val previous = get(partner, module, direction, rootPath)

            if (
                previous != null &&
                    Instant.parse(previous.path("last_updated").asText()).isAfter(updatedAt)
            ) {
                throw OcpiFault(
                    2001,
                    "La mise à jour est antérieure à l’objet enregistré.",
                    HttpStatus.CONFLICT,
                )
            }

            val updated = replaceOrPatch(module, parts, request.method, previous, input)
            objects.save(
                OcpiObjectEntity(
                    key = key(partner, module, direction, rootPath),
                    data = db.encode(updated),
                    lastUpdated = updatedAt,
                )
            )
            ocpi()
        }
    }

    private fun list(
        partner: PartnerEntity,
        module: String,
        direction: String,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): Any {
        val offset = request.getParameter("offset")?.toInt() ?: 0
        val limit = request.getParameter("limit")?.toInt() ?: 50
        require(offset >= 0 && limit in 1..500)
        val from = request.getParameter("date_from")?.let(Instant::parse) ?: Instant.EPOCH
        val to =
            request.getParameter("date_to")?.let(Instant::parse)
                ?: Instant.parse("9999-01-01T00:00:00Z")
        require(!from.isAfter(to))

        val specification =
            Specification<OcpiObjectEntity> { root, _, criteria ->
                val identity = root.get<OcpiObjectKey>("key")
                criteria.and(
                    criteria.equal(identity.get<java.util.UUID>("tenantId"), partner.tenantId),
                    criteria.equal(identity.get<java.util.UUID>("partnerId"), partner.id),
                    criteria.equal(identity.get<String>("module"), module),
                    criteria.equal(identity.get<String>("direction"), direction),
                    criteria.greaterThanOrEqualTo(root.get("lastUpdated"), from),
                    criteria.lessThan(root.get("lastUpdated"), to),
                )
            }
        // Les décalages OCPI ne sont pas nécessairement multiples de la taille
        // de page : lire aussi la page partielle supplémentaire.
        val firstPage = offset / limit
        val skip = offset % limit
        val first =
            objects.findAll(
                specification,
                PageRequest.of(firstPage, limit, Sort.by("key.objectPath")),
            )
        val rows = first.content.drop(skip).toMutableList()

        if (skip > 0 && first.hasNext()) {
            val second =
                objects.findAll(
                    specification,
                    PageRequest.of(firstPage + 1, limit, Sort.by("key.objectPath")),
                )
            rows.addAll(second.content.take(skip))
        }

        response.setHeader("X-Total-Count", first.totalElements.toString())
        response.setHeader("X-Limit", limit.toString())

        if (offset + rows.size < first.totalElements) {
            val url =
                "${base(partner)}/$module/?offset=${offset + limit}&limit=$limit&date_from=$from&date_to=$to"
            response.setHeader("Link", "<$url>; rel=\"next\"")
        }

        return ocpi(rows.map { row -> db.decode(row.data) })
    }

    private fun replaceOrPatch(
        module: String,
        parts: List<String>,
        method: String,
        previous: JsonNode?,
        input: JsonNode,
    ): JsonNode {
        if (module == "locations" && parts.size > 3) {
            val root =
                previous?.deepCopy() as? ObjectNode
                    ?: throw OcpiFault(2003, "Le site parent est inconnu.", HttpStatus.NOT_FOUND)
            val target = nested(root, parts.drop(3)) as ObjectNode

            if (method == "PUT") {
                require(input.path(if (parts.size == 4) "uid" else "id").asText() == parts.last())
                target.removeAll()
            }

            target.setAll(input as ObjectNode)
            root.put("last_updated", input.path("last_updated").asText())

            if (parts.size == 5) {
                val evse = nested(root, listOf(parts[3])) as ObjectNode
                evse.put("last_updated", input.path("last_updated").asText())
            }

            return root
        }

        if (method == "PATCH") {
            val updated =
                previous?.deepCopy() as? ObjectNode
                    ?: throw OcpiFault(2003, "Objet introuvable.", HttpStatus.NOT_FOUND)
            return updated.setAll(input as ObjectNode)
        }

        require(input.path(if (module == "tokens") "uid" else "id").asText() == parts[2])
        return input
    }

    private fun cdr(
        partner: PartnerEntity,
        path: String,
        method: String,
        data: JsonNode,
        response: HttpServletResponse,
    ): Any {
        if (method != "POST" || path.isNotEmpty()) {
            throw OcpiFault(
                2001,
                "Les relevés de recharge sont immuables.",
                HttpStatus.METHOD_NOT_ALLOWED,
            )
        }

        val required =
            listOf(
                "id",
                "start_date_time",
                "stop_date_time",
                "auth_id",
                "auth_method",
                "location",
                "currency",
                "charging_periods",
                "total_cost",
                "total_energy",
                "total_time",
                "last_updated",
            )
        require(required.all(data::hasNonNull))
        val id = data.path("id").asText()
        require(id.matches(Regex("[A-Za-z0-9_.:-]{1,36}")))
        val updatedAt = Instant.parse(data.path("last_updated").asText())

        db.atomic {
            partners.findForUpdate(partner.id)
            val previous = get(partner, "cdrs", "RECEIVER", id)

            if (previous != null && previous != data) {
                throw OcpiFault(
                    2004,
                    "Un relevé de recharge ne peut pas être remplacé.",
                    HttpStatus.CONFLICT,
                )
            }

            if (previous == null) {
                objects.save(
                    OcpiObjectEntity(
                        key(partner, "cdrs", "RECEIVER", id),
                        db.encode(data),
                        updatedAt,
                    )
                )
            }

            true
        }

        response.setHeader("Location", "${base(partner)}/cdrs/$id")
        return ocpi()
    }

    private fun verifyOwner(partner: PartnerEntity, parts: List<String>) {
        if (parts.size < 3 || parts[0] != partner.countryCode || parts[1] != partner.partyId) {
            throw OcpiFault(
                2001,
                "Le propriétaire de l’objet ne correspond pas au partenaire authentifié.",
                HttpStatus.FORBIDDEN,
            )
        }
    }

    private fun get(
        partner: PartnerEntity,
        module: String,
        direction: String,
        path: String,
    ): JsonNode? {
        val record = objects.findById(key(partner, module, direction, path)).orElse(null)
        return record?.data?.let(db::decode)
    }

    private fun key(
        partner: PartnerEntity,
        module: String,
        direction: String,
        path: String,
    ): OcpiObjectKey {
        return OcpiObjectKey(partner.tenantId, partner.id, module, direction, path)
    }

    private fun nested(root: JsonNode, parts: List<String>): JsonNode {
        if (parts.isEmpty()) {
            return root
        }
        require(parts.size <= 2)
        var current =
            root.path("evses").firstOrNull { evse -> evse.path("uid").asText() == parts[0] }
                ?: throw OcpiFault(2003, "Point de charge introuvable.", HttpStatus.NOT_FOUND)

        if (parts.size == 2) {
            current =
                current.path("connectors").firstOrNull { connector ->
                    connector.path("id").asText() == parts[1]
                } ?: throw OcpiFault(2003, "Connecteur introuvable.", HttpStatus.NOT_FOUND)
        }

        return current
    }

    private fun base(partner: PartnerEntity): String {
        return "${publicUrl.trimEnd('/')}/ocpi/${partner.localRole}/2.1.1"
    }
}
