package dev.robothanzo.werewolf.controller.dto

import io.swagger.v3.oas.annotations.media.Schema

/**
 * The full game-state snapshot broadcast to every dashboard client after **each** mutation
 * (snapshot-as-truth, FEATURES §10.5). The frontend store replaces its state wholesale from this;
 * countdowns tick client-side from the `endsAt` epoch-millis timestamps.
 *
 * Identities are described purely by `roleId` + localized `name` + `faction`, so the frontend never
 * hardcodes a role and future roles render with no UI change.
 */
@Schema(description = "Full game-state snapshot")
data class GameSnapshot(
    val guildId: String,
    val phase: String,
    val day: Int,
    val paused: Boolean,
    @get:Schema(description = "Epoch-millis the game was paused at (countdowns freeze here); null while running")
    val pausedAt: Long?,
    val started: Boolean,
    val doubleIdentity: Boolean,
    val muteAfterSpeech: Boolean,
    val witchSelfSave: Boolean,
    val hiddenWolfInheritsKnife: Boolean,
    @get:Schema(description = "Whether a dead player's identity is revealed in the public court death announcement")
    val revealRolesOnDeath: Boolean,
    val assigned: Boolean,
    val policeSeat: Int?,
    val aliveCount: Int,
    val totalSeats: Int,
    val winner: WinnerDto?,
    @get:Schema(description = "Whether the judge has revealed the result to the court (confirmed the win banner)")
    val winRevealed: Boolean,
    val timerEndsAt: Long?,
    @get:Schema(description = "Epoch-millis the double-identity order swap locks; null when not in the swap window")
    val orderLockEndsAt: Long?,
    val seats: List<SeatDto>,
    val meters: List<FactionMeterDto>,
    val speech: SpeechDto?,
    val poll: PollDto?,
    val night: NightDto?,
    @get:Schema(description = "Wolf-team chatter relayed from the seat channels, in send order — synced across every phase")
    val wolfChat: List<WolfChatDto>,
    val log: List<LogDto>,
    val pool: Map<String, Int>,
)

@Schema(description = "A seat and its identities")
data class SeatDto(
    val seat: Int,
    val label: String,
    val memberId: String?,
    val displayName: String?,
    val avatar: String?,
    val unassigned: Boolean,
    val alive: Boolean,
    val identities: List<IdentityDto>,
    val police: Boolean,
    val goldenBaby: Boolean,
    val clone: Boolean,
    val idiot: Boolean,
    val orderLocked: Boolean,
    // --- ROLES.md behavioural state surfaced for judge action buttons ---
    val revengePending: Boolean,
    val idiotRevealed: Boolean,
    val loverSeat: Int?,
    val charmedSeat: Int?,
    val learnedRoleId: String?,
    val knifeArmed: Boolean,
)

@Schema(description = "One identity card on a seat")
data class IdentityDto(
    val roleId: String,
    val name: String,
    val faction: String,
    val dead: Boolean,
)

@Schema(description = "Faction alive/total proportion (spectator meters)")
data class FactionMeterDto(
    val faction: String,
    val alive: Int,
    val total: Int,
)

@Schema(description = "Game result")
data class WinnerDto(
    val faction: String,
    val reason: String,
)

@Schema(description = "Live speech flow state")
data class SpeechDto(
    val active: Boolean,
    val waiting: Boolean,
    val direction: String?,
    val fromSeat: Int?,
    val speakerSeat: Int?,
    val endsAt: Long?,
    val order: List<Int>,
    val upcoming: List<Int>,
    @get:Schema(description = "Seats that have cast a 下台 (step-down) vote against the current speaker")
    val interruptVoters: List<Int>,
    @get:Schema(description = "Number of 下台 votes needed to force the current speaker off (alive majority)")
    val interruptThreshold: Int,
    @get:Schema(description = "Whether this is a last-words flow (still interruptible via the 下台 vote)")
    val lastWords: Boolean,
)

@Schema(description = "Live poll state (police election or expel vote)")
data class PollDto(
    val kind: String,
    val stage: String,
    val endsAt: Long?,
    val candidates: List<PollCandidateDto>,
    val eligibleVoters: Int,
    val votesCast: Int,
)

@Schema(description = "A poll candidate with its weighted tally and voters")
data class PollCandidateDto(
    val seat: Int,
    val withdrawn: Boolean,
    val weight: Double,
    val voters: List<Int>,
)

@Schema(description = "Night action board — abilities act in simultaneous waves")
data class NightDto(
    val active: Boolean,
    val day: Int,
    @get:Schema(description = "Deadline (epoch-millis) of the currently-active phase")
    val endsAt: Long?,
    @get:Schema(description = "Wave index of the phase currently being prompted (phases run sequentially)")
    val currentPhase: Int,
    val submittedCount: Int,
    val totalCount: Int,
    val resolved: Boolean,
    val summary: String?,
    val waves: List<NightWaveDto>,
)

@Schema(description = "One relayed wolf-chat line shown on the judge wolf-chat panel")
data class WolfChatDto(
    val seat: Int,
    @get:Schema(description = "Discord user id of the sender (string to avoid JS precision loss); the dashboard groups consecutive lines by this, not by seat")
    val userId: String,
    val author: String,
    val avatar: String?,
    val content: String,
    val at: Long,
)

@Schema(description = "One wave of simultaneous night actions")
data class NightWaveDto(
    val index: Int,
    val actions: List<NightActionDto>,
)

@Schema(description = "A pending or submitted night action")
data class NightActionDto(
    val abilityId: String,
    val roleId: String,
    val roleName: String,
    val faction: String,
    val actorSeats: List<Int>,
    val targetSeat: Int?,
    val status: String,
    @get:Schema(description = "Per-wolf vote breakdown for the collective knife (null for single-actor abilities)")
    val votes: List<NightVoteDto>? = null,
)

@Schema(description = "One wolf's knife vote within the collective kill")
data class NightVoteDto(
    val voter: Int,
    @get:Schema(description = "Target seat; null when not yet voted or when voting to skip")
    val target: Int?,
    @get:Schema(description = "True when this wolf voted not to kill tonight")
    val skip: Boolean,
)

@Schema(description = "A game log entry")
data class LogDto(
    val id: String,
    val timestamp: Long,
    val severity: String,
    val text: String,
)
