package dev.robothanzo.werewolf.controller.dto

import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer
import dev.robothanzo.werewolf.database.documents.AuthSession
import dev.robothanzo.werewolf.database.documents.Replay
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.GameStateData
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Generic API Response Wrapper")
open class ApiResponse(
    @Schema(description = "Whether the request was successful")
    open val success: Boolean,

    @Schema(description = "Response message (usually for success feedback)")
    open val message: String? = null,

    @Schema(description = "Error message (if success is false)")
    open val error: String? = null
) {
    companion object {
        fun ok(message: String? = null): ApiResponse {
            return ApiResponse(true, message, null)
        }

        fun error(error: String): ApiResponse {
            return ApiResponse(false, null, error)
        }
    }
}

data class SessionResponse(
    val data: Session
) : ApiResponse(true, null, null)

data class ReplayResponse(
    val data: Replay
) : ApiResponse(true, null, null)

data class ReplayListResponse(
    val data: List<Replay>
) : ApiResponse(true, null, null)

data class SessionSummary(
    val guildId: String,
    val guildName: String,
    val guildIcon: String,
    val playerCount: Int
)

data class SessionSummaryResponse(
    val data: List<SessionSummary>
) : ApiResponse(true, null, null)

data class GameStateDto(
    val currentState: String,
    val currentStep: String?,
    val day: Int,
    val stateData: GameStateData?
)

data class GameStateResponse(
    val data: GameStateDto
) : ApiResponse(true, null, null)

data class StateActionResponse(
    val data: Any?
) : ApiResponse(true, null, null)

data class RolesResponse(
    val data: List<String>
) : ApiResponse(true, null, null)

data class GuildMemberDto(
    val id: String,
    val name: String,
    val avatar: String?,
    val display: String,
    val roles: List<String> = emptyList()
)

data class GuildMembersResponse(
    val data: List<GuildMemberDto>
) : ApiResponse(true, null, null)

data class AuthData(
    val user: AuthSession,
    val username: String? = null,
    val avatar: String? = null
)

data class AuthResponse(
    val data: AuthData
) : ApiResponse(true, null, null)

data class BasicUserDto(
    @get:JsonSerialize(using = ToStringSerializer::class)
    @get:Schema(type = "string")
    val id: String,
    val name: String,
    val avatar: String?
)

data class BasicUserResponse(
    val data: BasicUserDto
) : ApiResponse(true, null, null)
