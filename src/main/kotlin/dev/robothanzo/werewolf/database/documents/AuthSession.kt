package dev.robothanzo.werewolf.database.documents

import com.mongodb.client.MongoCollection
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import dev.robothanzo.werewolf.database.Database
import org.bson.codecs.pojo.annotations.BsonIgnore
import java.io.Serializable
import java.util.*
import java.util.concurrent.TimeUnit

data class AuthSession(
    var sessionId: String? = null,
    var userId: String? = null,
    var guildId: String? = null,
    var role: UserRole? = null,
    var createdAt: Date? = null
) : Serializable {

    @get:BsonIgnore
    val isJudge: Boolean
        get() = role == UserRole.JUDGE

    @get:BsonIgnore
    val isSpectator: Boolean
        get() = role == UserRole.SPECTATOR

    @get:BsonIgnore
    val isPrivileged: Boolean
        get() = role != null && role!!.isPrivileged()

    @get:BsonIgnore
    val isBlocked: Boolean
        get() = role == UserRole.BLOCKED

    @get:BsonIgnore
    val isPending: Boolean
        get() = role == UserRole.PENDING || role == null

    companion object {
        private const val serialVersionUID = 1L

        fun fetchCollection(): MongoCollection<AuthSession> {
            val collection = Database.database.getCollection("auth_sessions", AuthSession::class.java)
            // Create TTL index on createdAt if it doesn't exist (30 days)
            collection.createIndex(Indexes.ascending("createdAt"), IndexOptions().expireAfter(30L, TimeUnit.DAYS))
            return collection
        }
    }
}
