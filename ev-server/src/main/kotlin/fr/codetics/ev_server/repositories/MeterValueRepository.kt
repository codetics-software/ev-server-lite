package fr.codetics.ev_server.repositories

import fr.codetics.ev_server.models.entities.MeterValueEntity
import org.springframework.data.jpa.repository.JpaRepository

interface MeterValueRepository : JpaRepository<MeterValueEntity, Long>
