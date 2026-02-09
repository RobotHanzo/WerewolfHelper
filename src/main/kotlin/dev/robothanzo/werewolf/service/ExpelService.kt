package dev.robothanzo.werewolf.service

import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.ExpelStatus
import dev.robothanzo.werewolf.model.Candidate
import dev.robothanzo.werewolf.model.ExpelPoll
import dev.robothanzo.werewolf.model.ExpelSession
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel

interface ExpelService {
    val sessions: MutableMap<Long, ExpelSession>

    fun startExpelPoll(session: Session, durationSeconds: Int)
    fun endExpelPoll(guildId: Long)
    fun getExpelSession(guildId: Long): ExpelSession?

    // Poll instance management and UI start
    val polls: MutableMap<Long, ExpelPoll>

    fun hasPoll(guildId: Long): Boolean
    fun getPoll(guildId: Long): ExpelPoll?
    fun getPollCandidates(guildId: Long): Map<Int, Candidate>?
    fun setPollCandidates(guildId: Long, candidates: MutableMap<Int, Candidate>)
    fun removePoll(guildId: Long)

    fun startExpelPollUI(
        session: Session,
        channel: GuildMessageChannel,
        allowPK: Boolean,
        durationMillis: Long = 30000L,
        callback: (() -> Unit)? = null
    )

    /**
     * Returns a UI-friendly ExpelStatus for the given guild, or null if no expel session/poll exists.
     */
    fun getExpelStatus(guildId: Long): ExpelStatus?
}
