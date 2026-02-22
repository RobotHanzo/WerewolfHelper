package dev.robothanzo.werewolf.service.impl

import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.audio.Audio
import dev.robothanzo.werewolf.audio.Audio.play
import dev.robothanzo.werewolf.database.documents.Player
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.model.SpeechOrder
import dev.robothanzo.werewolf.model.SpeechSession
import dev.robothanzo.werewolf.service.GameSessionService
import dev.robothanzo.werewolf.service.SpeechService
import dev.robothanzo.werewolf.utils.MsgUtils
import dev.robothanzo.werewolf.utils.isAdmin
import dev.robothanzo.werewolf.utils.isSpectator
import dev.robothanzo.werewolf.utils.player
import net.dv8tion.jda.api.EmbedBuilder
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
import kotlin.jvm.optionals.getOrNull

@Service
class SpeechServiceImpl(
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
        val guildId = guild.idLong
        gameSessionService.withLockedSession(guildId) { session ->
            val speechSession = SpeechSession(
                guildId = guildId,
                channelId = enrollMessage?.channel?.idLong ?: 0L,
                session = session,
                finishedCallback = callback
            )
            speechSessions[guildId] = speechSession

            val order = SpeechOrder.getRandomOrder()
            val target = players.shuffled().first()

            enrollMessage?.replyEmbeds(
                EmbedBuilder()
                    .setTitle("隨機抽取投票辯論順序")
                    .setDescription("抽到的順序: 玩家" + target.id + order.toString())
                    .setColor(MsgUtils.randomColor).build()
            )?.queue()

            changeOrder(guildId, order, players, target)
            nextSpeaker(guildId)
        }
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
            session = gameSessionService.getSession(guild.idLong).orElse(null),
            order = orderList,
            finishedCallback = callback
        )
        speechSessions[guild.idLong] = speechSession
        nextSpeaker(guild.idLong)
    }

    override fun setSpeechOrder(session: Session, order: SpeechOrder) {
        val guildId = session.guildId
        var target: Player? = null
        for (player in session.alivePlayers().values) {
            if (player.police) {
                target = player
                break
            }
        }
        if (target == null && !session.alivePlayers().isEmpty()) {
            target = session.alivePlayers().values.iterator().next()
        }

        if (target != null) {
            changeOrder(guildId, order, session.alivePlayers().values, target)
        }
    }

    override fun confirmSpeechOrder(session: Session) {
        nextSpeaker(session.guildId)
    }

    override fun handleOrderSelection(event: StringSelectInteractionEvent) {
        event.deferReply(true).queue()
        val guild = event.guild ?: return
        val guildId = guild.idLong
        val session = gameSessionService.getSession(guildId).orElse(null) ?: return

        val order = SpeechOrder.fromString(event.selectedOptions.first().value)
        if (!speechSessions.containsKey(guildId)) {
            event.hook.editOriginal("法官尚未開始發言流程").queue()
            return
        }

        val target = event.member?.player()
        if (target?.police != true) {
            event.hook.editOriginal(":x: 你不是警長").queue()
            return
        }

        changeOrder(guildId, order, session.alivePlayers().values, target)
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

        val speechSession = speechSessions[guildId]
        if (speechSession == null) {
            event.hook.editOriginal(":x: 法官尚未開始發言流程").queue()
            return
        }

        val player = event.member?.player()
        val isPolice = player?.police == true

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
            val currentPlayer = gameSessionService.getSession(guildId).getOrNull()?.getPlayer(event.user.idLong)
            if (speechSession.lastSpeaker != null
                && currentPlayer?.id != speechSession.lastSpeaker
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
            val session = speechSession.session
            val player = session.getPlayer(event.user.idLong)

            if (speechSession.lastSpeaker != null && !member.isAdmin()) {
                if (player?.id == speechSession.lastSpeaker) {
                    event.hook.editOriginal(":x: 若要跳過發言請按左邊的跳過按鈕").queue()
                } else {
                    if (member.isSpectator()) {
                        event.hook.editOriginal(":x: 旁觀者不得投票").queue()
                    } else if (player != null) {
                        if (speechSession.interruptVotes.contains(player.id)) {
                            speechSession.interruptVotes.remove(player.id)
                            event.hook.editOriginal(
                                ":white_check_mark: 成功取消下台投票，距離該玩家下台還缺" +
                                        (session.alivePlayers().size / 2 + 1
                                                - speechSession.interruptVotes.size)
                                        + "票"
                            ).queue()
                        } else {
                            speechSession.interruptVotes.add(player.id)
                            event.hook.editOriginal(
                                ":white_check_mark: 下台投票成功，距離該玩家下台還缺" +
                                        (session.alivePlayers().size / 2 + 1
                                                - speechSession.interruptVotes.size)
                                        + "票"
                            ).queue()

                            gameSessionService.broadcastSessionUpdate(session)
                            if (speechSession.interruptVotes.size > (session.alivePlayers().size / 2)) {
                                val voterMentions = speechSession.interruptVotes.map { pid ->
                                    val voter = session.getPlayer(pid)
                                    voter?.user?.asMention ?: voter?.nickname ?: "玩家 $pid"
                                }
                                event.message.reply(
                                    "人民的法槌已強制該玩家下台，有投票的有: ${voterMentions.joinToString("、")}"
                                )
                                    .queue()
                                nextSpeaker(guildId)
                            }
                        }
                    } else {
                        event.hook.editOriginal(":x: 你不是玩家").queue()
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

    override fun startAutoSpeechFlow(session: Session, channelId: Long, callback: (() -> Unit)?) {
        val guildId = session.guildId
        if (speechSessions.containsKey(guildId))
            return

        val speechSession = SpeechSession(
            guildId = guildId,
            channelId = channelId,
            session = session,
            finishedCallback = callback
        )
        speechSessions[guildId] = speechSession

        val guild = session.guild ?: return

        if (session.muteAfterSpeech)
            setAllMute(guildId, true)
        for (player in session.alivePlayers().values) {
            if (player.police) {
                session.courtTextChannel?.sendMessageEmbeds(
                    EmbedBuilder()
                        .setTitle("警長請選擇發言順序")
                        .setDescription("警長尚未選擇順序")
                        .setColor(MsgUtils.randomColor).build()
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
        val shuffled = session.alivePlayers().values.shuffled()
        val randOrder = SpeechOrder.getRandomOrder()
        changeOrder(guildId, randOrder, session.alivePlayers().values, shuffled.first())
        session.courtTextChannel?.sendMessageEmbeds(
            EmbedBuilder()
                .setTitle("找不到警長，自動抽籤發言順序")
                .setDescription("抽到的順序: 玩家${shuffled.first().id}$randOrder")
                .setColor(MsgUtils.randomColor).build()
        )?.queue()

        for (c in guild.textChannels) {
            c.sendMessage("⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯我是白天分隔線⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯").queue()
        }

        nextSpeaker(guildId)
    }

    override fun startTimer(guildId: Long, channelId: Long, voiceChannelId: Long, seconds: Int) {
        val guild = WerewolfApplication.jda.getGuildById(guildId) ?: return
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

    override fun interruptSession(guildId: Long, triggerCallback: Boolean) {
        speechSessions.remove(guildId)?.let {
            gameSessionService.getSession(guildId).getOrNull()?.let { session ->
                session.courtTextChannel?.sendMessage("法官已強制終止發言流程")?.queue()
            }

            it.order.clear()
            stopCurrentSpeaker(it)
            if (triggerCallback) {
                it.finishedCallback?.invoke()
            }
        }
    }

    override fun skipToNext(guildId: Long) {
        val speechSession = speechSessions[guildId]
        if (speechSession != null) {
            val guild = WerewolfApplication.jda.getGuildById(guildId)
            if (guild != null) {
                speechSession.session.courtTextChannel?.sendMessage("法官已強制該玩家下台")?.queue()
            }
            nextSpeaker(guildId)
        }
    }

    override fun setAllMute(guildId: Long, mute: Boolean) {
        val guild = WerewolfApplication.jda.getGuildById(guildId) ?: return
        for (member in guild.members) {
            if (member.isAdmin() || member.user.isBot)
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

        gameSessionService.withLockedSession(guildId) { session ->
            val guild = WerewolfApplication.jda.getGuildById(guildId) ?: return@withLockedSession

            speechSession.lastSpeaker?.let { lastSpeakerPid ->
                val lastPlayer = session.getPlayer(lastSpeakerPid)
                val member = lastPlayer?.user?.idLong?.let { guild.getMemberById(it) }
                if (member != null) {
                    try {
                        if (session.muteAfterSpeech)
                            guild.mute(member, true).queue()
                    } catch (_: Exception) {
                    }
                }
            }

            if (speechSession.order.isEmpty()) {
                session.courtTextChannel?.sendMessage("發言流程結束")?.queue()

                speechSessions.remove(guildId)
                gameSessionService.broadcastSessionUpdate(session)
                speechSession.finishedCallback?.invoke()
                return@withLockedSession
            }

            val player = speechSession.order.removeFirst()
            speechSession.lastSpeaker = player.id
            val time = getSpeakerDuration(player.police)
            speechSession.totalSpeechTime = time
            speechSession.currentSpeechEndTime = System.currentTimeMillis() + (time * 1000L)

            val t = thread(start = true) {
                try {
                    player.user?.idLong?.let { userId ->
                        val member = guild.getMemberById(userId)
                        member?.let { guild.mute(it, false).queue() }
                    }
                } catch (_: Exception) {
                }

                val message = session.courtTextChannel?.sendMessage(
                    "${player.user?.asMention ?: player.nickname} 你有 $time 秒可以發言\n"
                )
                    ?.setComponents(
                        ActionRow.of(
                            Button.danger("skipSpeech", "跳過 (發言者按)").withEmoji(Emoji.fromUnicode("U+23ed")),
                            Button.danger("interruptSpeech", "下台 (玩家或法官按)")
                                .withEmoji(Emoji.fromUnicode("U+1f5d1"))
                        )
                    )
                    ?.complete() ?: return@thread

                val voiceChannel = session.courtVoiceChannel
                try {
                    var remainingMs = time * 1000L
                    var warned30s = false
                    val stepMs = 500L

                    while (remainingMs > 0) {
                        if (speechSession.shouldStopCurrentSpeaker) break

                        val currentSession = gameSessionService.getSession(guildId).orElse(null)
                        if (currentSession?.stateData?.paused == true) {
                            Thread.sleep(stepMs)
                            continue
                        }

                        Thread.sleep(stepMs)
                        remainingMs -= stepMs

                        if (time > 30 && !warned30s && remainingMs in 29000L..30500L) {
                            voiceChannel?.play(Audio.Resource.TIMER_30S_REMAINING)
                            warned30s = true
                        }
                    }

                    if (!speechSession.shouldStopCurrentSpeaker && remainingMs <= 0) {
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
                        message.reply("發言中斷（可能發生錯誤）").queue()
                        nextSpeaker(guildId)
                    }
                }
            }
            speechSession.speakingThread = t
        }
    }

    private fun stopCurrentSpeaker(session: SpeechSession) {
        session.shouldStopCurrentSpeaker = true
        session.speakingThread?.interrupt()
        session.speakingThread = null
    }

    private fun getSpeakerDuration(isPolice: Boolean): Int {
        return if (isPolice) 210 else 180
    }

    override fun getSpeechStatus(guildId: Long): dev.robothanzo.werewolf.game.model.SpeechStatus? {
        val session = speechSessions[guildId] ?: return null
        return dev.robothanzo.werewolf.game.model.SpeechStatus(
            order = session.order.map { it.id },
            currentSpeakerId = session.lastSpeaker,
            endTime = session.currentSpeechEndTime,
            totalTime = session.totalSpeechTime,
            isPaused = session.shouldStopCurrentSpeaker, // Or another dedicated flag if available
            interruptVotes = session.interruptVotes.toList()
        )
    }

    override fun extendSpeechEndTime(guildId: Long, addedMillis: Long) {
        speechSessions[guildId]?.let {
            it.currentSpeechEndTime += addedMillis
        }
    }

    private fun getTotalQueueDuration(queue: List<Player>): Int {
        var duration = 0
        for (p in queue) {
            duration += getSpeakerDuration(p.police)
        }
        return duration
    }
}
