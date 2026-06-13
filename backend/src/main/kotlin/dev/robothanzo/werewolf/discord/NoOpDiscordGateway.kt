package dev.robothanzo.werewolf.discord

import dev.robothanzo.werewolf.domain.GameSession
import org.slf4j.LoggerFactory

/**
 * The fallback gateway used when no Discord token is configured. Every operation is a logged no-op,
 * so the engine, REST API, and WebSocket hub run fully against seeded/Mongo state for local
 * development and tests without a live bot.
 */
class NoOpDiscordGateway : DiscordGateway {

    private val log = LoggerFactory.getLogger(javaClass)

    override val available: Boolean = false

    override fun listMembers(guildId: Long): List<GuildMember> = emptyList()
    override fun isOwner(guildId: Long, memberId: Long): Boolean = false
    override fun canInteract(guildId: Long, memberId: Long): Boolean = true

    override suspend fun provisionGuild(session: GameSession) = log.info("[noop] provisionGuild {}", session.guildId)
    override suspend fun resizeGuild(session: GameSession, newCount: Int) =
        log.info("[noop] resizeGuild {} -> {}", session.guildId, newCount)
    override suspend fun deleteGuild(guildId: Long) = log.info("[noop] deleteGuild {}", guildId)

    override suspend fun grantSeatRole(guildId: Long, memberId: Long, seatNumber: Int) {
        log.debug("[noop] grantSeatRole {} seat {}", memberId, seatNumber)
    }

    override suspend fun setNickname(guildId: Long, memberId: Long, nickname: String) {
        log.debug("[noop] setNickname {} -> {}", memberId, nickname)
    }

    override suspend fun grantSpectatorRole(guildId: Long, memberId: Long) {
        log.debug("[noop] grantSpectatorRole {}", memberId)
    }

    override suspend fun resetMember(guildId: Long, memberId: Long) {
        log.debug("[noop] resetMember {}", memberId)
    }

    override fun sendChannelMessage(guildId: Long, channel: ChannelKind, text: String) {
        log.debug("[noop] {} <- {}", channel, text)
    }

    override fun sendSeatMessage(guildId: Long, seatNumber: Int, text: String) {
        log.debug("[noop] seat {} <- {}", seatNumber, text)
    }

    override fun muteAll(guildId: Long) = log.debug("[noop] muteAll {}", guildId)
    override fun unmuteAll(guildId: Long) = log.debug("[noop] unmuteAll {}", guildId)
    override fun muteMember(guildId: Long, memberId: Long, muted: Boolean) {
        log.debug("[noop] muteMember {} {}", memberId, muted)
    }

    override fun playSound(guildId: Long, cue: SoundCue) = log.debug("[noop] playSound {}", cue)

    override fun relayWolfChat(
        guildId: Long,
        fromSeat: Int,
        authorName: String,
        authorAvatar: String?,
        content: String,
    ) {
        log.debug("[noop] relayWolfChat seat {} : {}", fromSeat, content)
    }
}
