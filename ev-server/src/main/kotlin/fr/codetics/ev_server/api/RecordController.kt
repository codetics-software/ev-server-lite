package fr.codetics.ev_server.api

import fr.codetics.ev_server.core.AppPrincipal
import java.util.UUID
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/records/{kind}")
@PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
class RecordController(private val records: RecordService) {
    @GetMapping
    fun list(
        @PathVariable kind: String,
        @AuthenticationPrincipal user: AppPrincipal,
        @RequestParam(defaultValue = "") search: String,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(defaultValue = "50") limit: Int
    ) = records.list(kind, user, search, offset, limit)

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    fun create(@PathVariable kind: String, @RequestBody body: RecordInput, @AuthenticationPrincipal user: AppPrincipal) =
        records.create(kind, body, user)

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    fun update(@PathVariable kind: String, @PathVariable id: UUID, @RequestBody body: RecordInput, @AuthenticationPrincipal user: AppPrincipal) =
        records.update(kind, id, body, user)

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    fun delete(@PathVariable kind: String, @PathVariable id: UUID, @AuthenticationPrincipal user: AppPrincipal) =
        records.delete(kind, id, user)
}
