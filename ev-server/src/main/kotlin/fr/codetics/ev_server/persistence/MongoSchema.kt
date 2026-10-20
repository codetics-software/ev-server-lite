package fr.codetics.ev_server.persistence

import fr.codetics.ev_server.models.billing.PaymentState
import org.bson.Document
import org.springframework.beans.factory.InitializingBean
import org.springframework.data.domain.Sort.Direction.ASC
import org.springframework.data.domain.Sort.Direction.DESC
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.springframework.data.mongodb.core.index.PartialIndexFilter
import org.springframework.data.mongodb.core.query.Criteria

class MongoSchema(private val mongo: MongoTemplate) : InitializingBean {
    override fun afterPropertiesSet() {
        requireReplicaSet()
        indexes().forEach { (collection, index) ->
            if (!mongo.collectionExists(collection)) {
                mongo.createCollection(collection)
            }
            mongo.indexOps(collection).createIndex(index)
        }
    }

    private fun requireReplicaSet() {
        val hello = mongo.db.runCommand(Document("hello", 1))
        check(hello.containsKey("setName") || hello.getString("msg") == "isdbgrid") {
            "MongoDB doit fonctionner en replica set ou derrière mongos : les transactions sont indispensables."
        }
    }

    private fun indexes(): List<Pair<String, Index>> {
        fun index(vararg fields: Pair<String, org.springframework.data.domain.Sort.Direction>) =
            Index().apply { fields.forEach { (field, direction) -> on(field, direction) } }
        fun exists(field: String) = PartialIndexFilter.of(Criteria.where(field).exists(true))

        return listOf(
            "tenants" to index("slug" to ASC).unique(),
            "app_users" to index("tenantId" to ASC, "email" to ASC).unique(),
            "access_tokens" to index("userId" to ASC),
            "access_tokens" to index("expiresAt" to ASC),
            "auth_challenges" to index("userId" to ASC, "kind" to ASC),
            "auth_challenges" to index("expiresAt" to ASC),
            "auth_attempts" to index("windowStart" to ASC),
            "oauth_identities" to index("userId" to ASC),
            "records" to index("_id.tenantId" to ASC, "_id.kind" to ASC, "name" to ASC),
            "charging_tokens" to index("_id.tenantId" to ASC, "userId" to ASC),
            "charging_tokens" to index("_id.tenantId" to ASC, "vehicleId" to ASC),
            "charging_transactions" to index("tenantId" to ASC, "startedAt" to DESC),
            "charging_transactions" to
                index(
                    "tenantId" to ASC,
                    "stationId" to ASC,
                    "connectorId" to ASC,
                    "stoppedAt" to ASC,
                ),
            "charging_transactions" to index("tenantId" to ASC, "userId" to ASC),
            "meter_values" to index("tenantId" to ASC, "transactionId" to ASC),
            "station_commands" to
                index(
                    "tenantId" to ASC,
                    "stationId" to ASC,
                    "state" to ASC,
                    "createdAt" to ASC,
                ),
            "station_commands" to index("state" to ASC, "deadline" to ASC),
            "station_commands" to index("tenantId" to ASC, "createdAt" to DESC),
            "audit_events" to index("tenantId" to ASC, "_id" to DESC),
            "ocpi_partners" to index("tokenHash" to ASC).unique(),
            "ocpi_partners" to index("tenantId" to ASC, "name" to ASC),
            "ocpi_objects" to
                index(
                    "_id.tenantId" to ASC,
                    "_id.partnerId" to ASC,
                    "_id.module" to ASC,
                    "_id.direction" to ASC,
                    "lastUpdated" to ASC,
                ),
            "public_evses" to
                index("tenantId" to ASC, "stationId" to ASC, "connectorId" to ASC).unique(),
            "public_evses" to index("tenantId" to ASC, "name" to ASC),
            "charging_payments" to index("tenantId" to ASC, "requestId" to ASC).unique(),
            "charging_payments" to index("tenantId" to ASC, "tokenUid" to ASC).unique(),
            "charging_payments" to
                index("stripeAccountId" to ASC, "stripeCheckoutId" to ASC)
                    .unique()
                    .partial(exists("stripeCheckoutId")),
            "charging_payments" to
                index("evseId" to ASC)
                    .unique()
                    .partial(
                        PartialIndexFilter.of(
                            Criteria.where("state").`in`(PaymentState.OPEN.map { it.name })
                        )
                    ),
            "charging_payments" to index("tenantId" to ASC, "transactionId" to ASC),
            "charging_payments" to index("state" to ASC),
            "charging_payments" to index("tenantId" to ASC, "createdAt" to DESC),
            "workspace_payment_settings" to
                index("stripeAccountId" to ASC).unique().partial(exists("stripeAccountId")),
            "mail_outbox" to index("sentAt" to ASC, "attempts" to ASC, "createdAt" to ASC),
            "stations" to index("_id.tenantId" to ASC, "siteAreaId" to ASC),
            "charging_tokens" to index("_id.tenantId" to ASC, "visualId" to ASC),
            "charging_transactions" to
                index("tenantId" to ASC, "stationId" to ASC, "startedAt" to DESC),
            "charging_transactions" to
                index("tenantId" to ASC, "userId" to ASC, "startedAt" to DESC),
            "charging_transactions" to index("stoppedAt" to ASC, "lastMeterAt" to ASC),
            "meter_values" to index("transactionId" to ASC, "sampledAt" to ASC),
            "site_users" to index("_id.tenantId" to ASC, "_id.userId" to ASC),
            "station_events" to
                index("tenantId" to ASC, "stationId" to ASC, "createdAt" to DESC),
            "station_events" to index("createdAt" to ASC),
            "charging_profiles" to
                index("tenantId" to ASC, "stationId" to ASC, "createdAt" to DESC),
            "registration_tokens" to index("tenantId" to ASC, "tokenHash" to ASC).unique(),
            "notifications" to index("tenantId" to ASC, "createdAt" to DESC),
            "notifications" to
                index("tenantId" to ASC, "dedupeKey" to ASC, "createdAt" to ASC),
            "asset_consumptions" to
                index("tenantId" to ASC, "assetId" to ASC, "sampledAt" to ASC),
            "asset_connections" to
                index("pushTokenHash" to ASC).unique().partial(exists("pushTokenHash")),
            "invoices" to index("tenantId" to ASC, "createdAt" to DESC),
            "invoices" to index("status" to ASC),
            "invoices" to
                index("stripeInvoiceId" to ASC).unique().partial(exists("stripeInvoiceId")),
            "ocpi_jobs" to index("tenantId" to ASC, "partnerId" to ASC, "startedAt" to DESC),
            "ocpi_outbox" to index("sentAt" to ASC, "attempts" to ASC, "nextAttemptAt" to ASC),
            "smart_charging_plans" to index("tenantId" to ASC),
        )
    }
}
