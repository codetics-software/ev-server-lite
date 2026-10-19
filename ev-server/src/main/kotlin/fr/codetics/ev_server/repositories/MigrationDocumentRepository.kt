package fr.codetics.ev_server.repositories

import fr.codetics.ev_server.models.entities.MigrationDocumentEntity
import fr.codetics.ev_server.models.entities.MigrationDocumentKey

interface MigrationDocumentRepository :
    StoreRepository<MigrationDocumentEntity, MigrationDocumentKey>
