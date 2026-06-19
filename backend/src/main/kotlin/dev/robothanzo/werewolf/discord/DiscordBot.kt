package dev.robothanzo.werewolf.discord

import club.minnced.discord.webhook.WebhookClient
import club.minnced.discord.webhook.send.WebhookMessageBuilder
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import dev.robothanzo.werewolf.domain.GameSession
import dev.robothanzo.werewolf.domain.Phase
import dev.robothanzo.werewolf.domain.Seat
import dev.robothanzo.werewolf.domain.repo.GameSessionRepository
import dev.robothanzo.werewolf.game.roles.RoleRegistry
import dev.robothanzo.werewolf.game.roles.RoleTag
import dev.robothanzo.werewolf.i18n.Msg
import jakarta.annotation.PostConstruct
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.guild.GuildJoinEvent
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent
import net.dv8tion.jda.api.events.guild.GuildReadyEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.events.session.ReadyEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * The live Discord bot runtime: the irreducible Discord plumbing that has **no service call site to
 * inline into** — the JDA event loop (the four listeners), the audio-cue cache + playback, and the
 * wolf-chat webhook relay. Everything else (messaging, role/nickname mutations, queries, night
 * prompts) is called directly on the injected `JDA?` by each owning service via [JdaExtensions];
 * guild provisioning lives in [GuildProvisioner].
 *
 * Services that react to Discord events register *back* here in their `@PostConstruct`
 * (`setInteractionHandler` / `setCommandHandler` / …), avoiding a constructor cycle — the bot injects
 * only infrastructure (`JDA?`, the session repo, the role registry, i18n), never a service.
 */
