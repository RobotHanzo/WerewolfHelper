package dev.robothanzo.werewolf.listeners

import com.mongodb.client.model.Filters.eq
import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.commands.Server
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.utils.SetupHelper
import dev.robothanzo.werewolf.utils.isAdmin
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.guild.GuildJoinEvent
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class GuildJoinListener : ListenerAdapter() {
    private val log = LoggerFactory.getLogger(GuildJoinListener::class.java)

    override fun onGuildJoin(event: GuildJoinEvent) {
        checkAndSetup(event.guild)
    }

    override fun onGuildMemberRoleAdd(event: GuildMemberRoleAddEvent) {
        if (event.user == event.jda.selfUser) {
            checkAndSetup(event.guild)
        }
    }

    override fun onGuildLeave(event: GuildLeaveEvent) {
        log.info("Bot left guild: {}", event.guild.id)
        Session.fetchCollection().deleteOne(eq("guildId", event.guild.idLong))
        WerewolfApplication.speechService.interruptSession(event.guild.idLong)
    }

    private fun checkAndSetup(guild: Guild) {
        val ownerId = guild.ownerIdLong

        if (Server.pendingSetups.containsKey(ownerId)) {
            if (guild.selfMember.isAdmin()) {
                val setupConfig = Server.pendingSetups.remove(ownerId)
                log.info("Found pending setup for guild owned by {}. Starting setup.", ownerId)
                if (setupConfig != null) {
                    SetupHelper.setup(guild, setupConfig)
                }
            } else {
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
