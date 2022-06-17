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
                "mongodb://localhost:27017"
        );
        CodecRegistry pojoCodecRegistry = fromRegistries(MongoClientSettings.getDefaultCodecRegistry(),
                fromProviders(PojoCodecProvider.builder().automatic(true).build()));
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
