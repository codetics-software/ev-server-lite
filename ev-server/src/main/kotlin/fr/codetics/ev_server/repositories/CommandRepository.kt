package fr.codetics.ev_server.repositories

import fr.codetics.ev_server.models.commands.CommandState
import fr.codetics.ev_server.models.entities.CommandEntity
import java.time.Instant
import java.util.UUID
import org.springframework.data.domain.Pageable

interface CommandRepository : StoreRepository<CommandEntity, UUID> {
    fun findAllByTenantIdOrderByCreatedAtDesc(tenant: UUID, pageable: Pageable): List<CommandEntity>

    fun findAllByStateInAndDeadlineBefore(
        states: Collection<CommandState>,
        deadline: Instant,
    ): List<CommandEntity>

    fun findAllByTenantIdAndStationIdAndStateAndDeadlineAfterOrderByCreatedAt(
        tenant: UUID,
        station: String,
        state: CommandState,
        now: Instant,
        pageable: Pageable,
    ): List<CommandEntity>
}
