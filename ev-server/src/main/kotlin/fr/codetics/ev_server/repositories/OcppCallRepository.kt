package fr.codetics.ev_server.repositories

import fr.codetics.ev_server.models.entities.OcppCallEntity
import fr.codetics.ev_server.models.entities.OcppCallKey

interface OcppCallRepository : StoreRepository<OcppCallEntity, OcppCallKey>
