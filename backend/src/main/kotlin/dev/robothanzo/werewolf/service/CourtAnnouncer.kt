package dev.robothanzo.werewolf.service

import dev.robothanzo.werewolf.discord.ChannelKind
import dev.robothanzo.werewolf.discord.DiscordGateway
import dev.robothanzo.werewolf.i18n.Msg
import org.springframework.stereotype.Component

/**
 * Renders a localized message key and posts it to the COURT (法院) channel — the one place all
 * players see. Centralizes the render-then-send pattern used by the day/night orchestrators and the
 * timer endpoints, and stays a complete no-op when the gateway has no Discord token.
 */
@Component
class CourtAnnouncer(
    private val gateway: DiscordGateway,
    private val msg: Msg,
) {
    fun announce(guildId: Long, key: String, vararg args: Any?) {
        gateway.sendChannelMessage(guildId, ChannelKind.COURT, msg.msg(key, *args))
    }
}
