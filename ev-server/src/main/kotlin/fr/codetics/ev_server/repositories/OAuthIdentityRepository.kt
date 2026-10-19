package fr.codetics.ev_server.repositories

import fr.codetics.ev_server.models.entities.OAuthIdentityEntity
import fr.codetics.ev_server.models.entities.OAuthIdentityKey

interface OAuthIdentityRepository : StoreRepository<OAuthIdentityEntity, OAuthIdentityKey>
