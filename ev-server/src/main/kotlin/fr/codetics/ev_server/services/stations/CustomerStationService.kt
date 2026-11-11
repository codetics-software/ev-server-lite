// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.ev_server.services.stations

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.core.NotFound
import fr.codetics.ev_server.core.ensure
import fr.codetics.ev_server.core.msg
import fr.codetics.ev_server.models.entities.ConnectorEntity
import fr.codetics.ev_server.models.entities.StationEntity
import fr.codetics.ev_server.models.entities.StationKey
import fr.codetics.ev_server.models.entities.TenantEntity
import fr.codetics.ev_server.models.entities.TransactionEntity
import fr.codetics.ev_server.models.organization.SiteAreaData
import fr.codetics.ev_server.models.organization.SiteData
import fr.codetics.ev_server.models.people.UserRole
import fr.codetics.ev_server.models.stations.ConnectorResponse
import fr.codetics.ev_server.models.stations.CustomerEvseLink
import fr.codetics.ev_server.models.stations.CustomerStationResponse
import fr.codetics.ev_server.models.stations.CustomerTariff
import fr.codetics.ev_server.repositories.ConnectorRepository
import fr.codetics.ev_server.repositories.PublicEvseRepository
import fr.codetics.ev_server.repositories.SiteUserRepository
import fr.codetics.ev_server.repositories.StationPhotoRepository
import fr.codetics.ev_server.repositories.StationRepository
import fr.codetics.ev_server.repositories.TenantRepository
import fr.codetics.ev_server.repositories.TransactionRepository
import fr.codetics.ev_server.services.organization.Entry
import fr.codetics.ev_server.services.organization.Organization
import fr.codetics.ev_server.services.pricing.PricingService
import java.time.Instant
import java.util.UUID
import org.springframework.stereotype.Service

