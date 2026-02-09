package dev.robothanzo.werewolf.controller

import dev.robothanzo.werewolf.controller.dto.ApiResponse
import dev.robothanzo.werewolf.controller.dto.SessionResponse
import dev.robothanzo.werewolf.controller.dto.SessionSummaryResponse
import dev.robothanzo.werewolf.security.annotations.CanViewGuild
import dev.robothanzo.werewolf.service.GameSessionService
import dev.robothanzo.werewolf.utils.IdentityUtils
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@RestController
@RequestMapping("/api/sessions")
@Tag(name = "Session Management", description = "Endpoints for retrieving game session information")
class SessionController(
    private val gameSessionService: GameSessionService,
    private val identityUtils: IdentityUtils
) {

    @Operation(
        summary = "Get All Sessions",
        description = "Retrieves a summary of all game sessions the user has access to."
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(
                responseCode = "200", description = "Successfully retrieved sessions",
                content = [Content(schema = Schema(implementation = SessionSummaryResponse::class))]
            ),
            SwaggerApiResponse(responseCode = "401", description = "User not authenticated")
        ]
    )
    @GetMapping
    fun getAllSessions(): ResponseEntity<ApiResponse> {
        val userOpt = identityUtils.getCurrentUser()
        if (userOpt.isEmpty) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }
        val user = userOpt.get()

        val data = gameSessionService.getAllSessions().filter { session ->
            // Check if user is in guild
            user.userId?.let { session.guild?.getMemberById(it) } != null
        }.map { it.generateSummary() }

        return ResponseEntity.ok(SessionSummaryResponse(data))
    }

    @Operation(
        summary = "Get Session Details",
        description = "Retrieves detailed information about a specific game session."
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(
                responseCode = "200", description = "Successfully retrieved session details", content = [
                    Content(schema = Schema(implementation = SessionResponse::class))
                ]
            ),
            SwaggerApiResponse(responseCode = "404", description = "Session not found"),
            SwaggerApiResponse(responseCode = "403", description = "User does not have permission to view this guild")
        ]
    )
    @GetMapping("/{guildId}")
    @CanViewGuild
    fun getSession(
        @Parameter(description = "ID of the guild/session")
        @PathVariable guildId: Long
    ): ResponseEntity<ApiResponse> {
        val sessionOpt = gameSessionService.getSession(guildId)

        return if (sessionOpt.isPresent) {
            val session = sessionOpt.get()
            ResponseEntity.ok(SessionResponse(session))
        } else {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Session not found"))
        }
    }
}
