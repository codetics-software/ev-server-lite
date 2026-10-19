package fr.codetics.ev_server.repositories

import fr.codetics.ev_server.models.entities.StripeEventEntity

interface StripeEventRepository : StoreRepository<StripeEventEntity, String>
