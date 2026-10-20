package fr.codetics.ev_server.repositories

import fr.codetics.ev_server.models.entities.SmartChargingPlanEntity
import java.util.UUID

interface SmartChargingPlanRepository : StoreRepository<SmartChargingPlanEntity, UUID>
