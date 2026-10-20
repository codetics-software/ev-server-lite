package fr.codetics.ev_server.repositories

import fr.codetics.ev_server.models.entities.IntegrationSecretEntity
import java.util.UUID

interface IntegrationSecretRepository : StoreRepository<IntegrationSecretEntity, UUID>
