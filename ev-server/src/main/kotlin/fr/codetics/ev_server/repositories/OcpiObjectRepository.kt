package fr.codetics.ev_server.repositories

import fr.codetics.ev_server.models.entities.OcpiObjectEntity
import fr.codetics.ev_server.models.entities.OcpiObjectKey
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor

interface OcpiObjectRepository :
    JpaRepository<OcpiObjectEntity, OcpiObjectKey>, JpaSpecificationExecutor<OcpiObjectEntity>
