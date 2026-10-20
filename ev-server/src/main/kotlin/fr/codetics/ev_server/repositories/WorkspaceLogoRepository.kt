package fr.codetics.ev_server.repositories

import fr.codetics.ev_server.models.entities.WorkspaceLogoEntity
import java.util.UUID

interface WorkspaceLogoRepository : StoreRepository<WorkspaceLogoEntity, UUID>
