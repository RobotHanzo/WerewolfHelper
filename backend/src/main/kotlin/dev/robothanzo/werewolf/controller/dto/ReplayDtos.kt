package dev.robothanzo.werewolf.controller.dto

import dev.robothanzo.werewolf.domain.GameRecording
import dev.robothanzo.werewolf.domain.RecordedChat
import dev.robothanzo.werewolf.domain.RecordedEvent
import dev.robothanzo.werewolf.domain.RecordedSeat
import io.swagger.v3.oas.annotations.media.Schema

/**
 * Wire contract for the replay player. Discord/user ids are serialized as **strings** (they exceed
 * JS's safe-integer range), mirroring [SeatDto]; timestamps are epoch millis. Kept in sync with the
 * hand-written `frontend/src/types/replay.ts` by hand (no codegen).
 */
@Schema(description = "Response carrying the list of the user's replays")
data class ReplayListResponse(val data: List<ReplaySummary>) : ApiResponse(true)

@Schema(description = "Response carrying a single full replay recording")
data class ReplayResponse(val data: ReplayDto) : ApiResponse(true)

@Schema(description = "A replay list item (lightweight, for the selector)")
data class ReplaySummary(
    val id: String,
    val guildId: String,
    val guildName: String?,
    val guildIcon: String?,
    val title: String,
    val startedAt: Long,
    val endedAt: Long,
    val durationMs: Long,
    val playerCount: Int,
    val doubleIdentity: Boolean,
    val winnerFaction: String?,
)

@Schema(description = "A full replay recording")
data class ReplayDto(
    val id: String,
    val guildId: String,
    val guildName: String?,
    val guildIcon: String?,
    val title: String,
    val startedAt: Long,
    val endedAt: Long,
    val playerCount: Int,
    val doubleIdentity: Boolean,
    val winnerFaction: String?,
    val winnerReasonKey: String?,
    val players: List<ReplaySeatDto>,
    val events: List<ReplayEventDto>,
    val wolfChat: List<ReplayChatDto>,
)

data class ReplaySeatDto(
    val seat: Int,
    val memberId: String?,
    val name: String?,
    val avatar: String?,
    val police: Boolean,
    val identities: List<ReplayIdentityDto>,
)

data class ReplayIdentityDto(val roleId: String, val name: String, val faction: String)

data class ReplayEventDto(
    val idx: Int,
    val day: Int,
    val phaseType: String,
    val type: String,
    val kind: String,
    val actorSeat: Int?,
    val author: String?,
    val avatar: String?,
    val text: String,
    val atMs: Long,
    val effect: ReplayEffectDto?,
    val vote: ReplayVoteDto?,
)

data class ReplayEffectDto(val kill: List<Int>, val police: Int?, val winner: String?)

data class ReplayVoteDto(
    val kind: String,
    val out: Int?,
    val win: Int?,
    val note: String?,
    val rows: List<ReplayVoteRowDto>,
)

data class ReplayVoteRowDto(val seat: Int, val count: Double, val voters: List<Int>)

data class ReplayChatDto(
    val seat: Int?,
    val userId: String,
    val author: String,
    val avatar: String?,
    val content: String,
    val atMs: Long,
)

fun GameRecording.toSummary() = ReplaySummary(
    id = id,
    guildId = guildId.toString(),
    guildName = guildName,
    guildIcon = guildIcon,
    title = title,
    startedAt = startedAt.toEpochMilli(),
    endedAt = endedAt.toEpochMilli(),
    durationMs = (endedAt.toEpochMilli() - startedAt.toEpochMilli()).coerceAtLeast(0),
    playerCount = playerCount,
    doubleIdentity = doubleIdentity,
    winnerFaction = winnerFaction,
)

fun GameRecording.toReplayDto() = ReplayDto(
    id = id,
    guildId = guildId.toString(),
    guildName = guildName,
    guildIcon = guildIcon,
    title = title,
    startedAt = startedAt.toEpochMilli(),
    endedAt = endedAt.toEpochMilli(),
    playerCount = playerCount,
    doubleIdentity = doubleIdentity,
    winnerFaction = winnerFaction,
    winnerReasonKey = winnerReasonKey,
    players = players.map { it.toDto() },
    events = events.map { it.toDto() },
    wolfChat = wolfChat.map { it.toDto() },
)

private fun RecordedSeat.toDto() = ReplaySeatDto(
    seat = seat,
    memberId = memberId?.toString(),
    name = name,
    avatar = avatar,
    police = police,
    identities = identities.map { ReplayIdentityDto(it.roleId, it.name, it.faction) },
)

private fun RecordedEvent.toDto() = ReplayEventDto(
    idx = idx,
    day = day,
    phaseType = phaseType,
    type = type,
    kind = kind,
    actorSeat = actorSeat,
    author = author,
    avatar = avatar,
    text = text,
    atMs = atMs,
    effect = effect?.let { ReplayEffectDto(it.kill, it.police, it.winner) },
    vote = vote?.let { v ->
        ReplayVoteDto(v.kind, v.out, v.win, v.note, v.rows.map { ReplayVoteRowDto(it.seat, it.count, it.voters) })
    },
)

private fun RecordedChat.toDto() = ReplayChatDto(
    seat = seat,
    userId = userId.toString(),
    author = author,
    avatar = avatar,
    content = content,
    atMs = atMs,
)
