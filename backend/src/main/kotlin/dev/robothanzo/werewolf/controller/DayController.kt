package dev.robothanzo.werewolf.controller

import dev.robothanzo.werewolf.controller.dto.ApiResponse
import dev.robothanzo.werewolf.controller.dto.DirectionRequest
import dev.robothanzo.werewolf.game.speech.SpeechDirection
import dev.robothanzo.werewolf.security.annotations.CanManageGuild
import dev.robothanzo.werewolf.service.DayOrchestrator
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

/**
 * Judge controls for the day-side flow, mirroring the Discord court buttons so the dashboard and the
 * bot stay equivalent under snapshot-as-truth. Each action delegates to [DayOrchestrator] (which
 * `mutate`s + broadcasts) and returns the plain `ApiResponse.ok()` envelope; the real result reaches
 * the client as the broadcast snapshot.
 */
@RestController
@RequestMapping("/api/sessions/{guildId}")
@Tag(name = "Day", description = "Judge controls for speeches and polls (speech skip/stop/direction, poll advance/resolve)")
class DayController(
    private val day: DayOrchestrator,
) {

    @Operation(summary = "Skip current speaker", description = "End the current speaker's turn and advance to the next.")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "Advanced")])
    @PostMapping("/speech/skip")
    @CanManageGuild
    fun skipSpeaker(@PathVariable guildId: String): ResponseEntity<ApiResponse> {
        day.skipCurrentSpeaker(guildId.toLong())
        return ResponseEntity.ok(ApiResponse.ok())
    }

    @Operation(summary = "Stop speech flow", description = "Terminate the whole speech flow.")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "Stopped")])
    @PostMapping("/speech/stop")
    @CanManageGuild
    fun stopSpeech(@PathVariable guildId: String): ResponseEntity<ApiResponse> {
        day.stopSpeech(guildId.toLong())
        return ResponseEntity.ok(ApiResponse.ok())
    }

    @Operation(summary = "Choose speech direction", description = "Resolve the parked direction choice (UP/DOWN).")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "Chosen")])
    @PostMapping("/speech/direction")
    @CanManageGuild
    fun chooseDirection(
        @PathVariable guildId: String,
        @RequestBody body: DirectionRequest,
    ): ResponseEntity<ApiResponse> {
        val dir = if (body.direction.equals("UP", ignoreCase = true)) SpeechDirection.UP else SpeechDirection.DOWN
        day.chooseDirection(guildId.toLong(), dir)
        return ResponseEntity.ok(ApiResponse.ok())
    }

    @Operation(summary = "Advance poll stage", description = "Force the current poll stage to resolve / advance early.")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "Advanced")])
    @PostMapping("/poll/advance")
    @CanManageGuild
    fun advancePoll(@PathVariable guildId: String): ResponseEntity<ApiResponse> {
        day.resolvePollStage(guildId.toLong())
        return ResponseEntity.ok(ApiResponse.ok())
    }

    @Operation(summary = "Resolve poll now", description = "Force-resolve the current poll stage immediately.")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "Resolved")])
    @PostMapping("/poll/resolve")
    @CanManageGuild
    fun resolvePoll(@PathVariable guildId: String): ResponseEntity<ApiResponse> {
        day.resolvePollStage(guildId.toLong())
        return ResponseEntity.ok(ApiResponse.ok())
    }
}
