package dev.robothanzo.werewolf.commands

import dev.robothanzo.jda.interactions.annotations.slash.Command
import dev.robothanzo.jda.interactions.annotations.slash.Subcommand
import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.audio.Audio
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.model.Candidate
import dev.robothanzo.werewolf.utils.CmdUtils
import dev.robothanzo.werewolf.utils.MsgUtils
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import dev.robothanzo.jda.interactions.annotations.Button as AnnotationButton

@Command
class Poll {
    companion object {
        private val log = LoggerFactory.getLogger(Poll::class.java)

        @JvmField
        val expelCandidates: MutableMap<Long, MutableMap<Int, Candidate>> = ConcurrentHashMap()

        fun handleExpelPK(
            session: Session, channel: GuildMessageChannel, message: Message?,
            winners: List<Candidate>
        ) {
            message?.reply("平票，請PK")?.queue()
            val newCandidates: MutableMap<Int, Candidate> = ConcurrentHashMap()
            for (winner in winners) {
                winner.electors.clear()
                winner.expelPK = true
                newCandidates[winner.player.id] = winner
            }
            expelCandidates[channel.guild.idLong] = newCandidates
            WerewolfApplication.speechService.startSpeechPoll(
                channel.guild, message,
                newCandidates.values.map { it.player }
            ) { startExpelPoll(session, channel, false) }
        }

        fun startExpelPoll(session: Session, channel: GuildMessageChannel, allowPK: Boolean) {
            val voiceChannel = channel.guild.getVoiceChannelById(session.courtVoiceChannelId)
            if (voiceChannel != null) {
                Audio.play(Audio.Resource.EXPEL_POLL, voiceChannel)
            }
            val embedBuilder = EmbedBuilder().setTitle("驅逐投票")
                .setDescription("30秒後立刻計票，請加快手速!\n若要改票可直接按下要改成的對象\n若要改為棄票需按下原本投給的使用者")
                .setColor(MsgUtils.randomColor)
            val buttons: MutableList<net.dv8tion.jda.api.components.buttons.Button> = LinkedList()
            for (player in expelCandidates[channel.guild.idLong]!!.values
                .sortedWith(Candidate.getComparator())) {
                val user = channel.guild.getMemberById(player.player.userId!!)
                if (user != null) {
                    buttons.add(
                        Button.danger(
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
            CmdUtils.schedule({
                val vc = channel.guild.getVoiceChannelById(session.courtVoiceChannelId)
                if (vc != null) {
                    Audio.play(Audio.Resource.POLL_10S_REMAINING, vc)
                }
            }, 20000)
            CmdUtils.schedule({
                val winners = Candidate.getWinner(
                    expelCandidates[channel.guild.idLong]!!.values,
                    null
                )
                if (winners.isEmpty()) {
                    message?.reply("沒有人投票，本次驅逐無人出局")?.queue()
                    expelCandidates.remove(channel.guild.idLong)
                    return@schedule
                }

                if (winners.size == 1) {
                    val winner = winners.first()
                    message?.reply("投票已結束，正在放逐玩家 <@!" + winner.player.userId + ">")?.queue()

                    val resultEmbed = EmbedBuilder().setTitle("驅逐投票").setColor(MsgUtils.randomColor)
                        .setDescription("放逐玩家: <@!" + winner.player.userId + ">")
                    sendVoteResult(session, channel, message, resultEmbed, expelCandidates, false)

                    expelCandidates.remove(channel.guild.idLong)

                    // Trigger death for expel
                    WerewolfApplication.gameActionService.markPlayerDead(
                        channel.guild.idLong,
                        winner.player.userId!!,
                        true
                    )

                } else {
                    if (allowPK) {
                        val resultEmbed = EmbedBuilder().setTitle("驅逐投票").setColor(MsgUtils.randomColor)
                            .setDescription("發生平票")
                        sendVoteResult(session, channel, message, resultEmbed, expelCandidates, false)

                        handleExpelPK(session, channel, message, winners)
                    } else {
                        val resultEmbed = EmbedBuilder().setTitle("驅逐投票").setColor(MsgUtils.randomColor)
                            .setDescription("再次發生平票，本次驅逐無人出局")
                        message?.reply("再次平票，無人出局")?.queue()
                        sendVoteResult(session, channel, message, resultEmbed, expelCandidates, false)
                        expelCandidates.remove(channel.guild.idLong)
                    }
                }
            }, 30000)
        }

        fun sendVoteResult(
            session: Session, channel: GuildMessageChannel, message: Message?,
            resultEmbed: EmbedBuilder,
            candidates: Map<Long, Map<Int, Candidate>>, police: Boolean
        ) {
            val voted: MutableList<Long> = LinkedList()
            for (candidate in candidates[channel.guild.idLong]!!.values) {
                val user = WerewolfApplication.jda.getUserById(candidate.player.userId!!)
                voted.addAll(candidate.electors)
                resultEmbed.addField(
                    candidate.player.nickname + " (" + user!!.name + ")",
                    java.lang.String.join("、", candidate.getElectorsAsMention()), false
                )
            }
            val discarded: MutableList<String> = LinkedList()
            for (player in session.fetchAlivePlayers().values) {
                if (!voted.contains(player.userId)) {
                    discarded.add("<@!" + player.userId + ">")
                }
            }
            resultEmbed.addField(
                "棄票",
                if (discarded.isEmpty()) "無" else java.lang.String.join("、", discarded),
                false
            )
            if (message != null)
                message.channel.sendMessageEmbeds(resultEmbed.build()).queue()
            else
                channel.sendMessageEmbeds(resultEmbed.build()).queue()
        }
    }

    @Subcommand(description = "啟動驅逐投票")
    fun expel(event: SlashCommandInteractionEvent) {
        event.deferReply().queue()
        if (!CmdUtils.isAdmin(event)) return
        val session = CmdUtils.getSession(event.guild!!) ?: return

        val candidates: MutableMap<Int, Candidate> = ConcurrentHashMap()
        for (p in session.players.values) {
            if (p.isAlive) {
                candidates[p.id] = Candidate(player = p, expelPK = true)
            }
        }
        expelCandidates[event.guild!!.idLong] = candidates
        startExpelPoll(session, event.channel as GuildMessageChannel, true)
        event.hook.editOriginal(":white_check_mark:").queue()
    }

    @Subcommand
    class Police {
        @Subcommand(description = "啟動警長參選投票")
        fun enroll(event: SlashCommandInteractionEvent) {
            event.deferReply().queue()
            if (!CmdUtils.isAdmin(event)) return
            val session = CmdUtils.getSession(event.guild!!) ?: return

            if (WerewolfApplication.policeService.sessions.containsKey(event.guild!!.idLong)) {
                event.hook.editOriginal(":x: 警長選舉已在進行中").queue()
                return
            }

            WerewolfApplication.policeService.startEnrollment(
                session,
                event.channel as GuildMessageChannel,
                null
            )
            event.hook.editOriginal(":white_check_mark:").queue()
        }

        @Subcommand(description = "啟動警長投票 (會自動開始，請只在出問題時使用)")
        fun start(event: SlashCommandInteractionEvent) {
            event.deferReply().queue()
            if (!CmdUtils.isAdmin(event)) return

            WerewolfApplication.policeService.forceStartVoting(event.guild!!.idLong)
            event.hook.editOriginal(":white_check_mark:").queue()
        }

        @AnnotationButton
        fun enrollPolice(event: ButtonInteractionEvent) {
            WerewolfApplication.policeService.enrollPolice(event)
        }
    }
}
