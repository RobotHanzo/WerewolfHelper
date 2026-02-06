package dev.robothanzo.werewolf.listeners

import club.minnced.discord.webhook.WebhookClient
import club.minnced.discord.webhook.send.WebhookMessageBuilder
import dev.robothanzo.werewolf.database.documents.Player
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.service.GameSessionService
import dev.robothanzo.werewolf.service.NightManager
import dev.robothanzo.werewolf.utils.CmdUtils
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component

@Component
class MessageListener : ListenerAdapter() {
    @Autowired(required = false)
    @Lazy
    private lateinit var gameSessionService: GameSessionService

    @Autowired(required = false)
    @Lazy
    private lateinit var nightManager: NightManager

    companion object {
        val webhookCache: MutableMap<Long, WebhookClient> = HashMap()

        fun getWebhookClientOrCreate(channel: TextChannel): WebhookClient {
            if (!webhookCache.containsKey(channel.idLong)) {
                var webhook = channel.retrieveWebhooks().complete().firstOrNull()
                if (webhook == null) {
                    webhook = channel.createWebhook("Werewolf Impersonator").complete()
                }
                webhookCache[channel.idLong] = WebhookClient.withUrl(webhook.url)
            }
            return webhookCache[channel.idLong]!!
        }
    }

    private fun isCharacterAlive(session: Session, character: String): Boolean {
        for (player in session.alivePlayers().values) {
            if (player.roles?.contains(character) == true) {
                // Check if this specific role is NOT dead
                if (player.deadRoles == null || !player.deadRoles!!.contains(character)) {
                    return true
                }
            }
        }
        return false
    }

    private fun shouldSend(player: Player, session: Session): Boolean {
        // Kotlin handles nullability; assuming roles is not null per logic
        val roles = player.roles
        if (roles.isNullOrEmpty()) return false

        val firstRole = roles.filterNot { player.deadRoles?.contains(it) == true }.firstOrNull() ?: return false
        return firstRole.contains("狼人") ||
                roles.contains("狼兄") ||
                firstRole.contains("狼王") ||
                firstRole.contains("狼美人") ||
                firstRole.contains("白狼王") ||
                firstRole.contains("血月使者") ||
                firstRole.contains("惡靈騎士") ||
                firstRole.contains("夢魘") ||
                (roles.contains("狼弟") && !isCharacterAlive(session, "狼兄"))
    }

    override fun onMessageReceived(event: MessageReceivedEvent) {
        if (event.author.isBot) return
        val session = CmdUtils.getSession(event.guild) ?: return

        val alivePlayers = session.alivePlayers().values
        val isJudgeChannel = event.channel == session.judgeTextChannel

        for (player in alivePlayers) {
            if (!player.roles.isNullOrEmpty()) {
                if ((player.channel == event.channel && shouldSend(player, session)) ||
                    isJudgeChannel
                ) {
                    // Track werewolf messages during night phase for NightStatus
                    if (session.currentState == "NIGHT_PHASE" && ::gameSessionService.isInitialized) {
                        gameSessionService.withLockedSession(session.guildId) { lockedSession ->
                            // Record if it's from the judge channel or the werewolf's own channel
                            val messagesList = lockedSession.stateData.werewolfMessages

                            val senderId =
                                if (isJudgeChannel) 0 else lockedSession.getPlayer(event.author.idLong)?.id ?: 0
                            val displayName = event.member?.effectiveName ?: event.author.effectiveName
                            val senderName = if (isJudgeChannel) "法官頻道 (${displayName})" else displayName

                            // We should probably filter who sees these in the dashboard too, but for simplicity we'll keep all for now
                            // but mark them if possible.
                            messagesList.add(
                                dev.robothanzo.werewolf.game.model.WerewolfMessage(
                                    senderId = senderId,
                                    senderName = senderName,
                                    avatarUrl = event.author.effectiveAvatarUrl,
                                    content = event.message.contentRaw,
                                    timestamp = System.currentTimeMillis()
                                )
                            )

                            if (messagesList.size > 20) {
                                messagesList.removeAt(0)
                            }

                            // Notify NightManager and broadcast updated status inside lock to ensure data consistency
                            nightManager.notifyPhaseUpdate(lockedSession.guildId)
                            nightManager.broadcastNightStatus(lockedSession)
                        }
                    }

                    val message = WebhookMessageBuilder()
                        .setContent(event.message.contentRaw)
                        .setUsername(
                            (if (isJudgeChannel) "法官頻道" else player.nickname) +
                                    " (" + event.author.name + ")"
                        )
                        .setAvatarUrl(event.author.avatarUrl)
                        .build()

                    for (p in session.alivePlayers().values) {
                        if (shouldSend(p, session) && event.channel.idLong != p.channel?.idLong) {
                            val targetChannel = event.guild.getTextChannelById(p.channel?.idLong ?: 0)
                            if (targetChannel != null) {
                                getWebhookClientOrCreate(targetChannel).send(message)
                            }
                        }
                    }
                    if (event.channel != session.judgeTextChannel) {
                        session.judgeTextChannel?.let { getWebhookClientOrCreate(it) }?.send(message)
                    }
                    break
                }

                if (player.jinBaoBao && player.channel?.idLong == event.channel.idLong) {
                    val message = WebhookMessageBuilder()
                        .setContent(event.message.contentRaw)
                        .setUsername("${player.nickname} (${event.author.name})")
                        .setAvatarUrl(event.author.avatarUrl)
                        .build()

                    for (p in session.alivePlayers().values) {
                        if (p.jinBaoBao && event.channel.idLong != p.channel?.idLong) {
                            val targetChannel = event.guild.getTextChannelById(p.channel?.idLong ?: 0)
                            if (targetChannel != null) {
                                getWebhookClientOrCreate(targetChannel).send(message)
                            }
                        }
                    }
                    break
                }
            }
        }
    }
}
