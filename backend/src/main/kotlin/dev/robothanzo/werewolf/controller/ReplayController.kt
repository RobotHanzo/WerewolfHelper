package dev.robothanzo.werewolf.controller

import dev.robothanzo.werewolf.controller.dto.ReplayListResponse
import dev.robothanzo.werewolf.controller.dto.ReplayResponse
import dev.robothanzo.werewolf.controller.dto.toReplayDto
import dev.robothanzo.werewolf.controller.dto.toSummary
import dev.robothanzo.werewolf.security.CurrentUser
import dev.robothanzo.werewolf.service.GameRecordingService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

/**
 * Replay ("復盤") listing + playback. These are **cross-guild** and authorized purely by
 * participation (a recording is visible iff the caller's id is in its `participantIds`) — not by the
 * live `@CanViewGuild` guard, which gates the *current* game and would lock out a player whose guild
 * has since started a new one. Recordings are immutable history, so past players may always review.
 */
@RestController
@RequestMapping("/api/replays")
@Tag(name = "Replays", description = "Listing and playing back recorded games the user took part in")
class ReplayController(
    private val currentUser: CurrentUser,
    private val recordingService: GameRecordingService,
) {

    @Operation(summary = "List my replays", description = "Every recorded game the logged-in user took part in (any role).")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "OK")])
    @GetMapping
    fun list(): ResponseEntity<ReplayListResponse> {
        val userId = currentUser.userId() ?: return ResponseEntity.ok(ReplayListResponse(emptyList()))
        return ResponseEntity.ok(ReplayListResponse(recordingService.listForUser(userId).map { it.toSummary() }))
    }

    @Operation(summary = "Get a replay", description = "The full recording, only if the user participated in it.")
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "OK"),
            SwaggerApiResponse(responseCode = "404", description = "Replay not found or user did not participate"),
        ],
    )
    @GetMapping("/{id}")
    fun get(@PathVariable id: String): ResponseEntity<ReplayResponse> {
        val userId = currentUser.userId() ?: return ResponseEntity.status(401).build()
        return ResponseEntity.ok(ReplayResponse(recordingService.requireForUser(id, userId).toReplayDto()))
    }
}
