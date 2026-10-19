package fr.codetics.ev_server.repositories

import fr.codetics.ev_server.models.entities.MigrationDocumentEntity
import fr.codetics.ev_server.models.entities.MigrationDocumentKey
import org.springframework.data.jpa.repository.JpaRepository

interface MigrationDocumentRepository :
    JpaRepository<MigrationDocumentEntity, MigrationDocumentKey>
