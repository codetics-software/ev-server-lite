package fr.codetics.ev_server.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface OcppCallRepository : JpaRepository<OcppCallEntity, OcppCallKey>
