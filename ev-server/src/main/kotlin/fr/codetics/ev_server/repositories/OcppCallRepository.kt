package fr.codetics.ev_server.repositories

import fr.codetics.ev_server.models.entities.OcppCallEntity
import fr.codetics.ev_server.models.entities.OcppCallKey
import org.springframework.data.jpa.repository.JpaRepository

interface OcppCallRepository : JpaRepository<OcppCallEntity, OcppCallKey>
