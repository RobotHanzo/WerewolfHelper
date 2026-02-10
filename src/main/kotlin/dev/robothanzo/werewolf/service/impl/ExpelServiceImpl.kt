package dev.robothanzo.werewolf.service.impl

import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.ExpelCandidateDto
import dev.robothanzo.werewolf.game.model.ExpelStatus
import dev.robothanzo.werewolf.model.Candidate
import dev.robothanzo.werewolf.model.ExpelPoll
import dev.robothanzo.werewolf.model.ExpelSession
import dev.robothanzo.werewolf.service.ExpelService
import dev.robothanzo.werewolf.utils.MsgUtils
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class ExpelServiceImpl : ExpelService {
    private val log = LoggerFactory.getLogger(ExpelServiceImpl::class.java)

    override val sessions: MutableMap<Long, ExpelSession> = ConcurrentHashMap()

    // Registry of active expel polls
    override val polls: MutableMap<Long, ExpelPoll> = ConcurrentHashMap()

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
        // remove any running poll instances as well
        polls.remove(guildId)
        log.info("Ended expel poll for guild {}", guildId)
    }

    override fun getExpelSession(guildId: Long): ExpelSession? {
        return sessions[guildId]
    }

    override fun hasPoll(guildId: Long): Boolean {
        return polls.containsKey(guildId)
    }

    override fun getPoll(guildId: Long): ExpelPoll? {
        return polls[guildId]
    }

    override fun getPollCandidates(guildId: Long): Map<Int, Candidate>? {
        return polls[guildId]?.candidates
    }

    override fun setPollCandidates(guildId: Long, candidates: MutableMap<Int, Candidate>) {
        val poll = polls[guildId]
        if (poll != null) {
            poll.candidates.clear()
            poll.candidates.putAll(candidates)
        } else {
            val dummy = ExpelPoll(guildId, 0L, Session(guildId = guildId), null)
            dummy.candidates.clear()
            dummy.candidates.putAll(candidates)
            polls[guildId] = dummy
        }
    }

    override fun removePoll(guildId: Long) {
        polls.remove(guildId)
    }

    override fun startExpelPollUI(
        session: Session,
        channel: GuildMessageChannel,
        allowPK: Boolean,
        durationMillis: Long,
        candidates: Map<Int, Candidate>?,
        callback: (() -> Unit)?
    ) {
        // Create poll instance and register
        val poll = ExpelPoll(channel.guild.idLong, channel.idLong, session, null, callback)
        polls[channel.guild.idLong] = poll

        // Populate candidates
        val map = if (candidates != null) {
            candidates.toMutableMap()
        } else {
            val m = mutableMapOf<Int, Candidate>()
            for (player in session.alivePlayers().values.sortedBy { it.id }) {
                m[player.id] = Candidate(player = player)
            }
            m
        }
        poll.candidates.clear()
        poll.candidates.putAll(map)

        // Start expel session and UI
        // durationMillis expected in ms, ExpelService also stores meta in seconds
        startExpelPoll(session, (durationMillis / 1000L).toInt())
        session.addLog(dev.robothanzo.werewolf.database.documents.LogType.EXPEL_POLL_STARTED, "放逐投票開始", null)

        val embedBuilder = poll.buildResultEmbed("驅逐投票").apply {
            setDescription("30秒後立刻計票，請加快手速!\n若要改票可直接按下要改成的對象\n若要改為棄票需按下原本投給的使用者")
        }
        val buttons = mutableListOf<net.dv8tion.jda.api.components.buttons.Button>()
        poll.candidates.values
            .sortedWith(Candidate.getComparator())
            .forEach { player ->
                val user = channel.guild.getMemberById(player.player.user?.idLong ?: 0L)
                if (user != null) {
                    buttons.add(
                        net.dv8tion.jda.api.components.buttons.Button.danger(
                            "voteExpel" + player.player.id,
                            player.player.nickname + " (" + user.user.name + ")"
                        )
                    )
                }
            }

        val message = channel.sendMessageEmbeds(embedBuilder.build())
            .setComponents(
                *MsgUtils.spreadButtonsAcrossActionRows(buttons).toTypedArray()
            ).complete()

        poll.message = message
        poll.start(channel, allowPK)
    }

    override fun getExpelStatus(guildId: Long): ExpelStatus? {
        val expelSession = sessions[guildId]
        val expelPoll = polls[guildId]

        val candidates: List<ExpelCandidateDto> = when {
            expelPoll != null -> expelPoll.candidates.values.sortedWith(Candidate.getComparator()).map {
                ExpelCandidateDto(
                    id = it.player.id,
                    quit = it.quit,
                    voters = it.electors.map { e -> e.toString() }
                )
            }

            expelSession != null -> expelSession.candidates.values.sortedWith(Candidate.getComparator()).map {
                ExpelCandidateDto(
                    id = it.player.id,
                    quit = it.quit,
                    voters = listOf()
                )
            }

            else -> emptyList()
        }

        return if (expelPoll == null && expelSession == null) null else ExpelStatus(
            voting = (expelSession != null),
            endTime = expelSession?.endTime,
            candidates = candidates
        )
    }
}
