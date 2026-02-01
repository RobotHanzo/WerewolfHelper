package dev.robothanzo.werewolf.database;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import lombok.extern.slf4j.Slf4j;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.jetbrains.annotations.Nullable;

import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

@Slf4j
public class Database {
    public static MongoDatabase database;

    public static void initDatabase(@Nullable CodecRegistry... codecRegistry) {
        ConnectionString connString = new ConnectionString(
                System.getenv().getOrDefault("DATABASE", "mongodb://localhost:27017")
        );

        // Configure POJO codec to use fields directly and ignore getters without fields
        PojoCodecProvider pojoProvider = PojoCodecProvider.builder()
                .automatic(true)
                .conventions(java.util.Arrays.asList(
                        org.bson.codecs.pojo.Conventions.ANNOTATION_CONVENTION,
                        org.bson.codecs.pojo.Conventions.CLASS_AND_PROPERTY_CONVENTION,
                        org.bson.codecs.pojo.Conventions.SET_PRIVATE_FIELDS_CONVENTION,
                        builder -> {
                            // Custom convention: Remove properties discovered via getters/setters that don't have a backing field
                            java.util.List<String> toRemove = new java.util.ArrayList<>();
                            for (org.bson.codecs.pojo.PropertyModelBuilder<?> propertyBuilder : builder.getPropertyModelBuilders()) {
                                boolean hasField = false;
                                Class<?> current = builder.getType();
                                while (current != null && current != Object.class) {
                                    try {
                                        current.getDeclaredField(propertyBuilder.getName());
                                        hasField = true;
                                        break;
                                    } catch (NoSuchFieldException ignored) {
                                        current = current.getSuperclass();
                                    }
                                }
                                if (!hasField) {
                                    toRemove.add(propertyBuilder.getName());
                                }
                            }
                            toRemove.forEach(builder::removeProperty);
                        }
                ))
                .build();

        CodecRegistry pojoCodecRegistry = fromRegistries(
                MongoClientSettings.getDefaultCodecRegistry(),
                fromProviders(pojoProvider)
        );

        if (codecRegistry != null) {
            for (CodecRegistry codec : codecRegistry) {
                pojoCodecRegistry = fromRegistries(pojoCodecRegistry, codec);
            }
        }
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(connString)
                .retryWrites(true)
                .codecRegistry(pojoCodecRegistry)
                .build();
        MongoClient client = MongoClients.create(settings);
        database = client.getDatabase("WerewolfHelper");
    }
}
