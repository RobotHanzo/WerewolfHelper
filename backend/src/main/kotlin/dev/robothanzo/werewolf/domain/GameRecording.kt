package dev.robothanzo.werewolf.domain

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

/**
 * An immutable, self-contained record of a finished game ("復盤" / replay). Built once, when the
 * judge confirms the win banner ([dev.robothanzo.werewolf.service.GameActionService.confirmWin]) —
 * a guild therefore accumulates **many** recordings over its lifetime (one per game it resets
 * through). Everything the cinematic replay player needs is denormalized here so the document never
 * changes when roles / i18n evolve, and the live game state can be reset freely afterwards.
 *
 * [participantIds] is the union of every player, judge, and spectator user id at finalize time; the
 * replay list is filtered by it so a user only ever sees games they took part in.
 */
@Document(collection = "recordings")
data class GameRecording(
    @Id
    val id: String,
    @Indexed
    val guildId: Long,
    val guildName: String?,
    val guildIcon: String?,
    /** Auto-generated human title, e.g. "2026/06/17 21:40 · 12 人局". */
    val title: String,
    val startedAt: Instant,
    @Indexed
    val endedAt: Instant,
    val playerCount: Int,
    val doubleIdentity: Boolean,
    /** WOLF / GOD / VILLAGER (the broad winning side), or null if undetermined. */
    val winnerFaction: String?,
    val winnerReasonKey: String?,
    @Indexed
    val participantIds: Set<Long> = emptySet(),
    val players: List<RecordedSeat> = emptyList(),
    val events: List<RecordedEvent> = emptyList(),
    /** Wolf 密談 lines (the dedicated red panel); court text chat lives inline in [events]. */
    val wolfChat: List<RecordedChat> = emptyList(),
)

/** A seat's final state + the identities it held (revealed in the replay on death / spoiler / end). */
data class RecordedSeat(
    val seat: Int,
    val memberId: Long?,
    val name: String?,
    val avatar: String?,
    val police: Boolean = false,
    val identities: List<RecordedIdentity> = emptyList(),
)

data class RecordedIdentity(
    val roleId: String,
    val name: String,
    val faction: String,
)

/**
 * One point on the replay timeline. [text] is the rendered zh-TW line shown in the event stream;
 * [effect] lets the player fold deaths / police / winner forward to reconstruct the board at the
 * playhead. Court text-chat messages are folded in as `type/kind = "speech"` events carrying their
 * own [author]/[avatar] (the author may not map to a live seat — judges/spectators chat too).
 */
data class RecordedEvent(
    val idx: Int,
    val day: Int,
    /** night | day | end — drives the stage ambiance + scrubber segments. */
    val phaseType: String,
    /** system | seer | witch | hunter | speech | police | vote | death | wolf | result. */
    val type: String,
    /** event | skill | speech | vote | death | chat | result — drives the filter chips. */
    val kind: String,
    val actorSeat: Int? = null,
    val author: String? = null,
    val avatar: String? = null,
    val text: String,
    val atMs: Long,
    val effect: RecordedEffect? = null,
    val vote: RecordedVote? = null,
)

data class RecordedEffect(
    val kill: List<Int> = emptyList(),
    val police: Int? = null,
    val winner: String? = null,
)

/** A tally breakdown surfaced in the vote overlay. */
data class RecordedVote(
    /** police | exile. */
    val kind: String,
    val out: Int? = null,
    val win: Int? = null,
    val note: String? = null,
    val rows: List<RecordedVoteRow> = emptyList(),
)

data class RecordedVoteRow(
    val seat: Int,
    val count: Double,
    val voters: List<Int> = emptyList(),
)

/** A recorded chat line (wolf 密談, or a court message folded into the event stream). */
data class RecordedChat(
    val seat: Int? = null,
    val userId: Long,
    val author: String,
    val avatar: String? = null,
    val content: String,
    val atMs: Long,
)
