package dev.robothanzo.werewolf.service.impl

import dev.robothanzo.werewolf.audio.Audio
import dev.robothanzo.werewolf.audio.Audio.play
import dev.robothanzo.werewolf.database.SessionRepository
import dev.robothanzo.werewolf.database.documents.Player
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.model.SpeechOrder
import dev.robothanzo.werewolf.model.SpeechSession
import dev.robothanzo.werewolf.service.DiscordService
import dev.robothanzo.werewolf.service.GameSessionService
import dev.robothanzo.werewolf.service.SpeechService
import dev.robothanzo.werewolf.utils.MsgUtils
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.components.selections.StringSelectMenu
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent
import net.dv8tion.jda.api.utils.TimeFormat
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

@Service
class SpeechServiceImpl(
    private val sessionRepository: SessionRepository,
    private val discordService: DiscordService,
    @param:Lazy private val gameSessionService: GameSessionService
) : SpeechService {
    private val speechSessions: MutableMap<Long, SpeechSession> = ConcurrentHashMap()
    private val timers: MutableMap<Long, Thread> = ConcurrentHashMap()

    override fun getSpeechSession(guildId: Long): SpeechSession? {
        return speechSessions[guildId]
    }

    override fun startSpeechPoll(
        guild: Guild, enrollMessage: Message?, players: Collection<Player>,
        callback: (() -> Unit)?
    ) {
        val speechSession = SpeechSession(
            guildId = guild.idLong,
            channelId = enrollMessage?.channel?.idLong ?: 0L,
            session = sessionRepository.findByGuildId(guild.idLong).orElse(null),
            finishedCallback = callback
        )
        speechSessions[guild.idLong] = speechSession

        val order = SpeechOrder.getRandomOrder()
        val target = players.shuffled().first()

        enrollMessage?.replyEmbeds(
            EmbedBuilder()
                .setTitle("隨機抽取投票辯論順序")
                .setDescription("抽到的順序: 玩家" + target.id + order.toString())
                .setColor(MsgUtils.randomColor).build()
        )?.queue()

        changeOrder(guild.idLong, order, players, target)
        nextSpeaker(guild.idLong)
    }

    override fun startLastWordsSpeech(
        guild: Guild,
        channelId: Long,
        player: Player,
        callback: (() -> Unit)?
    ) {
        val orderList = mutableListOf(player)

        val speechSession = SpeechSession(
            guildId = guild.idLong,
            channelId = channelId,
            session = sessionRepository.findByGuildId(guild.idLong).orElse(null),
            order = orderList,
            finishedCallback = callback
        )
        speechSessions[guild.idLong] = speechSession
        nextSpeaker(guild.idLong)
    }

    override fun setSpeechOrder(session: Session, order: SpeechOrder) {
        val guildId = session.guildId
        var target: Player? = null
        for (player in session.fetchAlivePlayers().values) {
            if (player.police) {
                target = player
                break
            }
        }
        if (target == null && !session.fetchAlivePlayers().isEmpty()) {
            target = session.fetchAlivePlayers().values.iterator().next()
        }

        if (target != null) {
            changeOrder(guildId, order, session.fetchAlivePlayers().values, target)
        }
    }

    override fun confirmSpeechOrder(session: Session) {
        nextSpeaker(session.guildId)
    }

    override fun handleOrderSelection(event: StringSelectInteractionEvent) {
        event.deferReply(true).queue()
        val guild = event.guild ?: return
        val guildId = guild.idLong
        val session = sessionRepository.findByGuildId(guildId).orElse(null) ?: return

        val order = SpeechOrder.fromString(event.selectedOptions.first().value)
        if (!speechSessions.containsKey(guildId)) {
            event.hook.editOriginal("法官尚未開始發言流程").queue()
            return
        }

        var target: Player? = null
        for (player in session.fetchAlivePlayers().values) {
            if (player.userId != null && player.userId == event.user.idLong) {
                if (player.police) {
                    target = player
                    break
                } else {
                    event.hook.editOriginal(":x: 你不是警長").queue()
                    return
                }
            }
        }

        if (target == null) {
            event.hook.editOriginal(":x: 你不是警長").queue()
            return
        }

        changeOrder(guildId, order, session.fetchAlivePlayers().values, target)
        event.hook.editOriginal(":white_check_mark: 請按下確認以開始發言流程").queue()
        event.message.editMessageEmbeds(
            EmbedBuilder(event.message.embeds.first())
                .setDescription("警長已選擇 " + order.toEmoji().name + " " + order + "\n請按下確認").build()
        )
            .queue()
        gameSessionService.broadcastUpdate(guildId)
    }

    override fun confirmOrder(event: ButtonInteractionEvent) {
        event.deferReply(true).queue()
        val guild = event.guild ?: return
        val guildId = guild.idLong
        val session = sessionRepository.findByGuildId(guildId).orElse(null) ?: return

        val speechSession = speechSessions[guildId]
        if (speechSession == null) {
            event.hook.editOriginal(":x: 法官尚未開始發言流程").queue()
            return
        }

        var isPolice = false
        for (player in session.fetchAlivePlayers().values) {
            if (player.userId != null && player.userId == event.user.idLong) {
                if (player.police) {
                    isPolice = true
                    break
                }
            }
        }

        if (!isPolice) {
            event.hook.editOriginal(":x: 你不是警長").queue()
        } else {
            if (speechSession.order.isEmpty()) {
                event.hook.editOriginal(":x: 請先選取往上或往下").queue()
            } else {
                nextSpeaker(guildId)
                event.hook.editOriginal(":white_check_mark: 確認完成").queue()
            }
        }
    }

    override fun skipSpeech(event: ButtonInteractionEvent) {
        event.deferReply().queue()
        val guild = event.guild ?: return
        val guildId = guild.idLong
        val speechSession = speechSessions[guildId]

        if (speechSession != null) {
            if (speechSession.lastSpeaker != null
                && event.user.idLong != speechSession.lastSpeaker
            ) {
                event.hook.setEphemeral(true).editOriginal(":x: 你不是發言者").queue()
            } else {
                event.hook.editOriginal(":white_check_mark: 發言已跳過").queue()
                nextSpeaker(guildId)
            }
        } else {
            event.hook.setEphemeral(true).editOriginal(":x: 法官尚未開始發言流程").queue()
        }
    }

    override fun interruptSpeech(event: ButtonInteractionEvent) {
        event.deferReply(true).queue()
        val guild = event.guild ?: return
        val guildId = guild.idLong
        val speechSession = speechSessions[guildId]

        if (speechSession != null) {
            val member = event.member ?: return
            if (speechSession.lastSpeaker != null && !member.hasPermission(Permission.ADMINISTRATOR)) {
                if (event.user.idLong == speechSession.lastSpeaker) {
                    event.hook.editOriginal(":x: 若要跳過發言請按左邊的跳過按鈕").queue()
                } else {
                    val session = speechSession.session
                    val spectatorRole = guild.getRoleById(session.spectatorRoleId)
                    if (spectatorRole != null && member.roles.contains(spectatorRole)) {
                        event.hook.editOriginal(":x: 旁觀者不得投票").queue()
                    } else {
                        if (speechSession.interruptVotes.contains(event.user.idLong)) {
                            speechSession.interruptVotes.remove(event.user.idLong)
                            event.hook.editOriginal(
                                ":white_check_mark: 成功取消下台投票，距離該玩家下台還缺" +
                                        (session.fetchAlivePlayers().size / 2 + 1
                                                - speechSession.interruptVotes.size)
                                        + "票"
                            ).queue()
                        } else {
                            speechSession.interruptVotes.add(event.user.idLong)
                            event.hook.editOriginal(
                                ":white_check_mark: 下台投票成功，距離該玩家下台還缺" +
                                        (session.fetchAlivePlayers().size / 2 + 1
                                                - speechSession.interruptVotes.size)
                                        + "票"
                            ).queue()

                            gameSessionService.broadcastSessionUpdate(session)
                            if (speechSession.interruptVotes.size > (session.fetchAlivePlayers().size / 2)) {
                                val voterMentions = speechSession.interruptVotes.map { "<@!$it>" }
                                event.message.reply(
                                    "人民的法槌已強制該玩家下台，有投票的有: ${voterMentions.joinToString("、")}"
                                )
                                    .queue()
                                nextSpeaker(guildId)
                            }
                        }
                    }
                }
            } else {
                event.hook.editOriginal(":white_check_mark: 成功強制下台").queue()
                event.message.reply("法官已強制該玩家下台").queue()
                nextSpeaker(guildId)
            }
        } else {
            event.hook.editOriginal(":x: 法官尚未開始發言流程").queue()
        }
    }

    override fun startAutoSpeechFlow(session: Session, channelId: Long) {
        val guildId = session.guildId
        if (speechSessions.containsKey(guildId))
            return

        val speechSession = SpeechSession(
            guildId = guildId,
            channelId = channelId,
            session = session
        )
        speechSessions[guildId] = speechSession

        val guild = discordService.getGuild(guildId) ?: return
        val channel = guild.getTextChannelById(channelId)

        for (player in session.fetchAlivePlayers().values) {
            if (player.userId != null) {
                try {
                    if (session.muteAfterSpeech) {
                        player.userId?.let { uid ->
                            val member = guild.getMemberById(uid)
                            member?.let { guild.mute(it, true).queue() }
                        }
                    }
                } catch (_: Exception) {
                }
            }
            if (player.police) {
                session.sendToCourt(
                    EmbedBuilder()
                        .setTitle("警長請選擇發言順序")
                        .setDescription("警長尚未選擇順序")
                        .setColor(MsgUtils.randomColor).build(),
                    queue = false
                )?.setComponents(
                    ActionRow.of(
                        StringSelectMenu.create("selectOrder")
                            .addOption(SpeechOrder.UP.toString(), "up", SpeechOrder.UP.toEmoji())
                            .addOption(SpeechOrder.DOWN.toString(), "down", SpeechOrder.DOWN.toEmoji())
                            .setPlaceholder("請警長按此選擇發言順序").build()
                    ),
                    ActionRow.of(Button.success("confirmOrder", "確認選取"))
                )?.queue()
                gameSessionService.broadcastUpdate(guildId)
                return
            }
        }

        // No police found, auto random
        val shuffled = session.fetchAlivePlayers().values.shuffled()
        val randOrder = SpeechOrder.getRandomOrder()
        changeOrder(guildId, randOrder, session.fetchAlivePlayers().values, shuffled.first())
        session.sendToCourt(
            EmbedBuilder()
                .setTitle("找不到警長，自動抽籤發言順序")
                .setDescription("抽到的順序: 玩家${shuffled.first().id}$randOrder")
                .setColor(MsgUtils.randomColor).build()
        )

        for (c in guild.textChannels) {
            c.sendMessage("⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯我是白天分隔線⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯").queue()
        }

        nextSpeaker(guildId)
    }

    override fun startTimer(guildId: Long, channelId: Long, voiceChannelId: Long, seconds: Int) {
        val guild = discordService.getGuild(guildId) ?: return
        val textChannel = guild.getTextChannelById(channelId) ?: return
        val voiceChannel = guild.getVoiceChannelById(voiceChannelId)

        val t = thread(start = true) {
            val message = textChannel
                .sendMessage(
                    seconds.toString() + "秒的計時開始，" + TimeFormat.TIME_LONG.after(
                        Duration.ofSeconds(
                            seconds.toLong()
                        )
                    ) + "後結束"
                )
                .setComponents(ActionRow.of(Button.danger("terminateTimer", "強制結束計時"))).complete()
            try {
                if (seconds > 30) {
                    Thread.sleep((seconds - 30) * 1000L)
                    voiceChannel?.play(Audio.Resource.TIMER_30S_REMAINING)
                    Thread.sleep(30000)
                } else {
                    Thread.sleep(seconds * 1000L)
                }
                voiceChannel?.play(Audio.Resource.TIMER_ENDED)
                message.editMessage(message.contentRaw + " (已結束)").queue()
                message.reply("計時結束").queue()
            } catch (_: InterruptedException) {
                message.reply("計時被終止").queue()
            }
        }
        timers[channelId] = t
    }

    override fun stopTimer(channelId: Long) {
        if (timers.containsKey(channelId)) {
            timers[channelId]?.interrupt()
            timers.remove(channelId)
        } else {
            throw RuntimeException("Timer not found")
        }
    }

    override fun interruptSession(guildId: Long) {
        val speechSession = speechSessions[guildId]
        if (speechSession != null) {
            val guild = discordService.getGuild(guildId)
            if (guild != null) {
                speechSession.session.sendToCourt("法官已強制終止發言流程")
            }

            speechSession.order.clear()
            stopCurrentSpeaker(speechSession)
            speechSessions.remove(guildId)
        }
    }

    override fun skipToNext(guildId: Long) {
        val speechSession = speechSessions[guildId]
        if (speechSession != null) {
            val guild = discordService.getGuild(guildId)
            if (guild != null) {
                speechSession.session.sendToCourt("法官已強制該玩家下台")
            }
            nextSpeaker(guildId)
        }
    }

    override fun setAllMute(guildId: Long, mute: Boolean) {
        val guild = discordService.getGuild(guildId) ?: return
        for (member in guild.members) {
            if (member.hasPermission(Permission.ADMINISTRATOR))
                continue
            try {
                guild.mute(member, mute).queue()
            } catch (_: Exception) {
            }
        }
    }

    private fun changeOrder(
        guildId: Long, order: SpeechOrder, playersRaw: Collection<Player>,
        target: Player
    ) {
        val speechSession = speechSessions[guildId] ?: return

        val sortedPlayers = playersRaw.sortedBy { it.id }
        var police: Player? = null
        val prePolice = mutableListOf<Player>()
        val postPolice = mutableListOf<Player>()

        for (player in sortedPlayers) {
            if (player.id == target.id) {
                police = player
                continue
            }
            if (police == null)
                prePolice.add(player)
            else
                postPolice.add(player)
        }

        val orderList = if (order == SpeechOrder.UP) {
            prePolice.reversed() + postPolice.reversed()
        } else {
            postPolice + prePolice
        }.toMutableList().apply {
            police?.let { add(it) }
        }

        speechSession.order.clear()
        speechSession.order.addAll(orderList)
        gameSessionService.broadcastUpdate(guildId)
    }

    private fun nextSpeaker(guildId: Long) {
        val speechSession = speechSessions[guildId] ?: return

        speechSession.interruptVotes.clear()
        stopCurrentSpeaker(speechSession)

        // Clear interrupt flag from thread interruption to prevent WebSocket errors
        Thread.interrupted()

        // Reset the stop flag for the new speaker's timer
        speechSession.shouldStopCurrentSpeaker = false

        val guild = discordService.getGuild(guildId) ?: return
        val session = speechSession.session

        speechSession.lastSpeaker?.let { lastSpeakerId ->
            val member = guild.getMemberById(lastSpeakerId)
            if (member != null) {
                try {
                    if (session.muteAfterSpeech)
                        guild.mute(member, true).queue()
                } catch (_: Exception) {
                }
            }
        }

        if (speechSession.order.isEmpty()) {
            session.sendToCourt("發言流程結束")

            speechSessions.remove(guildId)
            gameSessionService.broadcastSessionUpdate(session)
            speechSession.finishedCallback?.invoke()
            return
        }

        val player = speechSession.order.removeFirst()
        speechSession.lastSpeaker = player.userId
        val time = getSpeakerDuration(player.police)
        speechSession.totalSpeechTime = time
        speechSession.currentSpeechEndTime = System.currentTimeMillis() + (time * 1000L)

        // Update session timer to match TOTAL remaining time
        val totalDuration = time + getTotalQueueDuration(speechSession.order)
        session.currentStepEndTime = System.currentTimeMillis() + (totalDuration * 1000L)

        try {
            sessionRepository.save(session)
        } catch (e: Exception) {
            println("Warning: Failed to save session during nextSpeaker: ${e.message}")
            // Continue anyway - clients already have the updated timer
        }

        val t = thread(start = true) {
            try {
                player.userId?.let { userId ->
                    val member = guild.getMemberById(userId)
                    member?.let { guild.mute(it, false).queue() }
                }
            } catch (_: Exception) {
            }

            val message = session.sendToCourt(
                "<@!" + player.userId + "> 你有" + time + "秒可以發言\n",
                queue = false
            )
                ?.setComponents(
                    ActionRow.of(
                        Button.danger("skipSpeech", "跳過 (發言者按)").withEmoji(Emoji.fromUnicode("U+23ed")),
                        Button.danger("interruptSpeech", "下台 (玩家或法官按)").withEmoji(Emoji.fromUnicode("U+1f5d1"))
                    )
                )
                ?.complete() ?: return@thread

            val voiceChannel = guild.getVoiceChannelById(session.courtVoiceChannelId)
            try {
                Thread.sleep((time - 30) * 1000L)
                if (!speechSession.shouldStopCurrentSpeaker) {
                    voiceChannel?.play(Audio.Resource.TIMER_30S_REMAINING)
                    Thread.sleep(32000) // Extra 2 seconds to account for delays
                }
                if (!speechSession.shouldStopCurrentSpeaker) {
                    voiceChannel?.play(Audio.Resource.TIMER_ENDED)
                    message.reply("計時結束").queue()
                    nextSpeaker(guildId)
                }
            } catch (_: InterruptedException) {
                if (!speechSession.shouldStopCurrentSpeaker) {
                    voiceChannel?.play(Audio.Resource.TIMER_ENDED)
                }
            } catch (ignored: Exception) {
                if (!speechSession.shouldStopCurrentSpeaker) {
                    ignored.printStackTrace()
                    voiceChannel?.play(Audio.Resource.TIMER_ENDED)
                    message.reply("發言中斷（可能發言者離開或發生錯誤）").queue()
                    nextSpeaker(guildId)
                }
            }
        }
        speechSession.speakingThread = t
    }

    private fun stopCurrentSpeaker(session: SpeechSession) {
        session.shouldStopCurrentSpeaker = true
        session.speakingThread?.interrupt()
        session.speakingThread = null
    }
    private fun getSpeakerDuration(isPolice: Boolean): Int {
        return if (isPolice) 210 else 180
    }

    private fun getTotalQueueDuration(queue: List<Player>): Int {
        var duration = 0
        for (p in queue) {
            duration += getSpeakerDuration(p.police)
        }
        return duration
    }
}
