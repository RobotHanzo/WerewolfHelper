package dev.robothanzo.werewolf.database.documents;

import com.mongodb.client.MongoCollection;
import dev.robothanzo.werewolf.database.Database;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.concurrent.TimeUnit;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;

import static com.mongodb.client.model.Filters.eq;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthSession {
    private String sessionId;
    private String userId;
    private String username;
    private String discriminator;
    private String avatar;
    private long guildId;
    private String role;
    private Date createdAt;

    public static MongoCollection<AuthSession> fetchCollection() {
        MongoCollection<AuthSession> collection = Database.database.getCollection("auth_sessions", AuthSession.class);
        // Create TTL index on createdAt if it doesn't exist (30 days)
        collection.createIndex(Indexes.ascending("createdAt"), new IndexOptions().expireAfter(30L, TimeUnit.DAYS));
        return collection;
    }
}
