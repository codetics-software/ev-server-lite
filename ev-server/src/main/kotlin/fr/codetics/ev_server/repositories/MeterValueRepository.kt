package fr.codetics.ev_server.repositories

import fr.codetics.ev_server.models.entities.MeterValueEntity

interface MeterValueRepository : StoreRepository<MeterValueEntity, Long>
