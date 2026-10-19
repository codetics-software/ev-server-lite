package fr.codetics.ev_server.repositories

import fr.codetics.ev_server.models.entities.CommandEntity
import jakarta.persistence.LockModeType
import java.time.Instant
import java.util.UUID
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface CommandRepository : JpaRepository<CommandEntity, UUID> {
    fun findAllByTenantIdOrderByCreatedAtDesc(tenant: UUID, pageable: Pageable): List<CommandEntity>

    fun findAllByStateInAndDeadlineBefore(
        states: Collection<String>,
        deadline: Instant,
    ): List<CommandEntity>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        select command from CommandEntity command
        where command.tenantId = :tenant
          and command.stationId = :station
          and command.state = 'QUEUED'
          and command.deadline > :now
        order by command.createdAt
        """
    )
    fun findPending(
        @Param("tenant") tenant: UUID,
        @Param("station") station: String,
        @Param("now") now: Instant,
        pageable: Pageable,
    ): List<CommandEntity>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        "select c from CommandEntity c where c.id = :id and c.tenantId = :tenant and c.stationId = :station"
    )
    fun findForUpdate(
        @Param("id") id: UUID,
        @Param("tenant") tenant: UUID,
        @Param("station") station: String,
    ): CommandEntity?
}
