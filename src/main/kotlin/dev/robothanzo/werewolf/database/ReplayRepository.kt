package dev.robothanzo.werewolf.database

import dev.robothanzo.werewolf.database.documents.Replay
import org.bson.types.ObjectId
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface ReplayRepository : MongoRepository<Replay, ObjectId> {
    fun findBySessionId(sessionId: String): Optional<Replay>
    fun deleteBySessionId(sessionId: String)
}
