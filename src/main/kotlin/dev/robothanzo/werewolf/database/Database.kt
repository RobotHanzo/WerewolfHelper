package dev.robothanzo.werewolf.database

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.client.MongoClients
import com.mongodb.client.MongoDatabase
import org.bson.codecs.configuration.CodecRegistries
import org.bson.codecs.configuration.CodecRegistry
import org.bson.codecs.pojo.Conventions
import org.bson.codecs.pojo.PojoCodecProvider
import org.slf4j.LoggerFactory

object Database {
    private val log = LoggerFactory.getLogger(Database::class.java)
    lateinit var database: MongoDatabase

    fun initDatabase(vararg codecRegistry: CodecRegistry?) {
        val connString = ConnectionString(
            System.getenv().getOrDefault("DATABASE", "mongodb://localhost:27017")
        )

        // Configure POJO codec to use fields directly and ignore getters without fields
        // In Kotlin, we can rely on @BsonIgnore for computed properties, so we simplify this.
        // We stick to standard conventions but use SET_PRIVATE_FIELDS_CONVENTION to allow writing to private fields if needed.
        val pojoProvider = PojoCodecProvider.builder()
            .automatic(true)
            .conventions(
                listOf(
                    Conventions.ANNOTATION_CONVENTION,
                    Conventions.CLASS_AND_PROPERTY_CONVENTION,
                    Conventions.SET_PRIVATE_FIELDS_CONVENTION
                )
            )
            .build()

        var pojoCodecRegistry = CodecRegistries.fromRegistries(
            MongoClientSettings.getDefaultCodecRegistry(),
            CodecRegistries.fromProviders(pojoProvider)
        )

        if (codecRegistry.isNotEmpty()) {
            for (codec in codecRegistry) {
                if (codec != null) {
                    pojoCodecRegistry = CodecRegistries.fromRegistries(pojoCodecRegistry, codec)
                }
            }
        }
        val settings = MongoClientSettings.builder()
            .applyConnectionString(connString)
            .retryWrites(true)
            .codecRegistry(pojoCodecRegistry)
            .build()
        val client = MongoClients.create(settings)
        database = client.getDatabase("WerewolfHelper")
    }
}
