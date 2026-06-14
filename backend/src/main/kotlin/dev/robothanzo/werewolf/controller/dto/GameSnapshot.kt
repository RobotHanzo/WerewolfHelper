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
    val started: Boolean,
    val doubleIdentity: Boolean,
    val muteAfterSpeech: Boolean,
    val witchSelfSave: Boolean,
    val hiddenWolfInheritsKnife: Boolean,
    val assigned: Boolean,
    val policeSeat: Int?,
    val aliveCount: Int,
    val totalSeats: Int,
    val winner: WinnerDto?,
    val timerEndsAt: Long?,
    val seats: List<SeatDto>,
    val meters: List<FactionMeterDto>,
    val speech: SpeechDto?,
    val poll: PollDto?,
    val night: NightDto?,
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
    val endsAt: Long?,
    val submittedCount: Int,
    val totalCount: Int,
    val resolved: Boolean,
    val summary: String?,
    val waves: List<NightWaveDto>,
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
)

@Schema(description = "A game log entry")
data class LogDto(
    val id: String,
    val timestamp: Long,
    val severity: String,
    val text: String,
)
