package fr.codetics.ev_server.persistence

import jakarta.persistence.LockModeType
import java.time.Instant
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface AuthAttemptRepository : JpaRepository<AuthAttemptEntity, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AuthAttemptEntity a where a.subjectHash = :hash")
    fun findForUpdate(@Param("hash") hash: String): AuthAttemptEntity?

    fun deleteAllByWindowStartBefore(expiry: Instant)
}
