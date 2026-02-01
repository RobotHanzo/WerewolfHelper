package dev.robothanzo.werewolf.database.documents;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import dev.robothanzo.werewolf.database.Database;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;
import java.util.concurrent.TimeUnit;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthSession implements Serializable {
    private static final long serialVersionUID = 1L;
    private String sessionId;
    private String userId;
    private String username;
    private String discriminator;
    private String avatar;
    private String guildId;
    private UserRole role;
    private Date createdAt;

    public boolean isJudge() {
        return role == UserRole.JUDGE;
    }

    public boolean isSpectator() {
        return role == UserRole.SPECTATOR;
    }

    public boolean isPrivileged() {
        return role != null && role.isPrivileged();
    }

    public boolean isBlocked() {
        return role == UserRole.BLOCKED;
    }

    public boolean isPending() {
        return role == UserRole.PENDING || role == null;
    }

    public static MongoCollection<AuthSession> fetchCollection() {
        MongoCollection<AuthSession> collection = Database.database.getCollection("auth_sessions", AuthSession.class);
        // Create TTL index on createdAt if it doesn't exist (30 days)
        collection.createIndex(Indexes.ascending("createdAt"), new IndexOptions().expireAfter(30L, TimeUnit.DAYS));
        return collection;
    }
}
