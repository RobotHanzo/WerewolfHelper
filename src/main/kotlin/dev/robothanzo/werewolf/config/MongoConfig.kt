package dev.robothanzo.werewolf.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mapping.model.Property
import org.springframework.data.mapping.model.PropertyNameFieldNamingStrategy
import org.springframework.data.mapping.model.SimpleTypeHolder
import org.springframework.data.mongodb.core.convert.MongoCustomConversions
import org.springframework.data.mongodb.core.mapping.BasicMongoPersistentProperty
import org.springframework.data.mongodb.core.mapping.MongoMappingContext
import org.springframework.data.mongodb.core.mapping.MongoPersistentEntity
import org.springframework.data.mongodb.core.mapping.MongoPersistentProperty

@Configuration
class MongoConfig(private val customConversions: MongoCustomConversions) {
    @Bean
    fun mongoMappingContext(): MongoMappingContext {
        val mappingContext = object : MongoMappingContext() {
            override fun createPersistentProperty(
                property: Property,
                owner: MongoPersistentEntity<*>,
                simpleTypeHolder: SimpleTypeHolder
            ): MongoPersistentProperty {
                return object : BasicMongoPersistentProperty(
                    property,
                    owner,
                    simpleTypeHolder,
                    PropertyNameFieldNamingStrategy.INSTANCE
                ) {
                    override fun isTransient(): Boolean {
                        // Globally ignore properties that don't have a backing field 
                        // (i.e., computed properties/getters), unless they are the ID.
                        return super.isTransient() || (field == null && !isIdProperty)
                    }
                }
            }
        }
        mappingContext.setSimpleTypeHolder(customConversions.simpleTypeHolder)
        return mappingContext
    }
}
