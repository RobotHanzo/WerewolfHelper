package dev.robothanzo.werewolf.security

import dev.robothanzo.werewolf.database.documents.Session
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface SessionRepository : MongoRepository<Session, String> {
    fun findByGuildId(guildId: Long): Optional<Session>
    fun deleteByGuildId(guildId: Long)
}
