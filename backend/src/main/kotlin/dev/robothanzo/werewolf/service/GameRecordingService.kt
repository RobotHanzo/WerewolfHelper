package dev.robothanzo.werewolf.service

import dev.robothanzo.werewolf.discord.DiscordBot
import dev.robothanzo.werewolf.discord.guildIconUrl
import dev.robothanzo.werewolf.discord.guildName
import dev.robothanzo.werewolf.discord.listMembers
import dev.robothanzo.werewolf.domain.CourtChatData
import dev.robothanzo.werewolf.domain.DashboardRole
import dev.robothanzo.werewolf.domain.GameRecording
import dev.robothanzo.werewolf.domain.GameSession
import dev.robothanzo.werewolf.domain.RecordedChat
import dev.robothanzo.werewolf.domain.RecordedIdentity
import dev.robothanzo.werewolf.domain.RecordedSeat
import dev.robothanzo.werewolf.domain.repo.DashboardUserRepository
import dev.robothanzo.werewolf.domain.repo.GameLogRepository
import dev.robothanzo.werewolf.domain.repo.GameRecordingRepository
import dev.robothanzo.werewolf.game.roles.RoleRegistry
import jakarta.annotation.PostConstruct
import net.dv8tion.jda.api.JDA
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Records finished games for the dashboard replay ("復盤"). Court text chat is captured live onto the
 * session (via DiscordBot's court-chat handler, registered post-construct to avoid a constructor
 * cycle); the immutable [GameRecording] is built and saved once, when the judge confirms the win
 * banner. A guild therefore produces one recording per game it plays and resets through.
 */
@Service
class GameRecordingService(
    private val sessionService: GameSessionService,
    private val recordings: GameRecordingRepository,
    private val logs: GameLogRepository,
    private val dashboardUsers: DashboardUserRepository,
    private val jda: JDA?,
    private val discord: DiscordBot,
    private val roles: RoleRegistry,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val titleFormat = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm").withZone(ZoneId.systemDefault())

    @PostConstruct
    fun register() {
        discord.setCourtChatHandler(::onCourtChat)
    }

    /** Persist a captured court message onto the live session (mirrors the wolf-chat handler). */
    fun onCourtChat(guildId: Long, seat: Int?, userId: Long, author: String, avatar: String?, content: String) {
        sessionService.mutate(guildId) { session ->
            session.courtChat += CourtChatData(
                seat = seat, userId = userId, author = author, avatar = avatar,
                content = content, at = Instant.now().toEpochMilli(),
            )
        }
    }

    /**
     * Build and persist the immutable [GameRecording] from the just-finished game. Called from
     * [GameActionService.confirmWin] inside its mutate block, so [session] is the final state and the
     * guild's `game_logs` still hold this game's full event log (cleared only on the next reset).
     */
    fun finalize(session: GameSession) {
        val guildId = session.guildId
        val entries = logs.findByGuildIdOrderByTimestampDesc(guildId).asReversed() // -> ascending
        if (entries.isEmpty() && session.courtChat.isEmpty()) return

        val events = RecordingTimeline.build(entries, session.courtChat)
        val members = if (jda != null) jda.listMembers(session).associateBy { it.id } else emptyMap()

        val players = session.seats.sortedBy { it.number }.filter { it.assigned }.map { seat ->
            RecordedSeat(
                seat = seat.number,
                memberId = seat.memberId,
                name = seat.memberId?.let { members[it]?.displayName },
                avatar = seat.memberId?.let { members[it]?.avatarUrl },
                police = seat.police,
                identities = seat.cards.map { card ->
                    RecordedIdentity(card.roleId, roles.localizedName(card.roleId), roles.factionOf(card.roleId).name)
                },
            )
        }

        val participants = buildSet {
            session.seats.forEach { seat -> seat.memberId?.let(::add) }
            session.ownerId?.let(::add)
            dashboardUsers.findByGuildId(guildId)
                .filter { it.role == DashboardRole.JUDGE || it.role == DashboardRole.SPECTATOR }
                .forEach { add(it.userId) }
            if (jda != null) {
                jda.listMembers(session).filter { it.owner || it.spectator }.forEach { add(it.id) }
            }
        }

        val now = Instant.now()
        val recording = GameRecording(
            id = UUID.randomUUID().toString(),
            guildId = guildId,
            guildName = jda?.guildName(guildId),
            guildIcon = jda?.guildIconUrl(guildId),
            title = "${titleFormat.format(now)} · ${session.playerCount} 人局",
            startedAt = entries.firstOrNull()?.timestamp ?: now,
            endedAt = now,
            playerCount = session.playerCount,
            doubleIdentity = session.settings.doubleIdentity,
            winnerFaction = RecordingTimeline.winnerOf(entries),
            winnerReasonKey = null,
            participantIds = participants,
            players = players,
            events = events,
            wolfChat = session.wolfChat.map {
                RecordedChat(it.seat, it.userId, it.author, it.avatar, it.content, it.at)
            },
        )
        recordings.save(recording)
        log.info(
            "[recording] saved {} ({} events, {} players) for guild {}",
            recording.id, events.size, players.size, guildId,
        )
    }

    /** Every recording the user took part in (any role), newest first. */
    fun listForUser(userId: Long): List<GameRecording> =
        recordings.findByParticipantIdsContainingOrderByEndedAtDesc(userId)

    /** A single recording, but only if the user was a participant (cross-guild — no live lockout). */
    fun getForUser(id: String, userId: Long): GameRecording? =
        recordings.findById(id).orElse(null)?.takeIf { userId in it.participantIds }

    /** As [getForUser], but throws [RecordingNotFoundException] (→ 404) when absent or not a participant. */
    fun requireForUser(id: String, userId: Long): GameRecording =
        getForUser(id, userId) ?: throw RecordingNotFoundException(id)
}

/** Raised when a replay is missing or the caller didn't take part in it; rendered as a 404. */
class RecordingNotFoundException(val id: String) : RuntimeException("recording not found: $id")
