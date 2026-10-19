package fr.codetics.ev_server.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface MeterValueRepository : JpaRepository<MeterValueEntity, Long>
