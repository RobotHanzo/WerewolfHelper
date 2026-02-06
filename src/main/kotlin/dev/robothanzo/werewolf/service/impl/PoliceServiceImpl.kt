package dev.robothanzo.werewolf.service.impl

import dev.robothanzo.werewolf.audio.Audio
import dev.robothanzo.werewolf.audio.Audio.play
import dev.robothanzo.werewolf.database.SessionRepository
import dev.robothanzo.werewolf.database.documents.LogType
import dev.robothanzo.werewolf.database.documents.Player
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.model.Candidate
import dev.robothanzo.werewolf.model.PoliceSession
import dev.robothanzo.werewolf.service.GameSessionService
import dev.robothanzo.werewolf.service.PoliceService
import dev.robothanzo.werewolf.service.SpeechService
import dev.robothanzo.werewolf.service.TransferPoliceSession
import dev.robothanzo.werewolf.utils.CmdUtils
import dev.robothanzo.werewolf.utils.MsgUtils
import dev.robothanzo.werewolf.utils.player
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
        val guild = event.guild ?: return
        val session = CmdUtils.getSession(guild) ?: return

        val policeSession = sessions[guild.idLong]
        if (policeSession == null) {
            event.hook.editOriginal(":x: 無法參選，時間已到").queue()
            return
        }

        val iterator = policeSession.candidates.entries.iterator()
        while (iterator.hasNext()) {
            val candidate = iterator.next()
            if (event.user.idLong == candidate.value.player.user?.idLong) {
                if (policeSession.state.canEnroll()) { // ENROLLMENT -> Remove completely
                    iterator.remove()
                    event.hook.editOriginal(":white_check_mark: 已取消參選").queue()

                    val metadata: MutableMap<String, Any> = HashMap()
                    metadata["playerId"] = candidate.value.player.id
                    metadata["playerName"] = candidate.value.player.nickname
                    session.addLog(
                        LogType.POLICE_UNENROLLED,
                        buildString {
                            append(candidate.value.player.nickname)
                            append(" 已取消參選警長")
                        }, metadata
                    )

                } else if (policeSession.state.canQuit()) { // UNENROLLMENT -> Mark quit
                    candidate.value.quit = true
                    event.hook.editOriginal(":white_check_mark: 已取消參選").queue()
                    // Use session helper to announce cancel in court
                    session.courtTextChannel?.sendMessage(event.user.asMention + " 已取消參選")?.queue()

                    val metadata = mutableMapOf<String, Any>()
                    metadata["playerId"] = candidate.value.player.id
                    metadata["playerName"] = candidate.value.player.nickname
                    session.addLog(
                        LogType.POLICE_UNENROLLED,
                        "${candidate.value.player.nickname} 已取消參選警長", metadata
                    )
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

        val player = event.member?.player()
        if (player != null && player.alive) {
            policeSession.candidates[player.id] = Candidate(player = player)
            event.hook.editOriginal(":white_check_mark: 已參選").queue()

            val metadata = mutableMapOf<String, Any>()
            metadata["playerId"] = player.id
            metadata["playerName"] = player.nickname
            session.addLog(
                LogType.POLICE_ENROLLED,
                "${player.nickname} 已參選警長", metadata
            )

            return
        }
        event.hook.editOriginal(":x: 你不是玩家").queue()
    }

    override fun next(guildId: Long) {
        val policeSession = sessions[guildId] ?: return

        gameSessionService.withLockedSession(guildId) { session ->
            val guild = session.guild
            if (guild == null) {
                interrupt(guildId)
                return@withLockedSession
            }
            val channel = guild.getTextChannelById(policeSession.channelId)
            if (channel == null) {
                interrupt(guildId)
                return@withLockedSession
            }

            when (policeSession.state) {
                PoliceSession.State.NONE -> {
                    policeSession.state = PoliceSession.State.ENROLLMENT
                    policeSession.stageEndTime = System.currentTimeMillis() + 30000
                    policeSession.candidates.clear()

                    session.addLog(
                        LogType.POLICE_ENROLLMENT_STARTED,
                        "警長參選已開始", null
                    )

                    val vc = session.courtVoiceChannel
                    vc?.play(Audio.Resource.POLICE_ENROLL)
                    val embed = EmbedBuilder()
                        .setTitle("參選警長").setDescription("30秒後立刻進入辯論，請加快手速!")
                        .setColor(MsgUtils.randomColor)

                    channel.sendMessageEmbeds(embed.build())
                        .setComponents(ActionRow.of(Button.success("enrollPolice", "參選警長")))
                        .queue { msg -> policeSession.message = msg }

                    CmdUtils.schedule({
                        val vc10s = session.courtVoiceChannel
                        vc10s?.play(Audio.Resource.ENROLL_10S_REMAINING)
                    }, 20000)
                    CmdUtils.schedule({ next(guildId) }, 30000)
                }

                PoliceSession.State.ENROLLMENT -> {
                    if (policeSession.candidates.isEmpty()) {
                        policeSession.message?.reply("無人參選，警徽撕毀")?.queue()
                        interrupt(guildId)
                        return@withLockedSession
                    }

                    val candidateMentions = policeSession.candidates.values.asSequence()
                        .sortedWith(Candidate.getComparator())
                        .map { "<@!${it.player.user?.idLong}>" }
                        .toMutableList()

                    if (policeSession.candidates.size == 1) {
                        val winner = policeSession.candidates.values.iterator().next()
                        policeSession.message?.reply("只有" + candidateMentions.first() + "參選，直接當選")?.queue()
                        setPolice(guildId, winner.player.id)
                        interrupt(guildId)
                        return@withLockedSession
                    }

                    if (policeSession.message == null) {
                        interrupt(guildId)
                        return@withLockedSession
                    }

                    policeSession.message?.replyEmbeds(
                        EmbedBuilder().setTitle("參選警長結束")
                            .setDescription(
                                "參選的有: ${candidateMentions.joinToString("、")}\n備註:你可隨時再按一次按鈕以取消參選"
                            )
                            .setColor(MsgUtils.randomColor).build()
                    )?.queue()

                    policeSession.state = PoliceSession.State.SPEECH

                    // Start speech
                    speechService.startSpeechPoll(
                        guild, policeSession.message!!,
                        policeSession.candidates.values.map { it.player }
                    ) { next(guildId) }
                }

                PoliceSession.State.SPEECH -> {
                    policeSession.state = PoliceSession.State.UNENROLLMENT
                    policeSession.stageEndTime = System.currentTimeMillis() + 20000

                    // Use session helper to announce end of speeches
                    session.courtTextChannel?.sendMessage("政見發表結束，參選人查20秒進行退選，20秒不会自動開始投票")
                        ?.queue()

                    CmdUtils.schedule({ next(guildId) }, 20000)
                }

                PoliceSession.State.UNENROLLMENT -> {
                    policeSession.state = PoliceSession.State.VOTING
                    policeSession.stageEndTime = System.currentTimeMillis() + 30000

                    if (policeSession.candidates.values.stream().allMatch { it.quit }) {
                        policeSession.message?.reply("所有人退選，警徽撕毀")?.queue()
                        interrupt(guildId)
                        return@withLockedSession
                    }

                    gameSessionService.broadcastSessionUpdate(session)
                    startVoting(channel, true, policeSession)
                }

                else -> {
                    // Logic handled in startVoting callback
                }
            }
        }
    }

    override fun interrupt(guildId: Long) {
        val policeSession = sessions.remove(guildId)
        if (policeSession != null) {
            cancelPollTasks(policeSession)
            policeSession.session.courtTextChannel?.sendMessage("警長投票已終止")?.queue()
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
        cancelPollTasks(policeSession)
        val vc = policeSession.session.courtVoiceChannel
        vc?.play(Audio.Resource.POLICE_POLL)
        val embedBuilder = EmbedBuilder().setTitle("警長投票")
            .setDescription("30秒後立刻計票，請加快手速!\n若要改票可直接按下要改成的對象\n若要改為棄票需按下原本投給的使用者")
            .setColor(MsgUtils.randomColor)

        val buttons = mutableListOf<Button>()
        policeSession.candidates.values.asSequence()
            .sortedWith(Candidate.getComparator())
            .filter { !it.quit }
            .forEach { player ->
                player.player.user?.idLong?.let { uid ->
                    val user = channel.guild.getMemberById(uid)
                    if (user != null) {
                        buttons.add(
                            Button.primary(
                                "votePolice${player.player.id}",
                                "${player.player.nickname} (${user.user.name})"
                            )
                        )
                    }
                }
            }

        policeSession.session.courtTextChannel?.sendMessageEmbeds(embedBuilder.build())
            ?.setComponents(*MsgUtils.spreadButtonsAcrossActionRows(buttons).toTypedArray())
            ?.queue()

        policeSession.poll10sTask = CmdUtils.schedule({
            val vc = policeSession.session.courtVoiceChannel
            vc?.play(Audio.Resource.POLL_10S_REMAINING)
        }, 20000)
        policeSession.pollFinishTask = CmdUtils.schedule({
            finishVoting(channel, allowPK, policeSession)
        }, 30000)
    }

    private fun finishVoting(channel: GuildMessageChannel, allowPK: Boolean, policeSession: PoliceSession) {
        val guildId = policeSession.guildId
        gameSessionService.withLockedSession(guildId) { session ->
            if (sessions[guildId] !== policeSession) {
                return@withLockedSession
            }
            if (policeSession.state != PoliceSession.State.VOTING) {
                return@withLockedSession
            }
            val winners = policeSession.getWinners(null)
            if (winners.isEmpty()) {
                policeSession.message?.reply("沒有人投票，警徽撕毀")?.queue()
                interrupt(guildId)
                return@withLockedSession
            }

            val message = policeSession.message ?: return@withLockedSession

            if (winners.size == 1) {
                val winner = winners.first()
                message.reply("投票已結束，<@!" + winner.player.user?.idLong + "> 獲勝").queue()

                val resultEmbed = policeSession.buildResultEmbed("警長投票").apply {
                    setDescription("獲勝玩家: <@!" + winner.player.user?.idLong + ">")
                }

                policeSession.sendVoteResult(channel, message, resultEmbed, session)

                setPolice(guildId, winner.player.id)
                interrupt(guildId)
            } else {
                if (allowPK) {
                    val resultEmbed = policeSession.buildResultEmbed("警長投票").apply {
                        setDescription("發生平票")
                    }
                    policeSession.sendVoteResult(channel, message, resultEmbed, session)

                    handlePK(channel, winners, policeSession)
                } else {
                    val resultEmbed = EmbedBuilder().setTitle("警長投票")
                        .setColor(MsgUtils.randomColor)
                        .setDescription("平票第二次，警徽撕毀")
                    message.reply("平票第二次，警徽撕毀").queue()
                    policeSession.sendVoteResult(channel, message, resultEmbed, session)
                    interrupt(guildId)
                }
            }
        }
    }

    private fun handlePK(channel: GuildMessageChannel, winners: List<Candidate>, policeSession: PoliceSession) {
        policeSession.message?.reply("平票，請PK")?.queue()

        // Clear votes and reset candidates to only winners
        val newCandidates: MutableMap<Int, Candidate> = ConcurrentHashMap()
        for (winner in winners) {
            winner.electors.clear()
            newCandidates[winner.player.id] = winner
        }
        policeSession.candidates.clear()
        policeSession.candidates.putAll(newCandidates)

        val msg = policeSession.message ?: return
        speechService.startSpeechPoll(
            channel.guild, msg,
            newCandidates.values.map { it.player }
        ) {
            policeSession.state = PoliceSession.State.VOTING
            policeSession.stageEndTime = System.currentTimeMillis() + 30000
            gameSessionService.broadcastSessionUpdate(policeSession.session)
            startVoting(channel, false, policeSession)
        }
    }

    private fun cancelPollTasks(policeSession: PoliceSession) {
        policeSession.poll10sTask?.cancel()
        policeSession.poll10sTask = null
        policeSession.pollFinishTask?.cancel()
        policeSession.pollFinishTask = null
    }

    private fun setPolice(guildId: Long, playerId: Int) {
        gameSessionService.withLockedSession(guildId) { session ->
            val player = session.getPlayer(playerId) ?: return@withLockedSession
            player.police = true
            player.updateNickname()

            val metadata = mutableMapOf<String, Any>()
            metadata["playerId"] = player.id
            metadata["playerName"] = player.nickname
            session.addLog(
                LogType.POLICE_ELECTED,
                "${player.nickname} 當選警長", metadata
            )

            gameSessionService.broadcastSessionUpdate(session)
        }
    }


    // Transfer Police Implementation
    private val transferSessions: MutableMap<Long, TransferPoliceSession> = ConcurrentHashMap()

    override fun transferPolice(
        session: Session,
        guild: Guild?,
        player: Player,
        callback: (() -> Unit)?
    ) {
        if (guild == null) {
            callback?.invoke()
            return
        }
        if (player.police) {
            val senderPid = player.id
            val transferSession = TransferPoliceSession(
                guildId = guild.idLong,
                senderId = senderPid,
                callback = callback
            )
            transferSessions[guild.idLong] = transferSession

            val selectMenu = EntitySelectMenu
                .create("selectNewPolice", EntitySelectMenu.SelectTarget.USER)
                .setMinValues(1)
                .setMaxValues(1)

            for (p in session.alivePlayers().values) {
                if (p.id == player.id) continue
                transferSession.possibleRecipientIds.add(p.id)
            }

            val message = session.courtTextChannel?.sendMessageEmbeds(
                EmbedBuilder()
                    .setTitle("移交警徽").setColor(MsgUtils.randomColor)
                    .setDescription("請選擇要移交警徽的對象，若要撕掉警徽，請按下撕毀按鈕\n請在30秒內做出選擇，否則警徽將被自動撕毀")
                    .build()
            )?.setComponents(
                    ActionRow.of(selectMenu.build()),
                    ActionRow.of(
                        Button.success("confirmNewPolice", "移交"),
                        Button.danger("destroyPolice", "撕毀")
                    )
                )
                ?.complete()
            if (message == null) return

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
        val guild = event.guild ?: return
        val session = transferSessions[guild.idLong] ?: return
        val targetMember = event.mentions.members.first()
        val guildSession = sessionRepository.findByGuildId(guild.idLong).orElse(null) ?: return
        val targetPlayer = guildSession.getPlayer(targetMember.idLong)

        if (targetPlayer == null || !session.possibleRecipientIds.contains(targetPlayer.id)) {
            event.reply(":x: 你不能移交警徽給這個人").setEphemeral(true).queue()
        } else {
            val senderPlayer = guildSession.getPlayer(session.senderId)
            if (senderPlayer?.user?.idLong == event.user.idLong) {
                session.recipientId = targetPlayer.id
                event.reply(":white_check_mark: 請按下移交來完成移交動作").setEphemeral(true).queue()
            } else {
                event.reply(":x: 你不是原本的警長").setEphemeral(true).queue()
            }
        }
    }

    override fun confirmNewPolice(event: ButtonInteractionEvent) {
        val guild = event.guild ?: return
        if (transferSessions.containsKey(guild.idLong)) {
            val session = transferSessions[guild.idLong] ?: return
            val guildSession = sessionRepository.findByGuildId(guild.idLong).orElse(null) ?: return
            val senderPlayer = guildSession.getPlayer(session.senderId)

            if (senderPlayer?.user?.idLong == event.user.idLong) {
                if (session.recipientId != null) {
                    val recipientPlayer = guildSession.getPlayer(session.recipientId!!)
                    if (recipientPlayer != null) {
                        recipientPlayer.police = true
                        recipientPlayer.updateNickname()
                        event.reply(":white_check_mark: 警徽已移交給 ${recipientPlayer.user?.asMention ?: recipientPlayer.nickname}")
                            .queue()
                    }

                    // Update Sender
                    senderPlayer.police = false
                    senderPlayer.updateNickname()

                    gameSessionService.saveSession(guildSession)
                    log.info("Transferred police to {} in guild {}", session.recipientId, guild.idLong)
                    transferSessions.remove(guild.idLong)

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
        val guild = event.guild ?: return
        if (transferSessions.containsKey(guild.idLong)) {
            val session = transferSessions[guild.idLong] ?: return
            val guildSession = sessionRepository.findByGuildId(guild.idLong).orElse(null) ?: return
            val senderPlayer = guildSession.getPlayer(session.senderId)

            if (senderPlayer?.user?.idLong == event.user.idLong) {
                transferSessions.remove(guild.idLong)
                event.reply(":white_check_mark: 警徽已撕毀").setEphemeral(false).queue()
                session.callback?.invoke()
            } else {
                event.reply(":x: 你不是原本的警長").setEphemeral(true).queue()
            }
        }
    }
}
