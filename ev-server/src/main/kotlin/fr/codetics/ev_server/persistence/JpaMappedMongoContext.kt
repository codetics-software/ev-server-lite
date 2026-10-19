package fr.codetics.ev_server.persistence

import jakarta.persistence.EmbeddedId
import jakarta.persistence.Id
import jakarta.persistence.Version
import org.springframework.data.mapping.model.Property
import org.springframework.data.mapping.model.PropertyNameFieldNamingStrategy
import org.springframework.data.mapping.model.SimpleTypeHolder
import org.springframework.data.mongodb.core.mapping.CachingMongoPersistentProperty
import org.springframework.data.mongodb.core.mapping.MongoMappingContext
import org.springframework.data.mongodb.core.mapping.MongoPersistentEntity
import org.springframework.data.mongodb.core.mapping.MongoPersistentProperty

class JpaMappedMongoContext : MongoMappingContext() {
    override fun createPersistentProperty(
        property: Property,
        owner: MongoPersistentEntity<*>,
        simpleTypeHolder: SimpleTypeHolder,
    ): MongoPersistentProperty = JpaMappedProperty(property, owner, simpleTypeHolder)
}

private class JpaMappedProperty(
    property: Property,
    owner: MongoPersistentEntity<*>,
    simpleTypeHolder: SimpleTypeHolder,
) :
    CachingMongoPersistentProperty(
        property,
        owner,
        simpleTypeHolder,
        PropertyNameFieldNamingStrategy.INSTANCE,
    ) {
    override fun isIdProperty() =
        super.isIdProperty() ||
            isAnnotationPresent(Id::class.java) ||
            isAnnotationPresent(EmbeddedId::class.java)

    override fun isVersionProperty() =
        super.isVersionProperty() || isAnnotationPresent(Version::class.java)
}
