package fr.codetics.ev_server.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor

interface OcpiObjectRepository : JpaRepository<OcpiObjectEntity, OcpiObjectKey>, JpaSpecificationExecutor<OcpiObjectEntity>
