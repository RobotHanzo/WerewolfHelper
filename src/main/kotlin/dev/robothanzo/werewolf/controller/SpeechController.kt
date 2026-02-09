package dev.robothanzo.werewolf.controller

import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.controller.dto.ApiResponse
import dev.robothanzo.werewolf.controller.dto.GameRequests
import dev.robothanzo.werewolf.model.SpeechOrder
import dev.robothanzo.werewolf.security.annotations.CanManageGuild
import dev.robothanzo.werewolf.service.GameSessionService
import dev.robothanzo.werewolf.service.PoliceService
import dev.robothanzo.werewolf.service.SpeechService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@RestController
@RequestMapping("/api/sessions/{guildId}/speech")
@Tag(name = "Speech & Audio", description = "Endpoints for managing speech flows, timers, and audio controls")
class SpeechController(
    private val gameSessionService: GameSessionService,
    private val speechService: SpeechService,
    private val policeService: PoliceService // Injected to replace static access
) {

    @Operation(summary = "Start Auto Speech", description = "Initiates the automated speech flow for the game.")
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "Auto speech started successfully"),
            SwaggerApiResponse(responseCode = "404", description = "Session or Channel not found"),
            SwaggerApiResponse(responseCode = "403", description = "User does not have permission to manage this guild")
        ]
    )
    @PostMapping("/auto")
    @CanManageGuild
    fun startAutoSpeech(@PathVariable guildId: String): ResponseEntity<ApiResponse> {
        val sessionOpt = gameSessionService.getSession(guildId.toLong())
        if (sessionOpt.isEmpty)
            return ResponseEntity.notFound().build()
        val session = sessionOpt.get()

        WerewolfApplication.jda.getGuildById(guildId.toLong()) ?: return ResponseEntity.notFound().build()
        val channel = session.courtTextChannel

        if (channel != null) {
            speechService.startAutoSpeechFlow(session, channel.idLong)
        }
        gameSessionService.broadcastUpdate(guildId.toLong())
        return ResponseEntity.ok(ApiResponse.ok(message = "Auto speech started"))
    }

    @Operation(summary = "Skip Current Speech", description = "Skips the current speaker and moves to the next one.")
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "Skipped current speaker successfully"),
            SwaggerApiResponse(responseCode = "403", description = "User does not have permission to manage this guild")
        ]
    )
    @PostMapping("/skip")
    @CanManageGuild
    fun skipSpeech(@PathVariable guildId: String): ResponseEntity<ApiResponse> {
        speechService.skipToNext(guildId.toLong())
        gameSessionService.broadcastUpdate(guildId.toLong())
        return ResponseEntity.ok(ApiResponse.ok(message = "Skipped current speaker"))
    }

    @Operation(summary = "Interrupt Speech", description = "Interrupts the current speech flow.")
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "Speech interrupted successfully"),
            SwaggerApiResponse(responseCode = "403", description = "User does not have permission to manage this guild")
        ]
    )
    @PostMapping("/interrupt")
    @CanManageGuild
    fun interruptSpeech(@PathVariable guildId: String): ResponseEntity<ApiResponse> {
        speechService.interruptSession(guildId.toLong())
        gameSessionService.broadcastUpdate(guildId.toLong())
        return ResponseEntity.ok(ApiResponse.ok(message = "Speech interrupted"))
    }

    @Operation(
        summary = "Start Police Enrollment",
        description = "Starts the process for electing the Sheriff (Police)."
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "Police enrollment started successfully"),
            SwaggerApiResponse(responseCode = "404", description = "Session or Channel not found"),
            SwaggerApiResponse(responseCode = "403", description = "User does not have permission to manage this guild")
        ]
    )
    @PostMapping("/police-enroll")
    @CanManageGuild
    fun startPoliceEnroll(@PathVariable guildId: String): ResponseEntity<ApiResponse> {
        val sessionOpt = gameSessionService.getSession(guildId.toLong())
        if (sessionOpt.isEmpty)
            return ResponseEntity.notFound().build()
        val session = sessionOpt.get()

        WerewolfApplication.jda.getGuildById(guildId.toLong()) ?: return ResponseEntity.notFound().build()
        val channel = session.courtTextChannel

        if (channel != null) {
            policeService.startEnrollment(session, channel, null) // Injected access
        }
        gameSessionService.broadcastUpdate(guildId.toLong())
        return ResponseEntity.ok(ApiResponse.ok(message = "Police enrollment started"))
    }

    @Operation(
        summary = "Set Speech Order",
        description = "Sets the order in which players will speak (e.g., CLOCKWISE, COUNTER_CLOCKWISE)."
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "Speech order set successfully"),
            SwaggerApiResponse(responseCode = "400", description = "Invalid speech order"),
            SwaggerApiResponse(responseCode = "403", description = "User does not have permission to manage this guild")
        ]
    )
    @PostMapping("/order")
    @CanManageGuild
    fun setSpeechOrder(
        @PathVariable guildId: String,
        @RequestBody body: GameRequests.SpeechOrderRequest
    ): ResponseEntity<ApiResponse> {
        val direction = body.direction
        val order = try {
            SpeechOrder.valueOf(direction.uppercase())
        } catch (e: IllegalArgumentException) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Invalid speech order: $direction"))
        }

        val session = gameSessionService.getSession(guildId.toLong())
            .orElseThrow { Exception("Session not found") }

        speechService.setSpeechOrder(session, order)
        speechService.confirmSpeechOrder(session)

        gameSessionService.broadcastUpdate(guildId.toLong())
        return ResponseEntity.ok(ApiResponse.ok(message = "Speech order set to $order"))
    }

    @Operation(summary = "Confirm Speech Order", description = "Confirms the set speech order and proceeds.")
    @PostMapping("/confirm")
    @CanManageGuild
    fun confirmSpeech(@PathVariable guildId: String): ResponseEntity<ApiResponse> {
        val session = gameSessionService.getSession(guildId.toLong())
            .orElseThrow { Exception("Session not found") }
        speechService.confirmSpeechOrder(session)
        return ResponseEntity.ok(ApiResponse.ok(message = "Speech order confirmed"))
    }

    @Operation(summary = "Manual Start Timer", description = "Manually starts a speech timer for a specific duration.")
    @PostMapping("/manual-start")
    @CanManageGuild
    fun manualStartTimer(
        @PathVariable guildId: String,
        @RequestBody body: GameRequests.ManualTimerRequest
    ): ResponseEntity<ApiResponse> {
        val duration = body.duration

        val sessionOpt = gameSessionService.getSession(guildId.toLong())
        if (sessionOpt.isEmpty)
            return ResponseEntity.notFound().build()
        val session = sessionOpt.get()

        WerewolfApplication.jda.getGuildById(guildId.toLong()) ?: return ResponseEntity.notFound().build()
        val channel = session.courtTextChannel
        val voiceChannel = session.courtVoiceChannel

        if (channel != null) {
            speechService.startTimer(
                guildId.toLong(), channel.idLong,
                voiceChannel?.idLong ?: 0,
                duration
            )
        }
        return ResponseEntity.ok(ApiResponse.ok(message = "Timer started for $duration seconds"))
    }

    @Operation(summary = "Mute All", description = "Mutes all players in the voice channel.")
    @PostMapping("/mute-all")
    @CanManageGuild
    fun muteAll(@PathVariable guildId: String): ResponseEntity<ApiResponse> {
        speechService.setAllMute(guildId.toLong(), true)
        gameSessionService.broadcastUpdate(guildId.toLong())
        return ResponseEntity.ok(ApiResponse.ok(message = "All players muted"))
    }

    @Operation(summary = "Unmute All", description = "Unmutes all players in the voice channel.")
    @PostMapping("/unmute-all")
    @CanManageGuild
    fun unmuteAll(@PathVariable guildId: String): ResponseEntity<ApiResponse> {
        speechService.setAllMute(guildId.toLong(), false)
        gameSessionService.broadcastUpdate(guildId.toLong())
        return ResponseEntity.ok(ApiResponse.ok(message = "All players unmuted"))
    }
}
