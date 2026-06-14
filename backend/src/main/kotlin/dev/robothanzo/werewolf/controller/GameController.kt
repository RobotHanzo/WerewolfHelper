package dev.robothanzo.werewolf.controller

import dev.robothanzo.werewolf.controller.dto.ApiResponse
import dev.robothanzo.werewolf.controller.dto.EditRequest
import dev.robothanzo.werewolf.controller.dto.ForcePoliceRequest
import dev.robothanzo.werewolf.controller.dto.KillRequest
import dev.robothanzo.werewolf.controller.dto.PoliceTransferRequest
import dev.robothanzo.werewolf.controller.dto.ReviveRequest
import dev.robothanzo.werewolf.controller.dto.TargetRequest
import dev.robothanzo.werewolf.controller.dto.TimerRequest
import dev.robothanzo.werewolf.discord.DiscordGateway
import dev.robothanzo.werewolf.domain.Phase
import dev.robothanzo.werewolf.security.annotations.CanManageGuild
import dev.robothanzo.werewolf.service.DayOrchestrator
import dev.robothanzo.werewolf.service.GameActionService
import dev.robothanzo.werewolf.service.GameFlowCoordinator
import dev.robothanzo.werewolf.service.TimerService
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
    private val coordinator: GameFlowCoordinator,
    private val day: DayOrchestrator,
    private val gateway: DiscordGateway,
    private val timer: TimerService,
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

    @Operation(summary = "Fire a revenge shot", description = "Fire an armed 獵人 / 狼王 / 白狼王 revenge at a target.")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "Fired")])
    @PostMapping("/seats/{seat}/revenge")
    @CanManageGuild
    fun revenge(
        @PathVariable guildId: String,
        @PathVariable seat: Int,
        @RequestBody body: TargetRequest,
    ): ResponseEntity<ApiResponse> {
        actions.revenge(guildId.toLong(), seat, body.target)
        return ResponseEntity.ok(ApiResponse.ok())
    }

    @Operation(summary = "騎士 決鬥", description = "Knight duels a seat; a wolf hit enters night, a miss kills the knight.")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "Resolved")])
    @PostMapping("/seats/{seat}/duel")
    @CanManageGuild
    fun duel(
        @PathVariable guildId: String,
        @PathVariable seat: Int,
        @RequestBody body: TargetRequest,
    ): ResponseEntity<ApiResponse> {
        if (day.knightDuel(guildId.toLong(), seat, body.target)) coordinator.enterPhase(guildId.toLong(), Phase.NIGHT)
        return ResponseEntity.ok(ApiResponse.ok())
    }

    @Operation(summary = "自爆", description = "A wolf self-destructs, forcing night (白狼王 may then带人, 血月使徒 seals the night).")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "Detonated")])
    @PostMapping("/seats/{seat}/self-destruct")
    @CanManageGuild
    fun selfDestruct(
        @PathVariable guildId: String,
        @PathVariable seat: Int,
    ): ResponseEntity<ApiResponse> {
        if (day.selfDestruct(guildId.toLong(), seat)) coordinator.enterPhase(guildId.toLong(), Phase.NIGHT)
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
        coordinator.start(guildId.toLong())
        return ResponseEntity.ok(ApiResponse.ok())
    }

    @Operation(summary = "Next phase", description = "Advance the day/night phase machine.")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "Advanced")])
    @PostMapping("/state/next")
    @CanManageGuild
    fun next(@PathVariable guildId: String): ResponseEntity<ApiResponse> {
        coordinator.advance(guildId.toLong())
        return ResponseEntity.ok(ApiResponse.ok())
    }

    @Operation(
        summary = "Confirm win",
        description = "Reveal the game result to the court channel, unmute everyone, and open all channels for viewing.",
    )
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "Revealed")])
    @PostMapping("/state/confirm-win")
    @CanManageGuild
    fun confirmWin(@PathVariable guildId: String): ResponseEntity<ApiResponse> {
        actions.confirmWin(guildId.toLong())
        return ResponseEntity.ok(ApiResponse.ok())
    }

    @Operation(summary = "Pause / resume", description = "Freeze (or un-freeze) every running countdown.")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "Toggled")])
    @PostMapping("/state/pause")
    @CanManageGuild
    fun pause(@PathVariable guildId: String): ResponseEntity<ApiResponse> {
        coordinator.togglePause(guildId.toLong())
        return ResponseEntity.ok(ApiResponse.ok())
    }

    @Operation(summary = "Mute all members", description = "Mute everyone in the guild's voice channel.")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "Muted")])
    @PostMapping("/voice/mute")
    @CanManageGuild
    fun muteAll(@PathVariable guildId: String): ResponseEntity<ApiResponse> {
        gateway.muteAll(guildId.toLong())
        return ResponseEntity.ok(ApiResponse.ok())
    }

    @Operation(summary = "Unmute all members", description = "Unmute everyone in the guild's voice channel.")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "Unmuted")])
    @PostMapping("/voice/unmute")
    @CanManageGuild
    fun unmuteAll(@PathVariable guildId: String): ResponseEntity<ApiResponse> {
        gateway.unmuteAll(guildId.toLong())
        return ResponseEntity.ok(ApiResponse.ok())
    }

    @Operation(summary = "Start timer", description = "Start a public countdown timer.")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "Started")])
    @PostMapping("/timer/start")
    @CanManageGuild
    fun startTimer(
        @PathVariable guildId: String,
        @RequestBody body: TimerRequest,
    ): ResponseEntity<ApiResponse> {
        timer.start(guildId.toLong(), body.seconds)
        return ResponseEntity.ok(ApiResponse.ok())
    }

    @Operation(summary = "Stop timer", description = "Stop/cancel the active countdown timer early.")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "Stopped")])
    @PostMapping("/timer/stop")
    @CanManageGuild
    fun stopTimer(@PathVariable guildId: String): ResponseEntity<ApiResponse> {
        timer.stop(guildId.toLong())
        return ResponseEntity.ok(ApiResponse.ok())
    }
}