@Component
class DiscordBot(
    private val jda: JDA?,
    private val sessions: GameSessionRepository,
    private val roles: RoleRegistry,
    private val msg: Msg,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val playerManager = DefaultAudioPlayerManager().also { AudioSourceManagers.registerLocalSource(it) }
    private val webhookCache = ConcurrentHashMap<Long, WebhookClient>()
    private val soundFiles = ConcurrentHashMap<SoundCue, File>()

    /** Seat channels already told (this block) that relay is closed — so we explain once, then react ❌. */
    private val relayBlockWarned = ConcurrentHashMap.newKeySet<Long>()

    @Volatile
    private var interactionHandler: DiscordInteractionHandler? = null

    @Volatile
    private var commandHandler: DiscordCommandHandler? = null

    @Volatile
    private var wolfChatHandler: WolfChatHandler? = null

    @Volatile
    private var courtChatHandler: CourtChatHandler? = null

    @PostConstruct
    fun start() {
        val jda = jda ?: return
        jda.addEventListener(RelayListener())
        jda.addEventListener(LifecycleListener())
        jda.addEventListener(ComponentListener())
        jda.addEventListener(CommandListener())
    }

    // ---- inbound-event handler registration (post-construct, breaks the constructor cycle) ----
    fun setInteractionHandler(handler: DiscordInteractionHandler) { interactionHandler = handler }
    fun setCommandHandler(handler: DiscordCommandHandler) { commandHandler = handler }
    fun setWolfChatHandler(handler: WolfChatHandler) { wolfChatHandler = handler }
    fun setCourtChatHandler(handler: CourtChatHandler) { courtChatHandler = handler }

    private fun session(guildId: Long): GameSession? = sessions.findById(guildId).orElse(null)

    // ---- audio cues (lavaplayer) ----
    fun playSound(guildId: Long, cue: SoundCue) {
        val g = jda?.getGuildById(guildId) ?: return
        val voice = session(guildId)?.discordIds?.courtVoiceChannelId?.takeIf { it != 0L }
            ?.let { g.getVoiceChannelById(it) } ?: return
        val file = soundFiles.getOrPut(cue) { extractSound(cue) } ?: return
        val player = playerManager.createPlayer()
        val audioManager = g.audioManager
        audioManager.sendingHandler = AudioPlayerSendHandler(player)
        if (!audioManager.isConnected) runCatching { audioManager.openAudioConnection(voice) }
        playerManager.loadItem(file.absolutePath, object : AudioLoadResultHandler {
            override fun trackLoaded(track: AudioTrack) {
                player.startTrack(track, false)
            }

            override fun playlistLoaded(playlist: AudioPlaylist) {}
            override fun noMatches() {
                log.warn("audio cue {} not found", cue)
            }

            override fun loadFailed(e: FriendlyException) {
                log.error("audio cue {} failed: {}", cue, e.message)
            }
        })
    }

    /** Extract a bundled mp3 to a temp file so lavaplayer's local source can load it by path. */
    private fun extractSound(cue: SoundCue): File? = runCatching {
        val tmp = File.createTempFile("wh-", ".mp3").apply { deleteOnExit() }
        javaClass.classLoader.getResourceAsStream(cue.resource)!!
            .use { input -> tmp.outputStream().use { input.copyTo(it) } }
        tmp
    }.getOrNull()

    // ---- wolf-chat relay (one cached webhook per channel) ----
    /** Mirror a relayed line to the rest of the sender's group + the judge channel. */
    private fun relayWolfChat(guildId: Long, fromSeat: Int, authorName: String, authorAvatar: String?, content: String) {
        val g = jda?.getGuildById(guildId) ?: return
        val session = session(guildId) ?: return
        val sender = session.seat(fromSeat) ?: return
        val message =
            WebhookMessageBuilder().setContent(content).setUsername(authorName).setAvatarUrl(authorAvatar).build()

        val wolfGroup = isWolfChat(sender)
        val gbabyGroup = sender.goldenBaby
        session.seats.forEach { target ->
            if (target.number == fromSeat || target.channelId == 0L) return@forEach
            val sameGroup = (wolfGroup && isWolfChat(target)) || (gbabyGroup && target.goldenBaby)
            if (sameGroup) g.getTextChannelById(target.channelId)?.let { webhookFor(it).send(message) }
        }
        // Both groups are mirrored into the judge channel; 金寶寶 lines carry a [金寶寶] tag after the
        // author name so the judge can tell the two private chats apart.
        if (wolfGroup || gbabyGroup) {
            val judgeMessage = if (gbabyGroup)
                WebhookMessageBuilder().setContent(content)
                    .setUsername("$authorName ${msg.msg("relay.golden_baby_tag")}").setAvatarUrl(authorAvatar).build()
            else message
            g.getTextChannelById(session.discordIds.judgeTextChannelId)?.let { webhookFor(it).send(judgeMessage) }
        }
    }

    /** A seat participates in wolf chat if any living card has the WOLF_CHAT tag (wolves, 夢魘, …). */
    private fun isWolfChat(seat: Seat): Boolean =
        seat.livingCards().ifEmpty { seat.cards }.any { roles.byId(it.roleId)?.hasTag(RoleTag.WOLF_CHAT) == true }

    /** Forward a relayed line to the registered handler so it surfaces on the judge dashboard chat
     *  panel (both the wolf team and 金寶寶, the latter tagged [金寶寶] after the author name). */
    private fun recordWolfChat(session: GameSession, sender: Seat, userId: Long, author: String, avatar: String?, content: String) {
        if (content.isBlank()) return
        val isWolf = isWolfChat(sender)
        if (!isWolf && !sender.goldenBaby) return
        val tag = if (!isWolf && sender.goldenBaby) " ${msg.msg("relay.golden_baby_tag")}" else ""
        wolfChatHandler?.onWolfChat(session.guildId, sender.number, userId, "$author（${sender.paddedNumber}）$tag", avatar, content)
    }

    private fun webhookFor(channel: TextChannel): WebhookClient =
        webhookCache.getOrPut(channel.idLong) {
            val hook = channel.retrieveWebhooks().complete().firstOrNull { it.token != null }
                ?: channel.createWebhook("Werewolf Relay").complete()
            WebhookClient.withUrl(hook.url)
        }

    /** Cross-chat (wolf / 金寶寶) is only relayed during the night or before the game starts. */
    private fun relayWindowOpen(session: GameSession): Boolean =
        session.phase == Phase.LOBBY || session.phase == Phase.ASSIGNMENT || session.phase == Phase.NIGHT

    /** Outside the relay window: explain once per channel, then just react ❌ on later messages. */
    private fun denyOutsideWindow(event: MessageReceivedEvent) {
        if (relayBlockWarned.add(event.channel.idLong)) {
            event.message.reply(msg.msg("relay.outside_window")).queue({}, {})
        } else {
            event.message.addReaction(Emoji.fromUnicode("❌")).queue({}, {})
        }
    }

    /** Listens for messages in seat channels and mirrors wolf-team chatter. */
    private inner class RelayListener : ListenerAdapter() {
        override fun onMessageReceived(event: MessageReceivedEvent) {
            if (event.author.isBot || !event.isFromGuild) return
            val session = session(event.guild.idLong) ?: return
            val author = event.member?.effectiveName ?: event.author.name
            val avatar = event.author.effectiveAvatarUrl
            val content = event.message.contentRaw

            // From a seat channel → relay within the sender's group.
            session.seats.firstOrNull { it.channelId == event.channel.idLong }?.let { sender ->
                if (!isWolfChat(sender) && !sender.goldenBaby) return // not a chat participant — ignore
                if (!relayWindowOpen(session)) {
                    denyOutsideWindow(event)
                    return
                }
                relayBlockWarned.remove(event.channel.idLong)
                relayWolfChat(event.guild.idLong, sender.number, "$author（${sender.paddedNumber}）", avatar, content)
                recordWolfChat(session, sender, event.author.idLong, author, avatar, content)
                return
            }
            // From the judge channel → mirror to every wolf-team channel (only within the relay window).
            if (event.channel.idLong == session.discordIds.judgeTextChannelId) {
                if (!relayWindowOpen(session)) return
                val message =
                    WebhookMessageBuilder().setContent(content).setUsername("法官頻道（$author）").setAvatarUrl(avatar)
                        .build()
                session.seats.filter { isWolfChat(it) && it.channelId != 0L }
                    .forEach {
                        event.guild.getTextChannelById(it.channelId)?.let { ch -> webhookFor(ch).send(message) }
                    }
                return
            }
            // From the public court channel → record for the replay once the game has started.
            if (event.channel.idLong == session.discordIds.courtTextChannelId && session.phase != Phase.LOBBY) {
                if (content.isBlank()) return
                val seat = session.seats.firstOrNull { it.memberId == event.author.idLong }?.number
                courtChatHandler?.onCourtChat(event.guild.idLong, seat, event.author.idLong, author, avatar, content)
            }
        }
    }

    /** Membership rules (FEATURES §3): latecomers become spectators once identities are assigned; the
     *  session is deleted if the bot is removed from the guild. */
    private inner class LifecycleListener : ListenerAdapter() {
        override fun onGuildMemberJoin(event: GuildMemberJoinEvent) {
            val session = session(event.guild.idLong) ?: return
            if (event.member.idLong == session.ownerId) {
                jda?.grantJudgeRole(session, event.member.idLong) // re-grant judge to the owner (§3)
                return
            }
            if (session.assigned) jda?.grantSpectatorRole(session, event.member.idLong)
        }

        override fun onGuildJoin(event: GuildJoinEvent) {
            commandHandler?.onGuildJoined(event.guild.idLong, event.guild.ownerIdLong)
        }

        override fun onGuildReady(event: GuildReadyEvent) {
            commandHandler?.onGuildJoined(event.guild.idLong, event.guild.ownerIdLong)
        }

        override fun onGuildLeave(event: GuildLeaveEvent) {
            runCatching { sessions.deleteById(event.guild.idLong) }
            webhookCache.clear()
        }
    }

    /** Registers and handles slash commands: `/server` (create/delete) and `/game` (detonate). */
    private inner class CommandListener : ListenerAdapter() {
        override fun onReady(event: ReadyEvent) {
            event.jda.updateCommands().addCommands(
                Commands.slash("server", "管理狼人殺遊戲伺服器")
                    .addSubcommands(
                        SubcommandData("create", "建立一個新的遊戲伺服器設定")
                            .addOptions(
                                OptionData(OptionType.INTEGER, "players", "玩家人數", true).setRequiredRange(4, 20),
                                OptionData(OptionType.BOOLEAN, "double", "雙身分模式", false),
                            ),
                        SubcommandData("delete", "刪除目前伺服器的遊戲"),
                    ),
                Commands.slash("game", "遊戲中的玩家指令")
                    .addSubcommands(
                        SubcommandData("detonate", "自爆（限狼人：當前身分為狼才可使用）"),
                    ),
            ).queue()
        }

        override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
            val handler = commandHandler ?: return event.reply("尚未就緒").setEphemeral(true).queue()
            val reply = when (event.name) {
                "server" -> when (event.subcommandName) {
                    "create" -> handler.onServerCreate(
                        event.user.idLong,
                        event.getOption("players")!!.asInt,
                        event.getOption("double")?.asBoolean ?: false,
                    )

                    "delete" -> handler.onServerDelete(event.user.idLong, event.guild?.idLong ?: 0)
                    else -> "未知的指令"
                }

                "game" -> when (event.subcommandName) {
                    "detonate" -> handler.onDetonate(event.guild?.idLong ?: 0, event.user.idLong)
                    else -> "未知的指令"
                }

                else -> return
            }
            event.reply(reply).setEphemeral(true).queue()
        }
    }

    /** Routes night-action select menus and day/night vote buttons into the engine handler. */
    private inner class ComponentListener : ListenerAdapter() {
        override fun onButtonInteraction(event: ButtonInteractionEvent) {
            if (!event.componentId.startsWith(InteractionIds.PREFIX) || !event.isFromGuild) return
            val reply = interactionHandler?.handle(
                event.guild!!.idLong, event.user.idLong, event.channel.idLong, event.componentId, emptyList(),
            )
            event.reply(reply?.ack ?: "已收到").setEphemeral(true).queue()
        }

        override fun onStringSelectInteraction(event: StringSelectInteractionEvent) {
            if (!event.componentId.startsWith(InteractionIds.PREFIX) || !event.isFromGuild) return
            val reply = interactionHandler?.handle(
                event.guild!!.idLong, event.user.idLong, event.channel.idLong, event.componentId, event.values,
            )
            event.reply(reply?.ack ?: "已收到").setEphemeral(true).queue()
        }
    }
}
