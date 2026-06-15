package dev.robothanzo.werewolf.service

import dev.robothanzo.werewolf.discord.DiscordGateway
import dev.robothanzo.werewolf.discord.DiscordProperties
import dev.robothanzo.werewolf.domain.GameSession
import dev.robothanzo.werewolf.domain.LogSeverity
import dev.robothanzo.werewolf.domain.PendingSetup
import dev.robothanzo.werewolf.domain.repo.PendingSetupRepository
import dev.robothanzo.werewolf.i18n.Msg
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * The `/server create` flow (FEATURES §3): a trusted server creator stores a pending config; when the
 * bot later joins (or becomes ready in) the guild that creator owns, the server is built
 * automatically from that config and the pending entry consumed. [CommandRouter] is the gateway's
 * registered command handler and delegates the `/server` subcommands + bot-join events here.
 */
@Service
class ServerProvisioningService(
    private val gateway: DiscordGateway,
    private val properties: DiscordProperties,
    private val sessionService: GameSessionService,
    private val pendingSetups: PendingSetupRepository,
    private val msg: Msg,
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun onServerCreate(creatorId: Long, playerCount: Int, doubleIdentity: Boolean): String {
        if (creatorId !in properties.serverCreatorIds) return msg.msg("cmd.create.denied")
        pendingSetups.save(PendingSetup("setup:$creatorId", creatorId, playerCount, doubleIdentity))
        val invite = "https://discord.com/oauth2/authorize?client_id=${properties.clientId}" +
            "&permissions=8&scope=bot+applications.commands"
        val mode = if (doubleIdentity) msg.msg("cmd.create.double") else ""
        return msg.msg("cmd.create.ok", playerCount, mode, invite)
    }

    fun onServerDelete(creatorId: Long, guildId: Long): String {
        if (creatorId !in properties.serverCreatorIds) return msg.msg("cmd.delete.denied")
        if (guildId == 0L) return msg.msg("cmd.delete.no_guild")
        sessionService.find(guildId) ?: return msg.msg("error.session_not_found")
        scope.launch { gateway.deleteGuild(guildId) }
        sessionService.clearLogs(guildId)
        sessionService.delete(guildId)
        return msg.msg("cmd.delete.ok")
    }

    fun onGuildJoined(guildId: Long, ownerId: Long) {
        if (sessionService.find(guildId) != null) return // already provisioned
        val pending = pendingSetups.findByCreatorId(ownerId) ?: return
        scope.launch {
            try {
                val session = GameSession(guildId = guildId, ownerId = ownerId).apply {
                    settings.playerCount = pending.playerCount
                    settings.doubleIdentity = pending.doubleIdentity
                }
                gateway.provisionGuild(session)
                sessionService.save(session)
                sessionService.log(guildId, LogSeverity.ACTION, "provision.completed")
                pendingSetups.delete(pending)
                sessionService.broadcast(session)
                gateway.grantJudgeRole(guildId, ownerId)
                log.info("Auto-provisioned guild {} from pending config of {}", guildId, ownerId)
            } catch (e: Exception) {
                log.error("Auto-provisioning guild {} failed: {}", guildId, e.message)
            }
        }
    }

    @PreDestroy
    fun shutdown() = scope.cancel()
}
