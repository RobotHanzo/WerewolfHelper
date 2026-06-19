package dev.robothanzo.werewolf.service

import dev.robothanzo.werewolf.discord.ChannelKind
import dev.robothanzo.werewolf.discord.sendChannelMessage
import dev.robothanzo.werewolf.domain.repo.GameSessionRepository
import dev.robothanzo.werewolf.i18n.Msg
import net.dv8tion.jda.api.JDA
import org.springframework.stereotype.Component

/**
 * Renders a localized message key and posts it to the COURT (法院) channel — the one place all
 * players see. Centralizes the render-then-send pattern used by the day/night orchestrators and the
 * timer endpoints, and stays a complete no-op when there is no Discord connection (`JDA?` is null).
 */
@Component
class CourtAnnouncer(
    private val jda: JDA?,
    private val sessions: GameSessionRepository,
    private val msg: Msg,
) {
    fun announce(guildId: Long, key: String, vararg args: Any?) {
        val jda = jda ?: return
        val session = sessions.findById(guildId).orElse(null) ?: return
        jda.sendChannelMessage(session, ChannelKind.COURT, msg.msg(key, *args))
    }

    /**
     * Render [key] once and post it to each of [channels]. Used for the game-over notice, which stays
     * private to the judge + spectator channels until the judge reveals the result to the court.
     */
    fun announceTo(guildId: Long, channels: List<ChannelKind>, key: String, vararg args: Any?) {
        val jda = jda ?: return
        val session = sessions.findById(guildId).orElse(null) ?: return
        val text = msg.msg(key, *args)
        channels.forEach { jda.sendChannelMessage(session, it, text) }
    }
}
