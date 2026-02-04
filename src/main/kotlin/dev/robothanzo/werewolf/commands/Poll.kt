package dev.robothanzo.werewolf.commands

import dev.robothanzo.jda.interactions.annotations.slash.Command
import dev.robothanzo.jda.interactions.annotations.slash.Subcommand
import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.audio.Audio
import dev.robothanzo.werewolf.audio.Audio.play
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.DeathCause
import dev.robothanzo.werewolf.model.Candidate
import dev.robothanzo.werewolf.utils.CmdUtils
import dev.robothanzo.werewolf.utils.MsgUtils
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import java.util.concurrent.ConcurrentHashMap
import dev.robothanzo.jda.interactions.annotations.Button as AnnotationButton

@Command
class Poll {
    companion object {
        @JvmField
        val expelCandidates: MutableMap<Long, MutableMap<Int, Candidate>> = ConcurrentHashMap()

        fun handleExpelPK(
            session: Session, channel: GuildMessageChannel, message: Message?,
            winners: List<Candidate>
        ) {
            message?.reply("平票，請PK")?.queue()

            val newCandidates = winners.onEach {
                it.electors.clear()
                it.expelPK = true
            }.associateByTo(ConcurrentHashMap()) { it.player.id }

            expelCandidates[channel.guild.idLong] = newCandidates

            WerewolfApplication.speechService.startSpeechPoll(
                channel.guild, message,
                newCandidates.values.map { it.player }
            ) { startExpelPoll(session, channel, false) }
        }

        fun startExpelPoll(session: Session, channel: GuildMessageChannel, allowPK: Boolean) {
            val voiceChannel = channel.guild.getVoiceChannelById(session.courtVoiceChannelId)
            voiceChannel?.play(Audio.Resource.EXPEL_POLL)
            CmdUtils.schedule({
                val vc = channel.guild.getVoiceChannelById(session.courtVoiceChannelId)
                vc?.play(Audio.Resource.POLL_10S_REMAINING)
            }, 20000)

            val embedBuilder = EmbedBuilder().apply {
                setTitle("驅逐投票")
                setDescription("30秒後立刻計票，請加快手速!\n若要改票可直接按下要改成的對象\n若要改為棄票需按下原本投給的使用者")
                setColor(MsgUtils.randomColor)
            }

            val buttons = mutableListOf<Button>()

            expelCandidates[channel.guild.idLong]?.values
                ?.sortedWith(Candidate.getComparator())
                ?.forEach { player ->
                    val user = channel.guild.getMemberById(player.player.userId ?: 0L)
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
                vc?.play(Audio.Resource.POLL_10S_REMAINING)
            }, 20000)

            CmdUtils.schedule({
                val currentCandidates = expelCandidates[channel.guild.idLong]?.values ?: emptyList()
                val winners = Candidate.getWinner(currentCandidates, null)
                
                if (winners.isEmpty()) {
                    message?.reply("沒有人投票，本次驅逐無人出局")?.queue()
                    expelCandidates.remove(channel.guild.idLong)
                    return@schedule
                }

                if (winners.size == 1) {
                    val winner = winners.first()
                    message?.reply("投票已結束，正在放逐玩家 <@!" + winner.player.userId + ">")?.queue()

                    val resultEmbed = EmbedBuilder().apply {
                        setTitle("驅逐投票")
                        setColor(MsgUtils.randomColor)
                        setDescription("放逐玩家: <@!" + winner.player.userId + ">")
                    }
                    sendVoteResult(session, channel, message, resultEmbed, expelCandidates)

                    expelCandidates.remove(channel.guild.idLong)

                    // Trigger death for expel
                    winner.player.userId?.let { uid ->
                        WerewolfApplication.gameActionService.markPlayerDead(
                            session,
                            uid,
                            true,
                            DeathCause.EXPEL
                        )
                    }

                } else {
                    if (allowPK) {
                        val resultEmbed = EmbedBuilder().apply {
                            setTitle("驅逐投票")
                            setColor(MsgUtils.randomColor)
                            setDescription("發生平票")
                        }
                        sendVoteResult(session, channel, message, resultEmbed, expelCandidates)

                        handleExpelPK(session, channel, message, winners)
                    } else {
                        val resultEmbed = EmbedBuilder().apply {
                            setTitle("驅逐投票")
                            setColor(MsgUtils.randomColor)
                            setDescription("再次發生平票，本次驅逐無人出局")
                        }
                        message?.reply("再次平票，無人出局")?.queue()
                        sendVoteResult(session, channel, message, resultEmbed, expelCandidates)
                        expelCandidates.remove(channel.guild.idLong)
                    }
                }
            }, 30000)
        }

        fun sendVoteResult(
            session: Session, channel: GuildMessageChannel, message: Message?,
            resultEmbed: EmbedBuilder,
            candidates: Map<Long, Map<Int, Candidate>>
        ) {
            val voted = mutableListOf<Long>()

            candidates[channel.guild.idLong]?.values?.forEach { candidate ->
                // userId safely checked
                candidate.player.userId?.let { uid ->
                    val user = WerewolfApplication.jda.getUserById(uid)
                    voted.addAll(candidate.electors)
                    if (user != null) {
                        resultEmbed.addField(
                            candidate.player.nickname + " (" + user.name + ")",
                            candidate.getElectorsAsMention().joinToString("、"), false
                        )
                    }
                }
            }

            val discarded = mutableListOf<String>()
            for (player in session.fetchAlivePlayers().values) {
                if (player.userId != null && !voted.contains(player.userId)) {
                    discarded.add("<@!" + player.userId + ">")
                }
            }

            resultEmbed.addField(
                "棄票",
                if (discarded.isEmpty()) "無" else discarded.joinToString("、"),
                false
            )

            message?.channel?.sendMessageEmbeds(resultEmbed.build())?.queue()
                ?: channel.sendMessageEmbeds(resultEmbed.build()).queue()
        }
    }

    @Subcommand(description = "啟動驅逐投票")
    fun expel(event: SlashCommandInteractionEvent) {
        event.deferReply().queue()
        if (!CmdUtils.isAdmin(event)) return
        val session = CmdUtils.getSession(event.guild!!) ?: return

        val candidates = session.players.values.asSequence()
            .filter { it.isAlive }
            .map { Candidate(player = it, expelPK = true) }
            .associateByTo(ConcurrentHashMap()) { it.player.id }
            
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
            val guild = event.guild ?: return

            WerewolfApplication.policeService.forceStartVoting(guild.idLong)
            event.hook.editOriginal(":white_check_mark:").queue()
        }

        @AnnotationButton
        fun enrollPolice(event: ButtonInteractionEvent) {
            WerewolfApplication.policeService.enrollPolice(event)
        }
    }
}
