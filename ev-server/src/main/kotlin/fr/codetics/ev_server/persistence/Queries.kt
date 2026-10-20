package fr.codetics.ev_server.persistence

import jakarta.persistence.EntityManager
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.Expression
import jakarta.persistence.criteria.Path
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import java.util.regex.Pattern
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Repository

sealed interface Condition {
    data class Eq(val field: String, val value: Any?) : Condition

    data class In(val field: String, val values: Collection<Any>) : Condition

    data class AtLeast(val field: String, val value: Comparable<*>) : Condition

    data class Before(val field: String, val value: Comparable<*>) : Condition

    data class Present(val field: String, val present: Boolean) : Condition

    data class Contains(val field: String, val text: String) : Condition

    data class AnyOf(val conditions: List<Condition>) : Condition
}

fun eq(field: String, value: Any?) = Condition.Eq(field, value)

fun within(field: String, values: Collection<Any>) = Condition.In(field, values)

fun atLeast(field: String, value: Comparable<*>) = Condition.AtLeast(field, value)

fun before(field: String, value: Comparable<*>) = Condition.Before(field, value)

fun present(field: String, present: Boolean = true) = Condition.Present(field, present)

fun contains(field: String, text: String) = Condition.Contains(field, text)

fun anyOf(vararg conditions: Condition) = Condition.AnyOf(conditions.toList())

interface Queries {
    fun <T : Any> page(type: Class<T>, conditions: List<Condition>, pageable: Pageable): Page<T>

    fun <T : Any> list(type: Class<T>, conditions: List<Condition>, sort: Sort, limit: Int): List<T>

    fun <T : Any> count(type: Class<T>, conditions: List<Condition>): Long

    fun <T : Any> delete(type: Class<T>, conditions: List<Condition>): Long
}

inline fun <reified T : Any> Queries.page(conditions: List<Condition>, pageable: Pageable) =
    page(T::class.java, conditions, pageable)

inline fun <reified T : Any> Queries.list(
    conditions: List<Condition>,
    sort: Sort,
    limit: Int = 10000,
) = list(T::class.java, conditions, sort, limit)

inline fun <reified T : Any> Queries.count(conditions: List<Condition>) =
    count(T::class.java, conditions)

inline fun <reified T : Any> Queries.delete(conditions: List<Condition>) =
    delete(T::class.java, conditions)

@Repository
@ConditionalOnProperty(DatabaseKind.PROPERTY, havingValue = "sql", matchIfMissing = true)
class SqlQueries(private val entities: EntityManager) : Queries {
    override fun <T : Any> page(
        type: Class<T>,
        conditions: List<Condition>,
        pageable: Pageable,
    ): Page<T> {
        val rows = select(type, conditions, pageable.sort, pageable.offset.toInt(), pageable.pageSize)
        return PageImpl(rows, pageable, count(type, conditions))
    }

    override fun <T : Any> list(
        type: Class<T>,
        conditions: List<Condition>,
        sort: Sort,
        limit: Int,
    ): List<T> = select(type, conditions, sort, 0, limit)

    override fun <T : Any> count(type: Class<T>, conditions: List<Condition>): Long {
        val builder = entities.criteriaBuilder
        val query = builder.createQuery(Long::class.java)
        val root = query.from(type)
        query.select(builder.count(root)).where(*predicates(builder, root, conditions))
        return entities.createQuery(query).singleResult
    }

    override fun <T : Any> delete(type: Class<T>, conditions: List<Condition>): Long {
        val builder = entities.criteriaBuilder
        val query = builder.createCriteriaDelete(type)
        val root = query.from(type)
        query.where(*predicates(builder, root, conditions))
        return entities.createQuery(query).executeUpdate().toLong()
    }

    private fun <T : Any> select(
        type: Class<T>,
        conditions: List<Condition>,
        sort: Sort,
        offset: Int,
        limit: Int,
    ): List<T> {
        val builder = entities.criteriaBuilder
        val query = builder.createQuery(type)
        val root = query.from(type)
        query.select(root).where(*predicates(builder, root, conditions))
        query.orderBy(
            sort.map { order ->
                    val path = path<Any>(root, order.property)
                    if (order.isAscending) builder.asc(path) else builder.desc(path)
                }
                .toList()
        )
        return entities
            .createQuery(query)
            .setFirstResult(offset)
            .setMaxResults(limit)
            .resultList
    }

