package dev.robothanzo.werewolf.database

import dev.robothanzo.werewolf.database.documents.Session
import org.bson.types.ObjectId
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface SessionRepository : MongoRepository<Session, ObjectId> {
    fun findByGuildId(guildId: Long): Optional<Session>
    fun deleteByGuildId(guildId: Long)
}
