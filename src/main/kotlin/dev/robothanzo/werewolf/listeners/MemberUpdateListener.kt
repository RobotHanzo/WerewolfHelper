package dev.robothanzo.werewolf.listeners

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dev.robothanzo.werewolf.security.GlobalWebSocketHandler
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateAvatarEvent
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateNicknameEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.springframework.stereotype.Component

@Component
class MemberUpdateListener(
    private val webSocketHandler: GlobalWebSocketHandler
) : ListenerAdapter() {
    override fun onGuildMemberUpdateNickname(event: GuildMemberUpdateNicknameEvent) {
        broadcastUpdate(event.guild.id, event.member.id, event.member.effectiveName, event.member.effectiveAvatarUrl)
    }

    override fun onGuildMemberUpdateAvatar(event: GuildMemberUpdateAvatarEvent) {
        broadcastUpdate(event.guild.id, event.member.id, event.member.effectiveName, event.member.effectiveAvatarUrl)
    }

    private fun broadcastUpdate(guildId: String, userId: String, name: String, avatar: String) {
        val data = mapOf(
            "userId" to userId,
            "name" to name,
            "avatar" to avatar
        )

        val message = mapOf(
            "type" to "PLAYER_UPDATE",
            "data" to data
        )

        webSocketHandler.broadcastToGuild(guildId, message.json())
    }

    private fun Map<*, *>.json(): String {
        return jacksonObjectMapper().writeValueAsString(this)
    }
}
