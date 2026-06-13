package dev.robothanzo.werewolf.controller.dto

import io.swagger.v3.oas.annotations.media.Schema

/**
 * Generic API response envelope (mirrors the proven auto-branch style): `success` plus an optional
 * `message`/`error`. Endpoints returning data use a typed subclass carrying a `data` field so the
 * OpenAPI schema — and the hand-written frontend types — stay precise.
 */
@Schema(description = "Generic API response wrapper")
open class ApiResponse(
    @Schema(description = "Whether the request succeeded")
    open val success: Boolean,
    @Schema(description = "Success feedback message")
    open val message: String? = null,
    @Schema(description = "Error message when success is false")
    open val error: String? = null,
) {
    companion object {
        fun ok(message: String? = null): ApiResponse = ApiResponse(true, message, null)
        fun error(error: String): ApiResponse = ApiResponse(false, null, error)
    }
}

@Schema(description = "Response carrying the full game-state snapshot")
data class SnapshotResponse(val data: GameSnapshot) : ApiResponse(true)

@Schema(description = "Response carrying the list of the user's game servers")
data class SessionSummaryResponse(val data: List<SessionSummary>) : ApiResponse(true)

@Schema(description = "Response carrying the canonical role registry")
data class RolesResponse(val data: List<RoleDto>) : ApiResponse(true)

@Schema(description = "Response carrying guild members (for pickers)")
data class MembersResponse(val data: List<MemberDto>) : ApiResponse(true)

@Schema(description = "Response carrying the authenticated user and their dashboard role")
data class AuthResponse(val data: AuthInfo) : ApiResponse(true)

@Schema(description = "A game server the user can open")
data class SessionSummary(
    val guildId: String,
    val guildName: String,
    val guildIcon: String?,
    val playerCount: Int,
)

@Schema(description = "A canonical identity")
data class RoleDto(
    val id: String,
    val name: String,
    val faction: String,
)

@Schema(description = "A guild member")
data class MemberDto(
    val id: String,
    val name: String,
    val displayName: String,
    val avatar: String?,
)

@Schema(description = "Authenticated user + dashboard role on the selected guild")
data class AuthInfo(
    val userId: String,
    val username: String,
    val avatar: String?,
    val role: String,
    val guildId: String?,
)
