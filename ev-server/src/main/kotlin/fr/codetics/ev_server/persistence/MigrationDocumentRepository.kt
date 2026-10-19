package fr.codetics.ev_server.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface MigrationDocumentRepository : JpaRepository<MigrationDocumentEntity, MigrationDocumentKey>
