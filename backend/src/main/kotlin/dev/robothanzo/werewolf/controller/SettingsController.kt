package dev.robothanzo.werewolf.controller

import dev.robothanzo.werewolf.controller.dto.ApiResponse
import dev.robothanzo.werewolf.controller.dto.PlayerCountRequest
import dev.robothanzo.werewolf.controller.dto.PoolRequest
import dev.robothanzo.werewolf.controller.dto.ToggleRequest
import dev.robothanzo.werewolf.discord.DiscordGateway
import dev.robothanzo.werewolf.domain.Seat
import dev.robothanzo.werewolf.security.annotations.CanManageGuild
import dev.robothanzo.werewolf.service.GameSessionService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import kotlinx.coroutines.runBlocking
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@RestController
@RequestMapping("/api/sessions/{guildId}/settings")
@Tag(name = "Settings", description = "Pre-game configuration (auto-saving)")
class SettingsController(
    private val sessionService: GameSessionService,
    private val gateway: DiscordGateway,
) {

    @Operation(summary = "Set player count", description = "Resize the seat list and provision/recycle Discord channels and roles.")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "Updated")])
    @PostMapping("/player-count")
    @CanManageGuild
    fun playerCount(@PathVariable guildId: String, @RequestBody body: PlayerCountRequest): ResponseEntity<ApiResponse> {
        sessionService.mutate(guildId.toLong()) { s ->
            runBlocking {
                gateway.resizeGuild(s, body.count)
            }
        }
        return ResponseEntity.ok(ApiResponse.ok())
    }

    @Operation(summary = "Set identity pool", description = "Replace the pool multiset (roleId → count).")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "Updated")])
    @PostMapping("/pool")
    @CanManageGuild
    fun pool(@PathVariable guildId: String, @RequestBody body: PoolRequest): ResponseEntity<ApiResponse> {
        sessionService.mutate(guildId.toLong()) { s -> s.pool = body.pool.filterValues { it > 0 }.toMutableMap() }
        return ResponseEntity.ok(ApiResponse.ok())
    }

    @Operation(summary = "Toggle double identity", description = "Enable/disable 雙身分 mode.")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "Updated")])
    @PostMapping("/double-identity")
    @CanManageGuild
    fun doubleIdentity(@PathVariable guildId: String, @RequestBody body: ToggleRequest): ResponseEntity<ApiResponse> {
        sessionService.mutate(guildId.toLong()) { s -> s.settings.doubleIdentity = body.value }
        return ResponseEntity.ok(ApiResponse.ok())
    }

    @Operation(summary = "Toggle mute after speech", description = "Enable/disable 發言後靜音.")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "Updated")])
    @PostMapping("/mute-after-speech")
    @CanManageGuild
    fun muteAfterSpeech(@PathVariable guildId: String, @RequestBody body: ToggleRequest): ResponseEntity<ApiResponse> {
        sessionService.mutate(guildId.toLong()) { s -> s.settings.muteAfterSpeech = body.value }
        return ResponseEntity.ok(ApiResponse.ok())
    }
}
