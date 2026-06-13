package dev.robothanzo.werewolf.controller.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Kill a seat's identity")
data class KillRequest(
    @Schema(description = "Index of the identity card to kill (defaults to the first living one)")
    val identityIndex: Int? = null,
    @Schema(description = "Whether to run a last-words speech")
    val allowLastWords: Boolean = false,
)

@Schema(description = "Revive a seat or a single identity")
data class ReviveRequest(
    @Schema(description = "Index of the identity to revive; null revives the whole seat")
    val identityIndex: Int? = null,
)

@Schema(description = "Edit a seat's identities")
data class EditRequest(
    val roleIds: List<String>,
    val orderLocked: Boolean? = null,
)

@Schema(description = "Transfer the police badge")
data class PoliceTransferRequest(val fromSeat: Int, val toSeat: Int)

@Schema(description = "Force-assign the police badge")
data class ForcePoliceRequest(val seat: Int)

@Schema(description = "Change the player count")
data class PlayerCountRequest(val count: Int)

@Schema(description = "Replace the identity pool (roleId → count)")
data class PoolRequest(val pool: Map<String, Int>)

@Schema(description = "Toggle a boolean setting")
data class ToggleRequest(val value: Boolean)

@Schema(description = "Speech direction choice")
data class DirectionRequest(
    @Schema(description = "UP or DOWN", allowableValues = ["UP", "DOWN"])
    val direction: String,
)

@Schema(description = "Start a standalone timer")
data class TimerRequest(val seconds: Int)

@Schema(description = "Promote/demote a member's dashboard role")
data class DashboardRoleRequest(
    val userId: String,
    @Schema(allowableValues = ["JUDGE", "SPECTATOR", "BLOCKED"])
    val role: String,
)
