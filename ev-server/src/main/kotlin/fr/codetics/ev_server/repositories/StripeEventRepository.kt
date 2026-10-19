package fr.codetics.ev_server.repositories

import fr.codetics.ev_server.models.entities.StripeEventEntity
import org.springframework.data.jpa.repository.JpaRepository

interface StripeEventRepository : JpaRepository<StripeEventEntity, String>
