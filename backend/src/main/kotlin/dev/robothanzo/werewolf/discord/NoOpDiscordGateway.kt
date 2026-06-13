package dev.robothanzo.werewolf.discord

import dev.robothanzo.werewolf.domain.GameSession
import dev.robothanzo.werewolf.domain.Seat
import dev.robothanzo.werewolf.i18n.Msg
import dev.robothanzo.werewolf.websocket.GameWebSocketHandler
import org.slf4j.LoggerFactory

/**
 * The fallback gateway used when no Discord token is configured. Every operation is a logged no-op,
 * so the engine, REST API, and WebSocket hub run fully against seeded/Mongo state for local
 * development and tests without a live bot.
 */
class NoOpDiscordGateway(
    private val ws: GameWebSocketHandler? = null,
    private val msg: Msg? = null,
) : DiscordGateway {

    private val log = LoggerFactory.getLogger(javaClass)

    override val available: Boolean = false

    override fun listMembers(guildId: Long): List<GuildMember> = emptyList()
    override fun isOwner(guildId: Long, memberId: Long): Boolean = false
    override fun canInteract(guildId: Long, memberId: Long): Boolean = true

    override suspend fun provisionGuild(session: GameSession) {
        log.info("[noop] provisionGuild {}", session.guildId)
        (1..session.playerCount).forEach { n ->
            if (session.seats.none { it.number == n }) {
                session.seats.add(Seat(number = n))
            }
        }
        session.seats.sortBy { it.number }
    }

    override suspend fun resizeGuild(session: GameSession, newCount: Int) {
        log.info("[noop] resizeGuild {} -> {}", session.guildId, newCount)
        session.seats.removeAll { it.number > newCount }
        (1..newCount).forEach { n ->
            if (session.seats.none { it.number == n }) {
                session.seats.add(Seat(number = n))
            }
        }
        session.seats.sortBy { it.number }
        session.settings.playerCount = newCount

        ws?.let { socket ->
            val text = msg?.msg("bulk.finished") ?: "[完成] 所有操作已結束"
            socket.broadcastProgress(session.guildId, 100, text, "info")
        }
    }


    override suspend fun deleteGuild(guildId: Long) = log.info("[noop] deleteGuild {}", guildId)

    override fun grantSeatRole(guildId: Long, memberId: Long, seatNumber: Int) {
        log.debug("[noop] grantSeatRole {} seat {}", memberId, seatNumber)
    }

    override fun setNickname(guildId: Long, memberId: Long, nickname: String) {
        log.debug("[noop] setNickname {} -> {}", memberId, nickname)
    }

    override fun grantSpectatorRole(guildId: Long, memberId: Long) {
        log.debug("[noop] grantSpectatorRole {}", memberId)
    }

    override fun resetMember(guildId: Long, memberId: Long) {
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

    override fun setInteractionHandler(handler: DiscordInteractionHandler) {
        log.debug("[noop] setInteractionHandler")
    }

    override fun setCommandHandler(handler: DiscordCommandHandler) {
        log.debug("[noop] setCommandHandler")
    }

    override fun grantJudgeRole(guildId: Long, memberId: Long) {
        log.debug("[noop] grantJudgeRole {}", memberId)
    }

    override fun promptNightAction(
        guildId: Long,
        seatNumber: Int,
        customId: String,
        prompt: String,
        options: List<SeatOption>,
        extraValues: List<Pair<String, String>>,
        allowSkip: Boolean,
        maxValues: Int,
    ) {
        log.debug("[noop] promptNightAction seat {} {}", seatNumber, customId)
    }

    override fun promptWolfVote(guildId: Long, voterSeats: List<Int>, options: List<SeatOption>) {
        log.debug("[noop] promptWolfVote {}", voterSeats)
    }
}
