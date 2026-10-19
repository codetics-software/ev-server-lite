package fr.codetics.ev_server.repositories

import fr.codetics.ev_server.models.entities.AuthAttemptEntity
import java.time.Instant

interface AuthAttemptRepository : StoreRepository<AuthAttemptEntity, String> {
    fun deleteAllByWindowStartBefore(expiry: Instant)
}