@Service
class CustomerStationService(
    private val stations: StationRepository,
    private val connectors: ConnectorRepository,
    private val transactions: TransactionRepository,
    private val evses: PublicEvseRepository,
    private val photos: StationPhotoRepository,
    private val siteUsers: SiteUserRepository,
    private val tenants: TenantRepository,
    private val organization: Organization,
    private val pricing: PricingService,
) {
    private data class Snapshot(
        val tenant: TenantEntity,
        val sites: Map<UUID, Entry<SiteData>>,
        val areas: Map<UUID, Entry<SiteAreaData>>,
        val plugs: Map<String, List<ConnectorEntity>>,
        val links: Map<String, List<CustomerEvseLink>>,
        val own: Map<Pair<String, Int>, TransactionEntity>,
        val allowedSites: Set<UUID>?,
    )

    private fun snapshot(tenant: TenantEntity, user: AppPrincipal?): Snapshot {
        val own =
            if (user == null) emptyMap()
            else
                transactions
                    .findAllByTenantIdAndStoppedAtIsNull(tenant.id)
                    .filter { it.userId == user.id }
                    .associateBy { it.stationId to it.connectorId }
        return Snapshot(
            tenant,
            organization.sites(tenant.id).associateBy { it.id },
            organization.siteAreas(tenant.id).associateBy { it.id },
            connectors.findAllByKeyTenantId(tenant.id).filter { it.key.connectorId > 0 }.groupBy { it.key.stationId },
            evses
                .findAllByTenantIdOrderByName(tenant.id)
                .filter { it.published }
                .groupBy { it.stationId }
                .mapValues { (_, list) -> list.map { CustomerEvseLink(it.id, it.connectorId, tenant.slug, it.name) } },
            own,
            when {
                user == null -> null
                user.role != UserRole.DRIVER -> null
                else -> siteUsers.findAllByKeyTenantIdAndKeyUserId(tenant.id, user.id).map { it.key.siteId }.toSet()
            },
        )
    }

    private fun site(snapshot: Snapshot, station: StationEntity) =
        station.siteAreaId?.let(snapshot.areas::get)?.let { snapshot.sites[it.data.siteId] }

    private fun visible(snapshot: Snapshot, station: StationEntity, user: AppPrincipal?): Boolean {
        if (station.deleted || !station.enabled) return false
        val site = site(snapshot, station)
        if (user == null) {
            return station.publicStation ||
                site?.data?.public == true ||
                snapshot.links[station.key.stationId].orEmpty().isNotEmpty()
        }
        if (user.role != UserRole.DRIVER) return true
        return site == null || !site.data.accessControl || site.id in snapshot.allowedSites.orEmpty()
    }

    private fun view(
        snapshot: Snapshot,
        station: StationEntity,
        photoCount: Long? = null,
        tariff: CustomerTariff? = null,
    ): CustomerStationResponse {
        val site = site(snapshot, station)
        return CustomerStationResponse(
            id = station.key.stationId,
            name = station.name,
            vendor = station.vendor,
            model = station.model,
            connected = station.connected,
            lastSeen = station.lastSeen,
            connectors =
                snapshot.plugs[station.key.stationId].orEmpty().sortedBy { it.key.connectorId }.map { plug ->
                    val mine = snapshot.own[station.key.stationId to plug.key.connectorId]
                    ConnectorResponse.from(plug, mine)
                },
            public = station.publicStation,
            latitude = station.latitude ?: site?.data?.latitude,
            longitude = station.longitude ?: site?.data?.longitude,
            maximumPowerKw = station.maximumPowerKw,
            autoCharge = station.autoCharge,
            siteId = site?.id,
            siteAreaId = station.siteAreaId,
            siteName = site?.name,
            address = site?.data?.address,
            postalCode = site?.data?.postalCode,
            city = site?.data?.city,
            country = site?.data?.country,
            publicEvses = snapshot.links[station.key.stationId].orEmpty(),
            photoCount = photoCount,
            tariff = tariff,
        )
    }

    private fun list(tenant: TenantEntity, user: AppPrincipal?, search: String): List<CustomerStationResponse> {
        ensure(search.length <= 200) { msg("validation.search_too_long") }
        val snapshot = snapshot(tenant, user)
        val term = search.trim()
        return stations
            .findAllByKeyTenantIdAndDeletedFalse(tenant.id)
            .filter { visible(snapshot, it, user) }
            .filter { station ->
                term.isEmpty() ||
                    station.name.contains(term, true) ||
                    station.key.stationId.contains(term, true) ||
                    site(snapshot, station)?.let { site ->
                        site.name.contains(term, true) || site.data.city?.contains(term, true) == true
                    } == true
            }
            .sortedWith(compareBy({ it.name }, { it.key.stationId }))
            .take(MAXIMUM_STATIONS)
            .map { view(snapshot, it) }
    }

    private fun tariff(station: StationEntity, connectorId: Int): CustomerTariff? =
        pricing.resolve(pricing.context(station, connectorId, Instant.now()))?.let { definition ->
            CustomerTariff(
                definition.name,
                definition.data.currency,
                definition.data.flatPrice,
                definition.data.energyPrice,
                definition.data.timePrice,
                definition.data.parkingPrice,
            )
        }

    private fun detail(tenant: TenantEntity, user: AppPrincipal?, id: String): CustomerStationResponse {
        val snapshot = snapshot(tenant, user)
        val station =
            stations.findById(StationKey(tenant.id, id)).orElse(null)?.takeIf { visible(snapshot, it, user) }
                ?: throw NotFound(msg("stations.not_found"))
        val connector = snapshot.plugs[id].orEmpty().minOfOrNull { it.key.connectorId } ?: 1
        return view(snapshot, station, photos.countByTenantIdAndStationId(tenant.id, id), tariff(station, connector))
    }

    fun list(user: AppPrincipal, search: String) = list(tenants.findById(user.tenantId).orElseThrow(), user, search)

    fun find(user: AppPrincipal, id: String) = detail(tenants.findById(user.tenantId).orElseThrow(), user, id)

    fun requireVisible(user: AppPrincipal, id: String) {
        val tenant = tenants.findById(user.tenantId).orElseThrow()
        val station = stations.findById(StationKey(tenant.id, id)).orElse(null)
        if (station == null || !visible(snapshot(tenant, user), station, user)) throw NotFound(msg("stations.not_found"))
    }

    private fun publicTenant(slug: String) =
        tenants.findBySlugAndActiveTrue(slug) ?: throw NotFound(msg("workspaces.not_found_customer"))

    fun publicList(slug: String, search: String) = list(publicTenant(slug), null, search)

    fun publicFind(slug: String, id: String) = detail(publicTenant(slug), null, id)

    private companion object {
        const val MAXIMUM_STATIONS = 1000
    }
}
