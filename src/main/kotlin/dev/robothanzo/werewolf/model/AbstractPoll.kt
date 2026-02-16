package dev.robothanzo.werewolf.model

import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.audio.Audio
import dev.robothanzo.werewolf.audio.Audio.play
import dev.robothanzo.werewolf.database.documents.Player
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.HistoricalPollRecord
import dev.robothanzo.werewolf.utils.MsgUtils
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Simple abstract poll implementation shared by expel/police/wolf kill polls.
 * Subclasses should provide any custom behavior (e.g. PK handling).
 */
abstract class AbstractPoll(
    val guildId: Long,
    val channelId: Long,
    val session: Session,
    var message: Message?,
    var finishedCallback: (() -> Unit)? = null
) {
    val candidates: MutableMap<Int, Candidate> = ConcurrentHashMap()
    var poll10sTask: TimerTask? = null
    var pollFinishTask: TimerTask? = null
    var durationMillis: Long = 30000L

    /**
     * Default start schedules the 10s warning and the finish callback. Subclasses may override
     * and must call super.start(...) to retain the scheduling behavior.
     */
    open fun start(channel: GuildMessageChannel, allowPK: Boolean = false) {
        // cancel any existing tasks
        poll10sTask?.cancel()
        pollFinishTask?.cancel()

        if (durationMillis > 10000L) {
            val t = Timer()
            poll10sTask = object : TimerTask() {
                override fun run() {
                    on10sRemaining(channel)
                }
            }
            t.schedule(poll10sTask, durationMillis - 10000L)
        }

        val t2 = Timer()
        pollFinishTask = object : TimerTask() {
            override fun run() {
                pollFinishTask = null
                finish(channel, allowPK)
            }
        }
        t2.schedule(pollFinishTask, durationMillis)
    }

    /**
     * Default finish behavior tallies votes, builds and sends the result embed, and delegates
     * per-poll actions (single winner / PK tie) to overrides. Subclasses may override and may
     * call super.finish(...) to keep the common behavior.
     */
    open fun finish(channel: GuildMessageChannel, allowPK: Boolean = false, title: String = "投票") {
        // cancel any pending tasks
        poll10sTask?.cancel()
        pollFinishTask?.cancel()

        WerewolfApplication.gameSessionService.withLockedSession(guildId) { lockedSession ->
            val winners = getWinners(null)

            if (winners.isEmpty()) {
                message?.reply("沒有人投票，本次${title}無人出局")?.queue()
                finishedCallback?.invoke()
                return@withLockedSession
            }

            // Record poll results for replay
            val pollRecord = HistoricalPollRecord(
                day = lockedSession.day,
                title = title,
                votes = candidates.values.associate { it.player.id to it.electors.toList() }
            )
            lockedSession.stateData.historicalPolls.add(pollRecord)

            // Allow subclass to populate a description or other fields before sending
            val resultEmbed = buildResultEmbed(title)
            onPrepareResultEmbed(winners, resultEmbed)
            sendVoteResult(channel, message, resultEmbed, lockedSession)

            if (winners.size == 1) {
                onSingleWinner(winners.first(), channel, message, lockedSession)
                finishedCallback?.invoke()
            } else {
                if (allowPK) {
                    onPKTie(winners, channel, message, lockedSession)
                } else {
                    message?.reply("再次平票，無人出局")?.queue()
                    val tieEmbed = buildResultEmbed(title).apply { setDescription("再次發生平票，本次${title}無人出局") }
                    sendVoteResult(channel, message, tieEmbed, lockedSession)
                    finishedCallback?.invoke()
                }
            }
        }
    }

    // Hooks for subclasses to customize behavior
    open fun on10sRemaining(channel: GuildMessageChannel) {
        WerewolfApplication.gameSessionService.getSession(guildId).ifPresent {
            it.courtVoiceChannel?.play(Audio.Resource.POLL_10S_REMAINING)
        }
    }

    open fun onPrepareResultEmbed(winners: List<Candidate>, embed: EmbedBuilder) {}

    open fun onSingleWinner(winner: Candidate, channel: GuildMessageChannel, message: Message?, session: Session) {}

    open fun onPKTie(winners: List<Candidate>, channel: GuildMessageChannel, message: Message?, session: Session) {}

    /**
     * By default, any player is eligible to vote in a poll. Subclasses may override
     * to restrict eligible voters (e.g., police: non-enrollees; wolves: only wolves).
     */
    open fun isEligibleVoter(player: Player): Boolean {
        return true
    }

    fun getWinners(police: Player?): List<Candidate> {
        return Candidate.getWinner(candidates.values, police)
    }

    fun buildResultEmbed(title: String): EmbedBuilder {
        return EmbedBuilder().apply {
            setTitle(title)
            setColor(MsgUtils.randomColor)
        }
    }

    fun sendVoteResult(channel: GuildMessageChannel, message: Message?, resultEmbed: EmbedBuilder, session: Session) {
        val voted = mutableListOf<Long>()

        candidates.values
            .filter { !it.quit }
            .forEach { candidate ->
                candidate.player.user?.let { user ->
                    voted.addAll(candidate.electors)
                    resultEmbed.addField(
                        candidate.player.nickname + " (" + user.name + ")",
                        candidate.getElectorsAsMention().joinToString("、"), false
                    )
                }
            }

        val discarded = mutableListOf<String>()
        for (player in session.alivePlayers().values) {
            if (isEligibleVoter(player) && !voted.contains(player.user?.idLong)) {
                discarded.add("<@!" + player.user?.idLong + ">")
            }
        }

        resultEmbed.addField("棄票", if (discarded.isEmpty()) "無" else discarded.joinToString("、"), false)

        message?.channel?.sendMessageEmbeds(resultEmbed.build())?.queue()
            ?: channel.sendMessageEmbeds(resultEmbed.build()).queue()
    }
}
