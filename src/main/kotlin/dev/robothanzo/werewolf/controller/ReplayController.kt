package dev.robothanzo.werewolf.controller

import dev.robothanzo.werewolf.controller.dto.ApiResponse
import dev.robothanzo.werewolf.controller.dto.ReplayResponse
import dev.robothanzo.werewolf.database.ReplayRepository
import dev.robothanzo.werewolf.utils.IdentityUtils
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@RestController
@RequestMapping("/api/replays")
@Tag(name = "Replay", description = "Game Replay Management")
class ReplayController(
    private val replayRepository: ReplayRepository,
    private val identityUtils: IdentityUtils
) {

    @GetMapping("/{sessionId}")
    @Operation(summary = "Get replay by session ID")
    @ApiResponses(
        value = [
            SwaggerApiResponse(
                responseCode = "200", description = "Replay found",
                content = [Content(schema = Schema(implementation = ReplayResponse::class))]
            ),
            SwaggerApiResponse(responseCode = "401", description = "Unauthorized"),
            SwaggerApiResponse(responseCode = "403", description = "Access denied"),
            SwaggerApiResponse(responseCode = "404", description = "Replay not found")
        ]
    )
    fun getReplay(@PathVariable sessionId: String): ResponseEntity<ApiResponse> {
        val user = identityUtils.getCurrentUser()
            .orElseThrow { ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated") }

        val userId = user.userId ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Discord ID not found")

        val replay = replayRepository.findBySessionId(sessionId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Replay not found") }

        // Access Control: Must be a player, judge, or spectator
        val isPlayer = replay.players.values.any { it.userId.toString() == userId }
        val isJudge = replay.judgeList.any { it.toString() == userId }
        val isSpectator = replay.spectatorList.any { it.toString() == userId }

        if (!isPlayer && !isJudge && !isSpectator) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied")
        }

        return ResponseEntity.ok(ReplayResponse(replay))
    }

    @DeleteMapping("/{sessionId}")
    @Operation(summary = "Delete replay (Judges only)")
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "Replay deleted successfully"),
            SwaggerApiResponse(responseCode = "401", description = "Unauthorized"),
            SwaggerApiResponse(responseCode = "403", description = "Access denied"),
            SwaggerApiResponse(responseCode = "404", description = "Replay not found")
        ]
    )
    fun deleteReplay(@PathVariable sessionId: String): ResponseEntity<ApiResponse> {
        val user = identityUtils.getCurrentUser()
            .orElseThrow { ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated") }
        val userId = user.userId ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Discord ID not found")

        val replay = replayRepository.findBySessionId(sessionId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Replay not found") }

        val isSessionJudge = replay.judgeList.any { it.toString() == userId }
        if (!isSessionJudge && !identityUtils.isJudge) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: Only judges can delete replays")
        }

        replayRepository.deleteBySessionId(sessionId)
        return ResponseEntity.ok(ApiResponse.ok(message = "Replay deleted"))
    }
}
