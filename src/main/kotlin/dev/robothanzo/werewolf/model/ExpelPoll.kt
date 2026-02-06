package dev.robothanzo.werewolf.model

import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.audio.Audio
import dev.robothanzo.werewolf.audio.Audio.play
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.DeathCause
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel
import java.util.concurrent.ConcurrentHashMap

class ExpelPoll(
    guildId: Long,
    channelId: Long,
    session: Session,
    message: Message?,
    finishedCallback: (() -> Unit)? = null
) : AbstractPoll(guildId, channelId, session, message, finishedCallback) {

    override fun start(channel: GuildMessageChannel, allowPK: Boolean) {
        // Call super to schedule finish/10s warning
        super.start(channel, allowPK)

        // Play initial expel poll audio
        val vc = session.courtVoiceChannel
        vc?.play(Audio.Resource.EXPEL_POLL)
    }

    override fun onPrepareResultEmbed(winners: List<Candidate>, embed: EmbedBuilder) {
        if (winners.size == 1) {
            val winner = winners.first()
            embed.setDescription("放逐玩家: <@!" + winner.player.user?.idLong + ">")
        } else {
            embed.setDescription("發生平票")
        }
    }

    override fun onSingleWinner(winner: Candidate, channel: GuildMessageChannel, message: Message?, session: Session) {
        // Announce
        message?.reply("投票已結束，正在放逐玩家 <@!" + winner.player.user?.idLong + ">")?.queue()

        // Trigger death
        winner.player.died(DeathCause.EXPEL)

        // Finally remove poll registration
        WerewolfApplication.expelService.removePoll(guildId)
    }

    override fun onPKTie(winners: List<Candidate>, channel: GuildMessageChannel, message: Message?, session: Session) {
        // Reset winners for PK
        val newCandidates = winners.onEach {
            it.electors.clear()
            it.expelPK = true
        }.associateByTo(ConcurrentHashMap()) { it.player.id }

        candidates.clear()
        candidates.putAll(newCandidates)

        // start speech for PK players, after which finish with allowPK=false
        WerewolfApplication.speechService.startSpeechPoll(
            channel.guild, message,
            newCandidates.values.map { it.player }
        ) { finish(channel, false) }
    }

    override fun finish(channel: GuildMessageChannel, allowPK: Boolean, title: String) {
        // Use base behavior (build + send result) and allow our hooks to be called
        super.finish(channel, allowPK, "驅逐投票")

        // If there was no winner or tie without PK, ensure poll is removed
        if (!WerewolfApplication.expelService.hasPoll(guildId)) return

        val winners = getWinners(null)
        if (winners.isEmpty()) {
            // nothing
            WerewolfApplication.expelService.removePoll(guildId)
            return
        }

        if (winners.size > 1 && !allowPK) {
            // tie without PK -> remove poll
            WerewolfApplication.expelService.removePoll(guildId)
        }
    }
}
