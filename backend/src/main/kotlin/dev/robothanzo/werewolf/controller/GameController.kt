package dev.robothanzo.werewolf.controller

import dev.robothanzo.werewolf.controller.dto.ApiResponse
import dev.robothanzo.werewolf.controller.dto.EditRequest
import dev.robothanzo.werewolf.controller.dto.ForcePoliceRequest
import dev.robothanzo.werewolf.controller.dto.KillRequest
import dev.robothanzo.werewolf.controller.dto.PoliceTransferRequest
import dev.robothanzo.werewolf.controller.dto.ReviveRequest
import dev.robothanzo.werewolf.game.flow.GameFlowService
import dev.robothanzo.werewolf.domain.Phase
import dev.robothanzo.werewolf.security.annotations.CanManageGuild
import dev.robothanzo.werewolf.service.GameActionService
import dev.robothanzo.werewolf.service.GameSessionService
import dev.robothanzo.werewolf.service.NightOrchestrator
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

@RestController
@RequestMapping("/api/sessions/{guildId}")
@Tag(name = "Game", description = "Judge actions on the running game (all mutate then broadcast a snapshot)")
class GameController(
    private val actions: GameActionService,
    private val sessionService: GameSessionService,
    private val flow: GameFlowService,
    private val night: NightOrchestrator,
) {

    @Operation(summary = "Assign identities", description = "Deal the identity pool to the eligible members.")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "Assigned")])
    @PostMapping("/assign")
    @CanManageGuild
    fun assign(@PathVariable guildId: String): ResponseEntity<ApiResponse> {
        actions.assign(guildId.toLong())
        return ResponseEntity.ok(ApiResponse.ok())
    }

    @Operation(summary = "Reset game", description = "Return the server to the pre-assignment state and clear logs.")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "Reset")])
    @PostMapping("/reset")
    @CanManageGuild
    fun reset(@PathVariable guildId: String): ResponseEntity<ApiResponse> {
        actions.reset(guildId.toLong())
        return ResponseEntity.ok(ApiResponse.ok())
    }

    @Operation(summary = "Kill a seat's identity", description = "Soft-death one identity, then re-check win conditions.")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "Killed")])
    @PostMapping("/seats/{seat}/kill")
    @CanManageGuild
    fun kill(
        @PathVariable guildId: String,
        @PathVariable seat: Int,
        @RequestBody body: KillRequest,
    ): ResponseEntity<ApiResponse> {
        actions.kill(guildId.toLong(), seat, body.identityIndex, body.allowLastWords)
        return ResponseEntity.ok(ApiResponse.ok())
    }

    @Operation(summary = "Revive", description = "Revive a whole seat or a single dead identity.")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "Revived")])
    @PostMapping("/seats/{seat}/revive")
    @CanManageGuild
    fun revive(
        @PathVariable guildId: String,
        @PathVariable seat: Int,
        @RequestBody body: ReviveRequest,
    ): ResponseEntity<ApiResponse> {
        actions.revive(guildId.toLong(), seat, body.identityIndex)
        return ResponseEntity.ok(ApiResponse.ok())
    }

    @Operation(summary = "Edit identities", description = "Replace a seat's identities and toggle the order lock.")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "Edited")])
    @PostMapping("/seats/{seat}/edit")
    @CanManageGuild
    fun edit(
        @PathVariable guildId: String,
        @PathVariable seat: Int,
        @RequestBody body: EditRequest,
    ): ResponseEntity<ApiResponse> {
        actions.editIdentities(guildId.toLong(), seat, body.roleIds, body.orderLocked)
        return ResponseEntity.ok(ApiResponse.ok())
    }

    @Operation(summary = "Force police", description = "Force-assign the police badge to a seat.")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "Assigned")])
    @PostMapping("/police/force")
    @CanManageGuild
    fun forcePolice(
        @PathVariable guildId: String,
        @RequestBody body: ForcePoliceRequest,
    ): ResponseEntity<ApiResponse> {
        actions.setPolice(guildId.toLong(), body.seat)
        return ResponseEntity.ok(ApiResponse.ok())
    }

    @Operation(summary = "Transfer police", description = "Move the police badge between two seats.")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "Transferred")])
    @PostMapping("/police/transfer")
    @CanManageGuild
    fun transferPolice(
        @PathVariable guildId: String,
        @RequestBody body: PoliceTransferRequest,
    ): ResponseEntity<ApiResponse> {
        actions.transferPolice(guildId.toLong(), body.fromSeat, body.toSeat)
        return ResponseEntity.ok(ApiResponse.ok())
    }

    @Operation(summary = "Start game", description = "Leave the lobby and enter the first night.")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "Started")])
    @PostMapping("/state/start")
    @CanManageGuild
    fun start(@PathVariable guildId: String): ResponseEntity<ApiResponse> {
        val entered = sessionService.mutate(guildId.toLong()) { s ->
            val t = flow.start(); s.phase = t.phase; s.day = t.day; t.phase
        }
        if (entered == Phase.NIGHT) night.startNight(guildId.toLong())
        return ResponseEntity.ok(ApiResponse.ok())
    }

    @Operation(summary = "Next phase", description = "Advance the day/night phase machine.")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "Advanced")])
    @PostMapping("/state/next")
    @CanManageGuild
    fun next(@PathVariable guildId: String): ResponseEntity<ApiResponse> {
        val entered = sessionService.mutate(guildId.toLong()) { s ->
            val t = flow.next(s.phase, s.day); s.phase = t.phase; s.day = t.day; t.phase
        }
        if (entered == Phase.NIGHT) night.startNight(guildId.toLong())
        return ResponseEntity.ok(ApiResponse.ok())
    }

    @Operation(summary = "Pause / resume", description = "Toggle the paused flag.")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "Toggled")])
    @PostMapping("/state/pause")
    @CanManageGuild
    fun pause(@PathVariable guildId: String): ResponseEntity<ApiResponse> {
        sessionService.mutate(guildId.toLong()) { s -> s.paused = !s.paused }
        return ResponseEntity.ok(ApiResponse.ok())
    }
}
