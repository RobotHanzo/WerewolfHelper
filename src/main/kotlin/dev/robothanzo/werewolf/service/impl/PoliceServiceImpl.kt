package dev.robothanzo.werewolf.service.impl

import dev.robothanzo.werewolf.audio.Audio
import dev.robothanzo.werewolf.commands.Poll
import dev.robothanzo.werewolf.database.documents.LogType
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.model.Candidate
import dev.robothanzo.werewolf.model.PoliceSession
import dev.robothanzo.werewolf.security.SessionRepository
import dev.robothanzo.werewolf.service.*
import dev.robothanzo.werewolf.utils.CmdUtils
import dev.robothanzo.werewolf.utils.MsgUtils
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.components.selections.EntitySelectMenu
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class PoliceServiceImpl(
    private val sessionRepository: SessionRepository,
    private val discordService: DiscordService,
    private val gameSessionService: GameSessionService,
    private val speechService: SpeechService
) : PoliceService {
    private val log = LoggerFactory.getLogger(PoliceServiceImpl::class.java)

    override val sessions: MutableMap<Long, PoliceSession> = ConcurrentHashMap()

    override fun startEnrollment(session: Session, channel: GuildMessageChannel, message: Message?) {
        if (sessions.containsKey(session.guildId))
            return

        val policeSession = PoliceSession(
            guildId = session.guildId,
            channelId = channel.idLong,
            session = session
        )
        sessions[session.guildId] = policeSession
        next(session.guildId)
    }

    override fun enrollPolice(event: ButtonInteractionEvent) {
        event.deferReply(true).queue()
        val session = CmdUtils.getSession(event.guild!!) ?: return

        val policeSession = sessions[event.guild!!.idLong]
        if (policeSession == null) {
            event.hook.editOriginal(":x: 無法參選，時間已到").queue()
            return
        }

        val iterator = policeSession.candidates.entries.iterator()
        while (iterator.hasNext()) {
            val candidate = iterator.next()
            if (event.user.idLong == candidate.value.player!!.userId) {
                if (policeSession.state.canEnroll()) { // ENROLLMENT -> Remove completely
                    iterator.remove()
                    event.hook.editOriginal(":white_check_mark: 已取消參選").queue()

                    val metadata: MutableMap<String, Any> = HashMap()
                    metadata["playerId"] = candidate.value.player!!.id
                    metadata["playerName"] = candidate.value.player!!.nickname
                    session.addLog(
                        LogType.POLICE_UNENROLLED,
                        candidate.value.player!!.nickname + " 已取消參選警長", metadata
                    )
                    gameSessionService.broadcastSessionUpdate(session)

                } else if (policeSession.state.canQuit()) { // UNENROLLMENT -> Mark quit
                    candidate.value.quit = true
                    event.hook.editOriginal(":white_check_mark: 已取消參選").queue()
                    event.guild!!.getTextChannelById(session.courtTextChannelId)
                        ?.sendMessage(event.user.asMention + " 已取消參選")?.queue()

                    val metadata = mutableMapOf<String, Any>()
                    metadata["playerId"] = candidate.value.player!!.id
                    metadata["playerName"] = candidate.value.player!!.nickname
                    session.addLog(
                        LogType.POLICE_UNENROLLED,
                        "${candidate.value.player!!.nickname} 已取消參選警長", metadata
                    )
                    gameSessionService.broadcastSessionUpdate(session)
                } else {
                    event.hook.editOriginal(":x: 無法取消參選，投票已開始").queue()
                }
                return
            }
        }

        if (!policeSession.state.canEnroll()) {
            event.hook.editOriginal(":x: 無法參選，時間已到").queue()
            return
        }

        for (player in session.fetchAlivePlayers().values) {
            if (event.user.idLong == player.userId) {
                policeSession.candidates[player.id] = Candidate(player = player)
                event.hook.editOriginal(":white_check_mark: 已參選").queue()

                val metadata = mutableMapOf<String, Any>()
                metadata["playerId"] = player.id
                metadata["playerName"] = player.nickname
                session.addLog(
                    LogType.POLICE_ENROLLED,
                    "${player.nickname} 已參選警長", metadata
                )

                gameSessionService.broadcastSessionUpdate(session)
                return
            }
        }
        event.hook.editOriginal(":x: 你不是玩家").queue()
    }

    override fun next(guildId: Long) {
        val policeSession = sessions[guildId] ?: return

        val jda = discordService.jda
        val guild = jda?.getGuildById(guildId)
        if (guild == null) {
            interrupt(guildId)
            return
        }
        val channel = guild.getTextChannelById(policeSession.channelId)
        if (channel == null) {
            interrupt(guildId)
            return
        }

        when (policeSession.state) {
            PoliceSession.State.NONE -> {
                policeSession.state = PoliceSession.State.ENROLLMENT
                policeSession.stageEndTime = System.currentTimeMillis() + 30000
                policeSession.candidates.clear()

                policeSession.session!!.addLog(
                    LogType.POLICE_ENROLLMENT_STARTED,
                    "警長參選已開始", null
                )
                gameSessionService.broadcastSessionUpdate(policeSession.session!!)

                val vc = guild.getVoiceChannelById(policeSession.session!!.courtVoiceChannelId)
                if (vc != null) {
                    Audio.play(Audio.Resource.POLICE_ENROLL, vc)
                }
                val embed = EmbedBuilder()
                    .setTitle("參選警長").setDescription("30秒後立刻進入辯論，請加快手速!")
                    .setColor(MsgUtils.randomColor)

                channel.sendMessageEmbeds(embed.build())
                    .setComponents(ActionRow.of(Button.success("enrollPolice", "參選警長")))
                    .queue { msg -> policeSession.message = msg }

                CmdUtils.schedule({
                    val vc10s = guild.getVoiceChannelById(policeSession.session!!.courtVoiceChannelId)
                    if (vc10s != null) {
                        Audio.play(Audio.Resource.ENROLL_10S_REMAINING, vc10s)
                    }
                }, 20000)
                CmdUtils.schedule({ next(guildId) }, 30000)
            }

            PoliceSession.State.ENROLLMENT -> {
                if (policeSession.candidates.isEmpty()) {
                    if (policeSession.message != null)
                        policeSession.message!!.reply("無人參選，警徽撕毀").queue()
                    interrupt(guildId)
                    return
                }

                val candidateMentions = policeSession.candidates.values.asSequence()
                    .sortedWith(Candidate.getComparator())
                    .map { "<@!${it.player!!.userId}>" }
                    .toMutableList()

                if (policeSession.candidates.size == 1) {
                    if (policeSession.message != null)
                        policeSession.message!!.reply("只有" + candidateMentions.first() + "參選，直接當選").queue()
                    setPolice(policeSession.session!!, policeSession.candidates.values.iterator().next(), channel)
                    interrupt(guildId)
                    return
                }

                if (policeSession.message != null) {
                    policeSession.message!!.replyEmbeds(
                        EmbedBuilder().setTitle("參選警長結束")
                            .setDescription(
                                "參選的有: ${candidateMentions.joinToString("、")}\n備註:你可隨時再按一次按鈕以取消參選"
                            )
                            .setColor(MsgUtils.randomColor).build()
                    ).queue()
                }

                policeSession.state = PoliceSession.State.SPEECH
                gameSessionService.broadcastSessionUpdate(policeSession.session!!)

                // Start speech
                speechService.startSpeechPoll(
                    guild, policeSession.message!!,
                    policeSession.candidates.values.mapNotNull { it.player }
                ) { next(guildId) }
            }

            PoliceSession.State.SPEECH -> {
                policeSession.state = PoliceSession.State.UNENROLLMENT
                policeSession.stageEndTime = System.currentTimeMillis() + 20000
                gameSessionService.broadcastSessionUpdate(policeSession.session!!)

                if (policeSession.message != null) {
                    policeSession.message!!.channel.sendMessage("政見發表結束，參選人有20秒進行退選，20秒後將自動開始投票")
                        .queue()
                }

                CmdUtils.schedule({ next(guildId) }, 20000)
            }

            PoliceSession.State.UNENROLLMENT -> {
                policeSession.state = PoliceSession.State.VOTING
                policeSession.stageEndTime = System.currentTimeMillis() + 30000

                if (policeSession.candidates.values.stream().allMatch { it.quit }) {
                    if (policeSession.message != null)
                        policeSession.message!!.reply("所有人退選，警徽撕毀").queue()
                    interrupt(guildId)
                    return
                }

                gameSessionService.broadcastSessionUpdate(policeSession.session!!)
                startVoting(channel, false, policeSession)
            }

            else -> {
                // Logic handled in startVoting callback
            }
        }
    }

    override fun interrupt(guildId: Long) {
        val policeSession = sessions.remove(guildId)
        if (policeSession != null) {
            gameSessionService.broadcastSessionUpdate(policeSession.session!!)
        }
    }

    override fun forceStartVoting(guildId: Long) {
        val policeSession = sessions[guildId]
        if (policeSession != null) {
            policeSession.state = PoliceSession.State.UNENROLLMENT // trick next() to go to VOTING
            next(guildId)
        }
    }

    private fun startVoting(channel: GuildMessageChannel, allowPK: Boolean, policeSession: PoliceSession) {
        val vc = channel.guild.getVoiceChannelById(policeSession.session!!.courtVoiceChannelId)
        if (vc != null) {
            Audio.play(Audio.Resource.POLICE_POLL, vc)
        }
        val embedBuilder = EmbedBuilder().setTitle("警長投票")
            .setDescription("30秒後立刻計票，請加快手速!\n若要改票可直接按下要改成的對象\n若要改為棄票需按下原本投給的使用者")
            .setColor(MsgUtils.randomColor)

        val buttons = mutableListOf<Button>()
        policeSession.candidates.values.asSequence()
            .sortedWith(Candidate.getComparator())
            .filter { !it.quit }
            .forEach { player ->
                val user = channel.guild.getMemberById(player.player!!.userId!!)
                if (user != null) {
                    buttons.add(
                        Button.primary(
                            "votePolice${player.player!!.id}",
                            "${player.player!!.nickname} (${user.user.name})"
                        )
                    )
                }
            }

        channel.sendMessageEmbeds(embedBuilder.build())
            .setComponents(MsgUtils.spreadButtonsAcrossActionRows(buttons))
            .queue()

        CmdUtils.schedule({
            val vc = channel.guild.getVoiceChannelById(policeSession.session!!.courtVoiceChannelId)
            if (vc != null) {
                Audio.play(Audio.Resource.POLL_10S_REMAINING, vc)
            }
        }, 20000)
        CmdUtils.schedule({
            finishVoting(channel, allowPK, policeSession)
        }, 30000)
    }

    private fun finishVoting(channel: GuildMessageChannel, allowPK: Boolean, policeSession: PoliceSession) {
        val winners = Candidate.getWinner(policeSession.candidates.values, null)
        if (winners.isEmpty()) {
            if (policeSession.message != null)
                policeSession.message!!.reply("沒有人投票，警徽撕毀").queue()
            interrupt(policeSession.guildId)
            return
        }

        if (winners.size == 1) {
            val winner = winners.first()
            if (policeSession.message != null)
                policeSession.message!!.reply("投票已結束，<@!" + winner.player!!.userId + "> 獲勝").queue()

            val resultEmbed = EmbedBuilder().setTitle("警長投票").setColor(MsgUtils.randomColor)
                .setDescription("獲勝玩家: <@!" + winner.player!!.userId + ">")

            val wrapper = mutableMapOf<Long, Map<Int, Candidate>>()
            wrapper[channel.guild.idLong] = policeSession.candidates
            Poll.sendVoteResult(
                policeSession.session!!, channel, policeSession.message!!, resultEmbed, wrapper,
                true
            )

            setPolice(policeSession.session!!, winner, channel)
            interrupt(policeSession.guildId)
        } else {
            if (allowPK) {
                val resultEmbed = EmbedBuilder().setTitle("警長投票")
                    .setColor(MsgUtils.randomColor)
                    .setDescription("發生平票")
                val wrapper = mutableMapOf<Long, Map<Int, Candidate>>()
                wrapper[channel.guild.idLong] = policeSession.candidates
                Poll.sendVoteResult(
                    policeSession.session!!, channel, policeSession.message!!, resultEmbed,
                    wrapper, true
                )

                handlePK(channel, winners, policeSession)
            } else {
                val resultEmbed = EmbedBuilder().setTitle("警長投票")
                    .setColor(MsgUtils.randomColor)
                    .setDescription("平票第二次，警徽撕毀")
                if (policeSession.message != null)
                    policeSession.message!!.reply("平票第二次，警徽撕毀").queue()
                val wrapper = mutableMapOf<Long, Map<Int, Candidate>>()
                wrapper[channel.guild.idLong] = policeSession.candidates
                Poll.sendVoteResult(
                    policeSession.session!!, channel, policeSession.message!!, resultEmbed,
                    wrapper, true
                )
                interrupt(policeSession.guildId)
            }
        }
    }

    private fun handlePK(channel: GuildMessageChannel, winners: List<Candidate>, policeSession: PoliceSession) {
        if (policeSession.message != null)
            policeSession.message!!.reply("平票，請PK").queue()

        // Clear votes and reset candidates to only winners
        val newCandidates: MutableMap<Int, Candidate> = ConcurrentHashMap()
        for (winner in winners) {
            winner.electors.clear()
            newCandidates[winner.player!!.id] = winner
        }
        policeSession.candidates.clear()
        policeSession.candidates.putAll(newCandidates)

        speechService.startSpeechPoll(
            channel.guild, policeSession.message!!,
            newCandidates.values.mapNotNull { it.player }
        ) {
            policeSession.state = PoliceSession.State.VOTING
            policeSession.stageEndTime = System.currentTimeMillis() + 30000
            gameSessionService.broadcastSessionUpdate(policeSession.session!!)
            startVoting(channel, false, policeSession)
        }
    }

    private fun setPolice(session: Session, winner: Candidate, channel: GuildMessageChannel) {
        val member = channel.guild
            .getMemberById(winner.player!!.userId!!)
        if (member != null)
            member.modifyNickname(member.effectiveName + " [警長]").queue()

        val p = session.players[winner.player!!.id.toString()]
        if (p != null) {
            p.police = true
        }
        sessionRepository.save(session)

        val metadata = mutableMapOf<String, Any>()
        metadata["playerId"] = winner.player!!.id
        metadata["playerName"] = winner.player!!.nickname
        session.addLog(
            LogType.POLICE_ELECTED,
            "${winner.player!!.nickname} 當當選警長", metadata
        )

        gameSessionService.broadcastSessionUpdate(session)
    }


    // Transfer Police Implementation
    private val transferSessions: MutableMap<Long, TransferPoliceSession> = ConcurrentHashMap()

    override fun transferPolice(
        session: Session,
        guild: Guild,
        player: Session.Player,
        callback: (() -> Unit)?
    ) {
        if (player.police) {
            val senderId = player.userId!!
            val transferSession = TransferPoliceSession(
                guildId = guild.idLong,
                senderId = senderId,
                callback = callback
            )
            transferSessions[guild.idLong] = transferSession

            val selectMenu = EntitySelectMenu
                .create("selectNewPolice", EntitySelectMenu.SelectTarget.USER)
                .setMinValues(1)
                .setMaxValues(1)

            for (p in session.fetchAlivePlayers().values) {
                if (p.userId == player.userId) continue
                val user = discordService.jda!!.getUserById(p.userId!!) // Assuming user cached or available
                if (user != null) {
                    transferSession.possibleRecipientIds.add(p.userId!!)
                }
            }

            val courtChannel = guild.getTextChannelById(session.courtTextChannelId)!!
            val message = courtChannel.sendMessageEmbeds(
                EmbedBuilder()
                    .setTitle("移交警徽").setColor(MsgUtils.randomColor)
                    .setDescription("請選擇要移交警徽的對象，若要撕掉警徽，請按下撕毀按鈕\n請在30秒內做出選擇，否則警徽將被自動撕毀")
                    .build()
            )
                .setComponents(
                    ActionRow.of(selectMenu.build()),
                    ActionRow.of(
                        Button.success("confirmNewPolice", "移交"),
                        Button.danger("destroyPolice", "撕毀")
                    )
                )
                .complete()

            CmdUtils.schedule({
                if (transferSessions.remove(guild.idLong) != null) {
                    message.reply("警徽已自動撕毀").queue()
                    callback?.invoke()
                }
            }, 30000)
        } else {
            callback?.invoke()
        }
    }

    override fun selectNewPolice(event: EntitySelectInteractionEvent) {
        if (transferSessions.containsKey(event.guild!!.idLong)) {
            val target = event.mentions.members.first()
            val session = transferSessions[event.guild!!.idLong]!!

            if (!session.possibleRecipientIds.contains(target.idLong)) {
                event.reply(":x: 你不能移交警徽給這個人").setEphemeral(true).queue()
            } else {
                if (session.senderId == event.user.idLong) {
                    val guildSession = sessionRepository.findByGuildId(event.guild!!.idLong).orElse(null) ?: return
                    for (player in guildSession.players.values) {
                        if (player.userId == target.idLong) {
                            session.recipientId = player.id
                            event.reply(":white_check_mark: 請按下移交來完成移交動作").setEphemeral(true).queue()
                            break
                        }
                    }
                } else {
                    event.reply(":x: 你不是原本的警長").setEphemeral(true).queue()
                }
            }
        }
    }

    override fun confirmNewPolice(event: ButtonInteractionEvent) {
        if (transferSessions.containsKey(event.guild!!.idLong)) {
            val session = transferSessions[event.guild!!.idLong]!!
            if (session.senderId == event.user.idLong) {
                if (session.recipientId != null) {
                    // Update Recipient
                    val recipientPlayer = sessionRepository.findByGuildId(event.guild!!.idLong).get()
                        .players[session.recipientId.toString()]!!
                    recipientPlayer.police = true

                    // We need to update DB directly for specific fields if we want atomicity, 
                    // but here we are loading session. 
                    // To follow original logic: set("players." + session.getRecipientId() + ".police", true)
                    // But using repository save is fine for now as we are in single thread mostly per request.
                    // Wait, original logic used MongoDB Updates.set.
                    // I should reproduce the side effects: Nickname update and DB update.

                    // Update session object
                    val guildSession = sessionRepository.findByGuildId(event.guild!!.idLong).get()
                    guildSession.players[session.recipientId.toString()]!!.police = true

                    val recipientMember = event.guild!!.getMemberById(recipientPlayer.userId!!)
                    if (recipientMember != null) {
                        recipientPlayer.updateNickname(recipientMember)
                        event.reply(":white_check_mark: 警徽已移交給 " + recipientMember.asMention).queue()
                    }

                    // Update Sender
                    val senderEntry = guildSession.players.values.firstOrNull { it.userId == session.senderId }
                    senderEntry?.police = false
                    val senderMember = event.guild!!.getMemberById(session.senderId)
                    if (senderEntry != null && senderMember != null) {
                        senderEntry.updateNickname(senderMember)
                    }

                    sessionRepository.save(guildSession)
                    log.info("Transferred police to {} in guild {}", session.recipientId, event.guild!!.idLong)
                    transferSessions.remove(event.guild!!.idLong)

                    session.callback?.invoke()

                } else {
                    event.reply(":x: 請先選擇要移交警徽的對象").setEphemeral(true).queue()
                }
            } else {
                event.reply(":x: 你不是原本的警長").setEphemeral(true).queue()
            }
        }
    }

    override fun destroyPolice(event: ButtonInteractionEvent) {
        if (transferSessions.containsKey(event.guild!!.idLong)) {
            val session = transferSessions[event.guild!!.idLong]!!
            if (session.senderId == event.user.idLong) {
                transferSessions.remove(event.guild!!.idLong)
                event.reply(":white_check_mark: 警徽已撕毀").setEphemeral(false).queue()
                session.callback?.invoke()
            } else {
                event.reply(":x: 你不是原本的警長").setEphemeral(true).queue()
            }
        }
    }
}
