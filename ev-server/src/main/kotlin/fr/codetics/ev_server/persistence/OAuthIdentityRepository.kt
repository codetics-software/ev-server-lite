package fr.codetics.ev_server.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface OAuthIdentityRepository : JpaRepository<OAuthIdentityEntity, OAuthIdentityKey>
