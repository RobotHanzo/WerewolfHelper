package dev.robothanzo.werewolf.service.impl

import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.model.ExpelSession
import dev.robothanzo.werewolf.service.ExpelService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class ExpelServiceImpl : ExpelService {
    private val log = LoggerFactory.getLogger(ExpelServiceImpl::class.java)

    override val sessions: MutableMap<Long, ExpelSession> = ConcurrentHashMap()

    override fun startExpelPoll(session: Session, durationSeconds: Int) {
        val now = System.currentTimeMillis()
        val expelSession = ExpelSession(
            guildId = session.guildId,
            startTime = now,
            endTime = now + (durationSeconds * 1000L)
        )
        sessions[session.guildId] = expelSession
        log.info("Started expel poll for guild {} with duration {} seconds", session.guildId, durationSeconds)
    }

    override fun endExpelPoll(guildId: Long) {
        sessions.remove(guildId)
        log.info("Ended expel poll for guild {}", guildId)
    }

    override fun getExpelSession(guildId: Long): ExpelSession? {
        return sessions[guildId]
    }
}
