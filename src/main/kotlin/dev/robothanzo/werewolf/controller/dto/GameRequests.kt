package dev.robothanzo.werewolf.controller.dto

import io.swagger.v3.oas.annotations.media.Schema

class GameRequests {
    @Schema(description = "Request to set the game state")
    data class StateSetRequest(
        @Schema(description = "The ID/Name of the step to set", example = "NIGHT_PHASE")
        val stepId: String
    )

    @Schema(description = "Request to execute a specific action within the current state")
    data class StateActionRequest(
        @Schema(description = "The type of action", example = "VOTE")
        val action: String,
        @Schema(description = "Additional data for the action")
        val data: Map<String, Any>? = null
    )

    @Schema(description = "Request to update roles for a specific player")
    data class PlayerRolesUpdateRequest(
        @Schema(description = "List of role names", example = "[\"WEREWOLF\", \"VILLAGER\"]")
        val roles: List<String>
    )

    @Schema(description = "Request to update a user's permission level/role")
    data class PlayerRoleUpdateRequest(
        @Schema(description = "The new role/permission", example = "JUDGE")
        val role: String
    )

    @Schema(description = "Request to set the number of players")
    data class PlayerCountRequest(
        @Schema(description = "The number of players", example = "10")
        val count: Int
    )

    @Schema(description = "Request to add a role to the game configuration")
    data class AddRoleRequest(
        @Schema(description = "Name of the role", example = "SEER")
        val role: String,
        @Schema(description = "Amount to add", example = "1")
        val amount: Int = 1
    )

    @Schema(description = "Request to update game settings")
    data class UpdateSettingsRequest(
        @Schema(description = "Game settings key-value pairs")
        val settings: Map<String, Any>
    )

    @Schema(description = "Request to set the speech order")
    data class SpeechOrderRequest(
        @Schema(description = "Direction of speech", example = "CLOCKWISE")
        val direction: String
    )

    @Schema(description = "Request to manually start a timer")
    data class ManualTimerRequest(
        @Schema(description = "Duration in seconds", example = "60")
        val duration: Int
    )
}
