package dev.robothanzo.werewolf.listeners

import club.minnced.discord.webhook.WebhookClient
import club.minnced.discord.webhook.send.WebhookMessageBuilder
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.utils.CmdUtils
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.slf4j.LoggerFactory

class MessageListener : ListenerAdapter() {
    private val log = LoggerFactory.getLogger(MessageListener::class.java)

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
        for (player in session.fetchAlivePlayers().values) {
            if (player.roles?.contains(character) == true) {
                // Check if this specific role is NOT dead
                if (player.deadRoles == null || !player.deadRoles!!.contains(character)) {
                    return true
                }
            }
        }
        return false
    }

    private fun shouldSend(player: Session.Player, session: Session): Boolean {
        // Kotlin handles nullability; assuming roles is not null per logic
        val roles = player.roles
        if (roles.isNullOrEmpty()) return false

        val firstRole = roles.first()
        return firstRole.contains("狼人") ||
                roles.contains("狼兄") ||
                firstRole.contains("狼王") ||
                firstRole.contains("狼美人") ||
                firstRole.contains("血月使者") ||
                firstRole.contains("惡靈騎士") ||
                firstRole.contains("夢魘") ||
                (roles.contains("狼弟") && !isCharacterAlive(session, "狼兄"))
    }

    override fun onMessageReceived(event: MessageReceivedEvent) {
        if (event.author.isBot) return
        val session = CmdUtils.getSession(event.guild) ?: return

        for (player in session.fetchAlivePlayers().values) {
            if (!player.roles.isNullOrEmpty()) {
                if ((shouldSend(player, session) && player.channelId == event.channel.idLong)
                    || event.channel.idLong == session.judgeTextChannelId
                ) {
                    val message = WebhookMessageBuilder()
                        .setContent(event.message.contentRaw)
                        .setUsername(
                            (if (event.channel.idLong == session.judgeTextChannelId) "法官頻道" else player.nickname) +
                                    " (" + event.author.name + ")"
                        )
                        .setAvatarUrl(event.author.avatarUrl)
                        .build()

                    for (p in session.fetchAlivePlayers().values) {
                        if (shouldSend(p, session) && event.channel.idLong != p.channelId) {
                            val targetChannel = event.guild.getTextChannelById(p.channelId)
                            if (targetChannel != null) {
                                getWebhookClientOrCreate(targetChannel).send(message)
                            }
                        }
                    }
                    if (event.channel.idLong != session.judgeTextChannelId) {
                        val judgeChannel = event.guild.getTextChannelById(session.judgeTextChannelId)
                        if (judgeChannel != null) {
                            getWebhookClientOrCreate(judgeChannel).send(message)
                        }
                    }
                    break
                }

                if (player.jinBaoBao && player.channelId == event.channel.idLong) {
                    val message = WebhookMessageBuilder()
                        .setContent(event.message.contentRaw)
                        .setUsername("${player.nickname} (${event.author.name})")
                        .setAvatarUrl(event.author.avatarUrl)
                        .build()

                    for (p in session.fetchAlivePlayers().values) {
                        if (p.jinBaoBao && event.channel.idLong != p.channelId) {
                            val targetChannel = event.guild.getTextChannelById(p.channelId)
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
