package dev.robothanzo.werewolf.controller

import dev.robothanzo.werewolf.controller.dto.SessionSummary
import dev.robothanzo.werewolf.controller.dto.SessionSummaryResponse
import dev.robothanzo.werewolf.controller.dto.SnapshotResponse
import dev.robothanzo.werewolf.discord.DiscordGateway
import dev.robothanzo.werewolf.domain.repo.GameSessionRepository
import dev.robothanzo.werewolf.security.CurrentUser
import dev.robothanzo.werewolf.security.DashboardRoleService
import dev.robothanzo.werewolf.security.annotations.CanViewGuild
import dev.robothanzo.werewolf.service.GameSessionService
import dev.robothanzo.werewolf.service.SnapshotService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@RestController
@RequestMapping("/api/sessions")
@Tag(name = "Sessions", description = "Listing the user's game servers and reading game state")
class SessionsController(
    private val sessions: GameSessionRepository,
    private val sessionService: GameSessionService,
    private val snapshots: SnapshotService,
    private val currentUser: CurrentUser,
    private val roleService: DashboardRoleService,
    private val discordGateway: DiscordGateway,
) {

    @Operation(summary = "List my game servers", description = "Game servers the logged-in user may view.")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "OK")])
    @GetMapping
    fun list(): ResponseEntity<SessionSummaryResponse> {
        val userId = currentUser.userId() ?: return ResponseEntity.ok(SessionSummaryResponse(emptyList()))
        val summaries = sessions.findAll()
            .filter { roleService.canView(it.guildId, userId) }
            .map {
                SessionSummary(
                    guildId = it.guildId.toString(),
                    guildName = discordGateway.getGuildName(it.guildId) ?: "狼人殺遊戲",
                    guildIcon = discordGateway.getGuildIconUrl(it.guildId),
                    playerCount = it.playerCount,
                )
            }
        return ResponseEntity.ok(SessionSummaryResponse(summaries))
    }

    @Operation(summary = "Get game state", description = "The full snapshot for a guild (snapshot-as-truth).")
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "OK"),
            SwaggerApiResponse(responseCode = "403", description = "Not permitted to view this guild"),
            SwaggerApiResponse(responseCode = "404", description = "Session not found"),
        ],
    )
    @GetMapping("/{guildId}/state")
    @CanViewGuild
    fun state(@PathVariable guildId: String): ResponseEntity<SnapshotResponse> {
        val session = sessionService.require(guildId.toLong())
        val snapshot = snapshots.build(session, sessionService.recentLogs(session.guildId))
        return ResponseEntity.ok(SnapshotResponse(snapshot))
    }
}
