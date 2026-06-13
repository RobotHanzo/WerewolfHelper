package dev.robothanzo.werewolf.controller

import dev.robothanzo.werewolf.controller.dto.ApiResponse
import dev.robothanzo.werewolf.controller.dto.EditRequest
import dev.robothanzo.werewolf.controller.dto.ForcePoliceRequest
import dev.robothanzo.werewolf.controller.dto.KillRequest
import dev.robothanzo.werewolf.controller.dto.PoliceTransferRequest
import dev.robothanzo.werewolf.controller.dto.ReviveRequest
import dev.robothanzo.werewolf.game.flow.GameFlowService
import dev.robothanzo.werewolf.controller.dto.TimerRequest
import dev.robothanzo.werewolf.discord.DiscordGateway
import dev.robothanzo.werewolf.discord.SoundCue
import dev.robothanzo.werewolf.domain.LogSeverity
import dev.robothanzo.werewolf.game.flow.GameScheduler
import dev.robothanzo.werewolf.domain.Phase
import dev.robothanzo.werewolf.security.annotations.CanManageGuild
import dev.robothanzo.werewolf.service.CourtAnnouncer
import dev.robothanzo.werewolf.service.DayOrchestrator
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
    private val day: DayOrchestrator,
    private val announcer: CourtAnnouncer,
    private val gateway: DiscordGateway,
    private val gameScheduler: GameScheduler,
) {

    /** Run the orchestrator that owns the phase we just entered (night actions / day flow). */
    private fun onPhaseEntered(guildId: Long, phase: Phase) {
        when (phase) {
            Phase.NIGHT -> night.startNight(guildId)
            Phase.DAWN -> day.enterDawn(guildId)
            Phase.POLICE_ELECTION -> day.startPoliceElection(guildId)
            Phase.SPEECHES -> day.startSpeeches(guildId)
            Phase.EXPEL_VOTE -> day.startExpelVote(guildId)
            else -> {}
        }
    }

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
            require(s.assigned) { "error.not_assigned" }
            val t = flow.start(); s.phase = t.phase; s.day = t.day; t.phase
        }
        onPhaseEntered(guildId.toLong(), entered)
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
        onPhaseEntered(guildId.toLong(), entered)
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
        val seconds = body.seconds
        val timerEndsAt = System.currentTimeMillis() + seconds * 1000L
        sessionService.mutate(guildId.toLong()) { s ->
            s.timerEndsAt = timerEndsAt
            sessionService.log(guildId.toLong(), LogSeverity.ACTION, "timer.start", seconds)
        }
        announcer.announce(guildId.toLong(), "timer.start", seconds)

        // Schedule final timer completion
        val delay = seconds * 1000L
        gameScheduler.schedule(guildId.toLong(), GameScheduler.TIMER, delay) {
            sessionService.mutate(guildId.toLong()) { s ->
                s.timerEndsAt = null
                sessionService.log(guildId.toLong(), LogSeverity.ALERT, "timer.ended")
            }
            gateway.playSound(guildId.toLong(), SoundCue.TIMER_ENDED)
            announcer.announce(guildId.toLong(), "timer.ended")
        }

        // Schedule 30-seconds-remaining warning if timer is > 30s
        if (seconds > 30) {
            val warnDelay = (seconds - 30) * 1000L
            gameScheduler.schedule(guildId.toLong(), "timer.warn", warnDelay) {
                gateway.playSound(guildId.toLong(), SoundCue.TIMER_THIRTY_SECONDS)
            }
        }

        return ResponseEntity.ok(ApiResponse.ok())
    }

    @Operation(summary = "Stop timer", description = "Stop/cancel the active countdown timer early.")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "Stopped")])
    @PostMapping("/timer/stop")
    @CanManageGuild
    fun stopTimer(@PathVariable guildId: String): ResponseEntity<ApiResponse> {
        sessionService.mutate(guildId.toLong()) { s ->
            s.timerEndsAt = null
            sessionService.log(guildId.toLong(), LogSeverity.ACTION, "timer.stopped")
        }
        announcer.announce(guildId.toLong(), "timer.stopped")
        gameScheduler.cancel(guildId.toLong(), GameScheduler.TIMER)
        gameScheduler.cancel(guildId.toLong(), "timer.warn")
        return ResponseEntity.ok(ApiResponse.ok())
    }
}
