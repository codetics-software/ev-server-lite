package fr.codetics.ev_server.repositories

import fr.codetics.ev_server.models.entities.WorkspacePaymentSettings
import java.util.UUID

interface WorkspacePaymentSettingsRepository : StoreRepository<WorkspacePaymentSettings, UUID>