    private fun predicates(
        builder: CriteriaBuilder,
        root: Root<*>,
        conditions: List<Condition>,
    ): Array<Predicate> = conditions.map { predicate(builder, root, it) }.toTypedArray()

    @Suppress("UNCHECKED_CAST")
    private fun predicate(builder: CriteriaBuilder, root: Root<*>, condition: Condition): Predicate =
        when (condition) {
            is Condition.Eq ->
                if (condition.value == null) builder.isNull(path<Any>(root, condition.field))
                else builder.equal(path<Any>(root, condition.field), condition.value)
            is Condition.In ->
                if (condition.values.isEmpty()) builder.disjunction()
                else path<Any>(root, condition.field).`in`(condition.values)
            is Condition.AtLeast ->
                builder.greaterThanOrEqualTo(
                    path<Comparable<Any>>(root, condition.field),
                    condition.value as Comparable<Any>,
                )
            is Condition.Before ->
                builder.lessThan(
                    path<Comparable<Any>>(root, condition.field),
                    condition.value as Comparable<Any>,
                )
            is Condition.Present ->
                if (condition.present) builder.isNotNull(path<Any>(root, condition.field))
                else builder.isNull(path<Any>(root, condition.field))
            is Condition.Contains -> {
                val pattern =
                    "%" +
                        condition.text
                            .lowercase()
                            .replace("\\", "\\\\")
                            .replace("%", "\\%")
                            .replace("_", "\\_") +
                        "%"
                builder.like(builder.lower(path<String>(root, condition.field)), pattern, '\\')
            }
            is Condition.AnyOf ->
                builder.or(*condition.conditions.map { predicate(builder, root, it) }.toTypedArray())
        }

    @Suppress("UNCHECKED_CAST")
    private fun <X> path(root: Root<*>, field: String): Expression<X> =
        field.split('.').fold(root as Path<*>) { current, part -> current.get<Any>(part) }
            as Expression<X>
}

@Repository
@ConditionalOnProperty(DatabaseKind.PROPERTY, havingValue = "mongodb")
class MongoQueries(private val mongo: MongoTemplate) : Queries {
    override fun <T : Any> page(
        type: Class<T>,
        conditions: List<Condition>,
        pageable: Pageable,
    ): Page<T> {
        val rows = mongo.find(query(conditions).with(pageable), type)
        return PageImpl(rows, pageable, count(type, conditions))
    }

    override fun <T : Any> list(
        type: Class<T>,
        conditions: List<Condition>,
        sort: Sort,
        limit: Int,
    ): List<T> = mongo.find(query(conditions).with(sort).limit(limit), type)

    override fun <T : Any> count(type: Class<T>, conditions: List<Condition>): Long =
        mongo.count(query(conditions), type)

    override fun <T : Any> delete(type: Class<T>, conditions: List<Condition>): Long =
        mongo.remove(query(conditions), type).deletedCount

    private fun query(conditions: List<Condition>): Query =
        if (conditions.isEmpty()) Query()
        else Query(Criteria().andOperator(*conditions.map(::criteria).toTypedArray()))

    private fun criteria(condition: Condition): Criteria =
        when (condition) {
            is Condition.Eq -> Criteria.where(condition.field).`is`(condition.value)
            is Condition.In -> Criteria.where(condition.field).`in`(condition.values)
            is Condition.AtLeast -> Criteria.where(condition.field).gte(condition.value)
            is Condition.Before -> Criteria.where(condition.field).lt(condition.value)
            is Condition.Present ->
                if (condition.present) Criteria.where(condition.field).ne(null)
                else Criteria.where(condition.field).`is`(null)
            is Condition.Contains ->
                Criteria.where(condition.field)
                    .regex(Pattern.compile(Pattern.quote(condition.text), Pattern.CASE_INSENSITIVE))
            is Condition.AnyOf ->
                Criteria().orOperator(*condition.conditions.map(::criteria).toTypedArray())
        }
}
