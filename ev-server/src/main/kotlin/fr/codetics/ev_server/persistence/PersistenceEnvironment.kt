package fr.codetics.ev_server.persistence

import org.springframework.boot.EnvironmentPostProcessor
import org.springframework.boot.SpringApplication
import org.springframework.core.Ordered
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource

class PersistenceEnvironment : EnvironmentPostProcessor, Ordered {
    override fun getOrder() = Ordered.LOWEST_PRECEDENCE

    override fun postProcessEnvironment(
        environment: ConfigurableEnvironment,
        application: SpringApplication,
    ) {
        val url =
            environment.getProperty("app.database-url")
                ?: environment.getProperty("spring.datasource.url")
        val kind = url?.let(DatabaseKind::of) ?: DatabaseKind.SQL
        val properties = mutableMapOf<String, Any>(DatabaseKind.PROPERTY to kind.property)

        if (kind == DatabaseKind.MONGODB) {
            properties["spring.mongodb.uri"] = requireNotNull(url)
            properties.putDefault(environment, "spring.mongodb.representation.uuid", "standard")
            properties.putDefault(
                environment,
                "spring.data.mongodb.representation.big-decimal",
                "decimal128",
            )
        }

        val inactive =
            if (kind == DatabaseKind.MONGODB) SQL_AUTOCONFIGURATIONS else MONGODB_AUTOCONFIGURATIONS
        val excluded =
            environment.getProperty("spring.autoconfigure.exclude").orEmpty().split(',').map {
                it.trim()
            } + inactive
        properties["spring.autoconfigure.exclude"] =
            excluded.filter(String::isNotBlank).distinct().joinToString(",")

        environment.propertySources.addFirst(MapPropertySource("persistence", properties))
    }

    private fun MutableMap<String, Any>.putDefault(
        environment: ConfigurableEnvironment,
        key: String,
        value: String,
    ) {
        if (!environment.containsProperty(key)) {
            put(key, value)
        }
    }

    private companion object {
        val SQL_AUTOCONFIGURATIONS =
            listOf(
                "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
                "org.springframework.boot.jdbc.autoconfigure.DataSourceInitializationAutoConfiguration",
                "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration",
                "org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration",
                "org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration",
                "org.springframework.boot.jdbc.autoconfigure.health.DataSourceHealthContributorAutoConfiguration",
                "org.springframework.boot.jdbc.autoconfigure.metrics.DataSourcePoolMetricsAutoConfiguration",
                "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
                "org.springframework.boot.hibernate.autoconfigure.metrics.HibernateMetricsAutoConfiguration",
                "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration",
                "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
                "org.springframework.boot.flyway.autoconfigure.FlywayEndpointAutoConfiguration",
            )
        val MONGODB_AUTOCONFIGURATIONS =
            listOf(
                "org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration",
                "org.springframework.boot.mongodb.autoconfigure.MongoReactiveAutoConfiguration",
                "org.springframework.boot.mongodb.autoconfigure.health.MongoHealthContributorAutoConfiguration",
                "org.springframework.boot.mongodb.autoconfigure.health.MongoReactiveHealthContributorAutoConfiguration",
                "org.springframework.boot.mongodb.autoconfigure.metrics.MongoMetricsAutoConfiguration",
                "org.springframework.boot.data.mongodb.autoconfigure.DataMongoAutoConfiguration",
                "org.springframework.boot.data.mongodb.autoconfigure.DataMongoReactiveAutoConfiguration",
                "org.springframework.boot.data.mongodb.autoconfigure.DataMongoReactiveRepositoriesAutoConfiguration",
                "org.springframework.boot.data.mongodb.autoconfigure.DataMongoRepositoriesAutoConfiguration",
            )
    }
}
