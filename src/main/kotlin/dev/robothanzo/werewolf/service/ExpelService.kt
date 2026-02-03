package dev.robothanzo.werewolf.service

import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.model.ExpelSession

interface ExpelService {
    val sessions: MutableMap<Long, ExpelSession>

    fun startExpelPoll(session: Session, durationSeconds: Int)
    fun endExpelPoll(guildId: Long)
    fun getExpelSession(guildId: Long): ExpelSession?
}
