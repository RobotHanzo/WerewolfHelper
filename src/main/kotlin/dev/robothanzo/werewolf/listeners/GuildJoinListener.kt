package dev.robothanzo.werewolf.listeners

import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.commands.Server
import dev.robothanzo.werewolf.utils.SetupHelper
import dev.robothanzo.werewolf.utils.isAdmin
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.guild.GuildJoinEvent
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent
import net.dv8tion.jda.api.events.session.ReadyEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class GuildJoinListener(
    private val gameSessionService: dev.robothanzo.werewolf.service.GameSessionService
) : ListenerAdapter() {
    private val log = LoggerFactory.getLogger(GuildJoinListener::class.java)

    override fun onReady(event: ReadyEvent) {
        log.info("JDA Ready, syncing {} guilds with sessions...", event.jda.guilds.size)
        val allSessions = gameSessionService.getAllSessions()
        val joinedGuildIds = event.jda.guilds.map { it.idLong }.toSet()

        allSessions.forEach { session ->
            if (!joinedGuildIds.contains(session.guildId)) {
                log.info("Deleting stale session for guild {} (not in joined guilds)", session.guildId)
                cleanupGuild(session.guildId)
            }
        }
    }

    override fun onGuildJoin(event: GuildJoinEvent) {
        log.info("Bot joined guild: {} ({})", event.guild.name, event.guild.id)
        checkAndSetup(event.guild)
    }

    override fun onGuildMemberRoleAdd(event: GuildMemberRoleAddEvent) {
        if (event.user == event.jda.selfUser) {
            checkAndSetup(event.guild)
        }
    }

    override fun onGuildLeave(event: GuildLeaveEvent) {
        log.info("Bot left guild: {}", event.guild.id)
        cleanupGuild(event.guild.idLong)
    }

    private fun cleanupGuild(guildId: Long) {
        gameSessionService.deleteSession(guildId)
        WerewolfApplication.speechService.interruptSession(guildId)
        WerewolfApplication.policeService.interrupt(guildId)
        WerewolfApplication.expelService.endExpelPoll(guildId)
    }

    private fun checkAndSetup(guild: Guild) {
        val ownerId = guild.ownerIdLong
        log.info(
            "Checking pending setup for guild: {} (Owner: {}). Pending owners: {}",
            guild.id,
            ownerId,
            Server.pendingSetups.keys
        )

        if (Server.pendingSetups.containsKey(ownerId)) {
            if (guild.selfMember.isAdmin()) {
                val setupConfig = Server.pendingSetups.remove(ownerId)
                log.info("Found pending setup for guild owned by {}. Starting setup.", ownerId)
                if (setupConfig != null) {
                    SetupHelper.setup(guild, setupConfig)
                }
            } else {
                log.warn("Bot is missing Administrator permission in guild {}", guild.id)
                // Try to warn in default channel
                val defaultChannel = guild.defaultChannel
                if (defaultChannel is TextChannel && defaultChannel.canTalk()) {
                    defaultChannel.sendMessage(
                        ":warning: 機器人需要 **管理員 (Administrator)** 權限才能設定伺服器。請授予權限後，設定將會自動開始。"
                    ).queue()
                }
            }
        }
    }
}
