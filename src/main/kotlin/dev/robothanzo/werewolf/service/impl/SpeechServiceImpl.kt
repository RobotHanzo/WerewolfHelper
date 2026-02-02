package dev.robothanzo.werewolf.service.impl

import dev.robothanzo.werewolf.audio.Audio
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.model.SpeechOrder
import dev.robothanzo.werewolf.model.SpeechSession
import dev.robothanzo.werewolf.security.SessionRepository
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
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent
import net.dv8tion.jda.api.utils.TimeFormat
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

@Service
class SpeechServiceImpl(
    private val sessionRepository: SessionRepository,
    private val discordService: DiscordService,
    @Lazy private val gameSessionService: GameSessionService
) : SpeechService {

    private val log = LoggerFactory.getLogger(SpeechServiceImpl::class.java)

    private val speechSessions: MutableMap<Long, SpeechSession> = ConcurrentHashMap()
    private val timers: MutableMap<Long, Thread> = ConcurrentHashMap()

    override fun getSpeechSession(guildId: Long): SpeechSession? {
        return speechSessions[guildId]
    }

    override fun startSpeechPoll(
        guild: Guild, enrollMessage: Message?, players: Collection<Session.Player>,
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
        val shuffledPlayers = LinkedList(players)
        shuffledPlayers.shuffle()
        val target = shuffledPlayers.first

        if (enrollMessage != null) {
            enrollMessage.replyEmbeds(
                EmbedBuilder()
                    .setTitle("隨機抽取投票辯論順序")
                    .setDescription("抽到的順序: 玩家" + target.id + order.toString())
                    .setColor(MsgUtils.randomColor).build()
            ).queue()
        }

        changeOrder(guild.idLong, order, players, target)
        nextSpeaker(guild.idLong)
    }

    override fun startLastWordsSpeech(
        guild: Guild,
        channelId: Long,
        player: Session.Player,
        callback: (() -> Unit)?
    ) {
        val orderList: MutableList<Session.Player> = LinkedList()
        orderList.add(player)

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

    override fun setSpeechOrder(guildId: Long, order: SpeechOrder) {
        val session = sessionRepository.findByGuildId(guildId).orElse(null)
        if (session == null)
            return

        var target: Session.Player? = null
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

    override fun confirmSpeechOrder(guildId: Long) {
        nextSpeaker(guildId)
    }

    override fun handleOrderSelection(event: StringSelectInteractionEvent) {
        event.deferReply(true).queue()
        val guildId = event.guild!!.idLong
        val session = sessionRepository.findByGuildId(guildId).orElse(null)
        if (session == null)
            return

        val order = SpeechOrder.fromString(event.selectedOptions.first().value)
        if (!speechSessions.containsKey(guildId)) {
            event.hook.editOriginal("法官尚未開始發言流程").queue()
            return
        }

        var target: Session.Player? = null
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
        val guildId = event.guild!!.idLong
        val session = sessionRepository.findByGuildId(guildId).orElse(null)
        if (session == null)
            return

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
        val guildId = event.guild!!.idLong
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
        val guildId = event.guild!!.idLong
        val speechSession = speechSessions[guildId]

        if (speechSession != null) {
            if (speechSession.lastSpeaker != null && !event.member!!.hasPermission(Permission.ADMINISTRATOR)) {
                if (event.user.idLong == speechSession.lastSpeaker) {
                    event.hook.editOriginal(":x: 若要跳過發言請按左邊的跳過按鈕").queue()
                } else {
                    val session = speechSession.session!!
                    if (event.member!!.roles
                            .contains(event.guild!!.getRoleById(session.spectatorRoleId))
                    ) {
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
                                val voterMentions: MutableList<String> = LinkedList()
                                for (voter in speechSession.interruptVotes) {
                                    voterMentions.add("<@!$voter>")
                                }
                                event.message.reply(
                                    "人民的法槌已強制該玩家下台，有投票的有: " + voterMentions.joinToString(
                                        "、"
                                    )
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

    override fun startAutoSpeechFlow(guildId: Long, channelId: Long) {
        if (speechSessions.containsKey(guildId))
            return

        val session = sessionRepository.findByGuildId(guildId).orElse(null)
        if (session == null)
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
                        val member = guild.getMemberById(player.userId!!)
                        if (member != null) {
                            guild.mute(member, true).queue()
                        }
                    }
                } catch (ignored: Exception) {
                }
            }
            if (player.police) {
                if (channel != null) {
                    channel.sendMessageEmbeds(
                        EmbedBuilder()
                            .setTitle("警長請選擇發言順序")
                            .setDescription("警長尚未選擇順序")
                            .setColor(MsgUtils.randomColor).build()
                    )
                        .setComponents(
                            ActionRow.of(
                                StringSelectMenu.create("selectOrder")
                                    .addOption(SpeechOrder.UP.toString(), "up", SpeechOrder.UP.toEmoji())
                                    .addOption(SpeechOrder.DOWN.toString(), "down", SpeechOrder.DOWN.toEmoji())
                                    .setPlaceholder("請警長按此選擇發言順序").build()
                            ),
                            ActionRow.of(Button.success("confirmOrder", "確認選取"))
                        )
                        .queue()
                }
                gameSessionService.broadcastUpdate(guildId)
                return
            }
        }

        // No police found, auto random
        val shuffled = LinkedList(session.fetchAlivePlayers().values)
        shuffled.shuffle()
        val randOrder = SpeechOrder.getRandomOrder()
        changeOrder(guildId, randOrder, session.fetchAlivePlayers().values, shuffled.first)
        if (channel != null) {
            channel.sendMessageEmbeds(
                EmbedBuilder()
                    .setTitle("找不到警長，自動抽籤發言順序")
                    .setDescription("抽到的順序: 玩家" + shuffled.first.id + randOrder.toString())
                    .setColor(MsgUtils.randomColor).build()
            ).queue()
        }

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
                    if (voiceChannel != null)
                        Audio.play(Audio.Resource.TIMER_30S_REMAINING, voiceChannel)
                    Thread.sleep(30000)
                } else {
                    Thread.sleep(seconds * 1000L)
                }
                if (voiceChannel != null)
                    Audio.play(Audio.Resource.TIMER_ENDED, voiceChannel)
                message.editMessage(message.contentRaw + " (已結束)").queue()
                message.reply("計時結束").queue()
            } catch (e: InterruptedException) {
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
                val channel = guild.getTextChannelById(speechSession.channelId)
                channel?.sendMessage("法官已強制終止發言流程")?.queue()
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
                val channel = guild.getTextChannelById(speechSession.channelId)
                channel?.sendMessage("法官已強制該玩家下台")?.queue()
            }
            nextSpeaker(guildId)
        }
    }

    override fun setAllMute(guildId: Long, mute: Boolean) {
        val guild = discordService.getGuild(guildId)
        if (guild == null)
            return
        for (member in guild.members) {
            if (member.hasPermission(Permission.ADMINISTRATOR))
                continue
            try {
                guild.mute(member, mute).queue()
            } catch (ignored: Exception) {
            }
        }
    }

    private fun changeOrder(
        guildId: Long, order: SpeechOrder, playersRaw: Collection<Session.Player>,
        target: Session.Player
    ) {
        val speechSession = speechSessions[guildId]
        if (speechSession == null)
            return

        val players = LinkedList(playersRaw)
        players.sort()

        val prePolice = LinkedList<Session.Player>()
        var police: Session.Player? = null
        val postPolice = LinkedList<Session.Player>()

        for (player in players) {
            if (player.id == target.id) {
                police = player
                continue
            }
            if (police == null)
                prePolice.add(player)
            else
                postPolice.add(player)
        }

        val orderList: MutableList<Session.Player> = LinkedList()
        if (order == SpeechOrder.UP) {
            prePolice.reverse()
            orderList.addAll(prePolice)
            postPolice.reverse()
            orderList.addAll(postPolice)
        } else {
            orderList.addAll(postPolice)
            orderList.addAll(prePolice)
        }
        police?.let { orderList.add(it) }

        speechSession.order.clear()
        speechSession.order.addAll(orderList)
        gameSessionService.broadcastUpdate(guildId)
    }

    private fun nextSpeaker(guildId: Long) {
        val speechSession = speechSessions[guildId]
        if (speechSession == null)
            return

        speechSession.interruptVotes.clear()
        stopCurrentSpeaker(speechSession)

        val guild = discordService.getGuild(guildId) ?: return
        val session = speechSession.session!!

        if (speechSession.lastSpeaker != null) {
            val member = guild.getMemberById(speechSession.lastSpeaker!!)
            if (member != null) {
                try {
                    if (session.muteAfterSpeech)
                        guild.mute(member, true).queue()
                } catch (ignored: Exception) {
                }
            }
        }

        if (speechSession.order.isEmpty()) {
            val channel = guild.getTextChannelById(speechSession.channelId)
            channel?.sendMessage("發言流程結束")?.queue()

            speechSessions.remove(guildId)
            gameSessionService.broadcastSessionUpdate(session)
            speechSession.finishedCallback?.invoke()
            return
        }

        val player = speechSession.order.removeFirst()
        speechSession.lastSpeaker = player.userId
        val time = if (player.police) 210 else 180
        speechSession.totalSpeechTime = time
        speechSession.currentSpeechEndTime = System.currentTimeMillis() + (time * 1000L)

        gameSessionService.broadcastSessionUpdate(session)

        val t = thread(start = true) {
            try {
                val member = guild.getMemberById(player.userId!!)
                if (member != null) {
                    guild.mute(member, false).queue()
                }
            } catch (ignored: Exception) {
            }

            val channel = guild.getTextChannelById(speechSession.channelId) as TextChannel?
            if (channel == null)
                return@thread

            val message = channel.sendMessage("<@!" + player.userId + "> 你有" + time + "秒可以發言\n")
                .setComponents(
                    ActionRow.of(
                        Button.danger("skipSpeech", "跳過 (發言者按)").withEmoji(Emoji.fromUnicode("U+23ed")),
                        Button.danger("interruptSpeech", "下台 (玩家或法官按)").withEmoji(Emoji.fromUnicode("U+1f5d1"))
                    )
                )
                .complete()

            val voiceChannel = guild.getVoiceChannelById(session.courtVoiceChannelId)
            try {
                Thread.sleep((time - 30) * 1000L)
                if (voiceChannel != null)
                    Audio.play(Audio.Resource.TIMER_30S_REMAINING, voiceChannel)
                Thread.sleep(35000)
                if (voiceChannel != null)
                    Audio.play(Audio.Resource.TIMER_ENDED, voiceChannel)
                message.reply("計時結束").queue()
                nextSpeaker(guildId)
            } catch (ignored: InterruptedException) {
                if (voiceChannel != null)
                    Audio.play(Audio.Resource.TIMER_ENDED, voiceChannel)
            } catch (ignored: Exception) {
                if (voiceChannel != null)
                    Audio.play(Audio.Resource.TIMER_ENDED, voiceChannel)
                message.reply("發言中斷（可能發言者離開或發生錯誤）").queue()
                nextSpeaker(guildId)
            }
        }
        speechSession.speakingThread = t
    }

    private fun stopCurrentSpeaker(session: SpeechSession) {
        if (session.speakingThread != null) {
            session.speakingThread!!.interrupt()
            session.speakingThread = null
        }
    }
}
