package dev.robothanzo.werewolf.database.documents

import org.bson.codecs.pojo.annotations.BsonIgnore
import java.io.Serializable
import java.util.*

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
}
