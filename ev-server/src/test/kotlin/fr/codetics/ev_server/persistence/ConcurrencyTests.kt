package fr.codetics.ev_server.persistence

import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.core.lock
import fr.codetics.ev_server.core.sha256
import fr.codetics.ev_server.models.entities.StationEntity
import fr.codetics.ev_server.models.entities.StationKey
import fr.codetics.ev_server.models.entities.TenantEntity
import fr.codetics.ev_server.repositories.AuthAttemptRepository
import fr.codetics.ev_server.repositories.StationRepository
import fr.codetics.ev_server.repositories.TenantRepository
import fr.codetics.ev_server.security.RateLimiter
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("local")
@SpringBootTest(properties = ["app.bootstrap.email="])
class ConcurrencyTests
@Autowired
constructor(
    private val db: Database,
    private val limiter: RateLimiter,
    private val attempts: AuthAttemptRepository,
    private val tenants: TenantRepository,
    private val stations: StationRepository,
) {
    private fun <T> parallel(count: Int, task: (Int) -> T): List<T> {
        val pool = Executors.newFixedThreadPool(8)
        try {
            return pool.invokeAll((0 until count).map { index -> Callable { task(index) } }).map {
                it.get()
            }
        } finally {
            pool.shutdown()
        }
    }

    @Test
    fun `les tentatives concurrentes sont toutes comptees`() {
        val subject = "concurrence:${UUID.randomUUID()}"

        parallel(24) { limiter.check(subject, 1000) }

        assertEquals(24, attempts.findById(sha256(subject)).orElseThrow().attempts)
    }

    @Test
    fun `un verrou serialise les modifications concurrentes d'un meme document`() {
        val slug = "verrou-${UUID.randomUUID().toString().take(8)}"
        val tenant = tenants.save(TenantEntity(slug = slug, name = "Verrou"))
        val key = StationKey(tenant.id, "VERROU-01")
        stations.save(StationEntity(key, "0", "secret"))

        parallel(16) {
            db.atomic {
                val station = requireNotNull(db.lock<StationEntity>(key))
                station.name = (station.name.toInt() + 1).toString()
                stations.save(station)
            }
        }

        assertEquals("16", stations.findById(key).orElseThrow().name)
    }

    @Test
    fun `le verrou exige une transaction`() {
        assertThrows(RuntimeException::class.java) {
            db.lock<StationEntity>(StationKey(UUID.randomUUID(), "SANS-TRANSACTION"))
        }
    }
}
