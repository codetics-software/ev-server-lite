package fr.codetics.ev_server.repositories

import fr.codetics.ev_server.models.entities.OAuthIdentityEntity
import fr.codetics.ev_server.models.entities.OAuthIdentityKey
import org.springframework.data.jpa.repository.JpaRepository

interface OAuthIdentityRepository : JpaRepository<OAuthIdentityEntity, OAuthIdentityKey>
