package fr.codetics.ev_server.persistence

import com.mongodb.MongoException
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.ReturnDocument
import fr.codetics.ev_server.models.entities.AuditEntity
import fr.codetics.ev_server.models.entities.MeterValueEntity
import fr.codetics.ev_server.models.entities.TransactionEntity
import java.math.BigDecimal
import java.util.UUID
import org.bson.Document
import org.bson.types.Decimal128
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.core.TypeInformation
import org.springframework.data.mongodb.MongoDatabaseFactory
import org.springframework.data.mongodb.MongoManagedTypes
import org.springframework.data.mongodb.MongoTransactionManager
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.convert.MongoCustomConversions
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertCallback
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Repository
import org.springframework.transaction.support.TransactionSynchronizationManager

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(DatabaseKind.PROPERTY, havingValue = "mongodb")
class MongoPersistenceConfiguration {
    @Bean
    fun mongoMappingContext(conversions: MongoCustomConversions, types: MongoManagedTypes) =
        JpaMappedMongoContext().apply {
            setManagedTypes(types)
            setSimpleTypeHolder(conversions.simpleTypeHolder)
        }

    @Bean
    fun transactionManager(factory: MongoDatabaseFactory) = MongoTransactionManager(factory)

    @Bean fun mongoSequences(factory: MongoDatabaseFactory) = MongoSequences(factory)

    @Bean fun mongoSchema(mongo: MongoTemplate) = MongoSchema(mongo)
}

@Repository
@ConditionalOnProperty(DatabaseKind.PROPERTY, havingValue = "mongodb")
class MongoStore(private val mongo: MongoTemplate) : Store {
    override fun <T : Any> lock(type: Class<T>, id: Any): T? {
        check(TransactionSynchronizationManager.isActualTransactionActive()) {
            "Le verrouillage MongoDB nécessite une transaction."
        }
        val key = mongo.converter.convertToMongoType(id, TypeInformation.of(id.javaClass))
        return mongo.findAndModify(
            Query.query(Criteria.where("_id").`is`(key)),
            Update().inc(LOCK_FIELD, 1),
            FindAndModifyOptions.options().returnNew(true),
            type,
        )
    }

    override fun flush() = Unit

    override fun isTransient(error: Throwable): Boolean =
        generateSequence(error) { it.cause }
            .filterIsInstance<MongoException>()
            .any {
                it.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL) ||
                    it.hasErrorLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL)
            }

    private companion object {
        const val LOCK_FIELD = "_lock"
    }
}

@Repository
@ConditionalOnProperty(DatabaseKind.PROPERTY, havingValue = "mongodb")
class MongoSessionStatistics(private val mongo: MongoTemplate) : SessionStatistics {
    override fun deliveredEnergyWh(tenantId: UUID): BigDecimal {
        val aggregation =
            Aggregation.newAggregation(
                Aggregation.match(
                    Criteria.where("tenantId")
                        .`is`(tenantId)
                        .and("meterStopWh")
                        .exists(true)
                        .ne(null)
                ),
                Aggregation.project()
                    .andExpression("meterStopWh - meterStartWh")
                    .`as`("delivered"),
                Aggregation.match(Criteria.where("delivered").gte(Decimal128(0))),
                Aggregation.group().sum("delivered").`as`("total"),
            )
        val total =
            mongo
                .aggregate(aggregation, "charging_transactions", Document::class.java)
                .uniqueMappedResult
                ?.get("total")
        return when (total) {
            is Decimal128 -> total.bigDecimalValue()
            is Number -> BigDecimal(total.toString())
            else -> BigDecimal.ZERO
        }
    }
}

class MongoSequences(private val factory: MongoDatabaseFactory) : BeforeConvertCallback<Any> {
    override fun onBeforeConvert(entity: Any, collection: String): Any {
        when (entity) {
            is TransactionEntity -> if (entity.id == null) entity.id = next(collection).toInt()
            is AuditEntity -> if (entity.id == null) entity.id = next(collection)
            is MeterValueEntity -> if (entity.id == null) entity.id = next(collection)
        }
        return entity
    }

    private fun next(sequence: String): Long {
        val counter =
            factory.mongoDatabase
                .getCollection("sequences")
                .findOneAndUpdate(
                    Document("_id", sequence),
                    Document("\$inc", Document("value", 1L)),
                    FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER),
                )
        return requireNotNull(counter).getLong("value")
    }
}
