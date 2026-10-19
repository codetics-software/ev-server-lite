package fr.codetics.ev_server.api

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.core.sha256
import fr.codetics.ev_server.persistence.MigrationDocumentEntity
import fr.codetics.ev_server.persistence.MigrationDocumentKey
import fr.codetics.ev_server.persistence.MigrationDocumentRepository
import java.nio.charset.CharacterCodingException
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import tools.jackson.core.JacksonException

@RestController
@RequestMapping("/api/v1/migrations")
@PreAuthorize("hasRole('ADMIN')")
class MigrationController(
    private val db: Database,
    private val documents: MigrationDocumentRepository
) {
    @PostMapping("/{collection}", consumes = ["application/x-ndjson", "text/plain"])
    fun archive(
        @PathVariable collection: String,
        @RequestBody exportBytes: ByteArray,
        @AuthenticationPrincipal user: AppPrincipal
    ): Map<String, Int> {
        require(collection.matches(Regex("[A-Za-z][A-Za-z0-9_-]{0,79}")))
        require(exportBytes.size <= 1_048_576) {
            "Split the export into batches of at most 1 MiB"
        }
        val export = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(exportBytes))
                .toString()
        } catch (_: CharacterCodingException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Export must contain valid UTF-8")
        }
        val lines = export.lineSequence().filter(String::isNotBlank).toList()
        require(lines.size in 1..10000)

        return db.atomic {
            var imported = 0
            var unchanged = 0

            lines.forEach { line ->
                val document = try {
                    db.json.readTree(line)
                } catch (_: JacksonException) {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Export contains an invalid JSON document")
                }
                require(document.isObject && document.hasNonNull("_id")) {
                    "Every exported document must retain its original _id"
                }

                val identity = db.encode(document.path("_id"))
                val key = MigrationDocumentKey(user.tenantId, collection, identity)
                val fingerprint = sha256(line)
                val existing = documents.findById(key).orElse(null)

                if (existing == null) {
                    documents.save(
                        MigrationDocumentEntity(
                            key = key,
                            sourceSha256 = fingerprint,
                            document = line,
                            rawDocument = line
                        )
                    )
                    imported += 1
                } else if (existing.sourceSha256 == fingerprint) {
                    unchanged += 1
                } else {
                    throw ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "An archived document changed; reconcile it before importing again"
                    )
                }
            }

            db.audit(user.tenantId, user.id.toString(), "MIGRATION_ARCHIVE", collection)
            mapOf("imported" to imported, "unchanged" to unchanged)
        }
    }
}
