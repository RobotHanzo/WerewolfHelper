package dev.robothanzo.werewolf.controller

import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.model.SpeechOrder
import dev.robothanzo.werewolf.security.annotations.CanManageGuild
import dev.robothanzo.werewolf.service.GameSessionService
import dev.robothanzo.werewolf.service.PoliceService
import dev.robothanzo.werewolf.service.SpeechService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/sessions/{guildId}/speech")
class SpeechController(
    private val gameSessionService: GameSessionService,
    private val speechService: SpeechService,
    private val policeService: PoliceService // Injected to replace static access
) {

    @PostMapping("/auto")
    @CanManageGuild
    fun startAutoSpeech(@PathVariable guildId: Long): ResponseEntity<*> {
        val sessionOpt = gameSessionService.getSession(guildId)
        if (sessionOpt.isEmpty)
            return ResponseEntity.notFound().build<Any>()
        val session = sessionOpt.get()

        WerewolfApplication.jda.getGuildById(guildId) ?: return ResponseEntity.notFound().build<Any>()
        val channel = session.courtTextChannel

        if (channel != null) {
            speechService.startAutoSpeechFlow(session, channel.idLong)
        }
        gameSessionService.broadcastUpdate(guildId)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @PostMapping("/skip")
    @CanManageGuild
    fun skipSpeech(@PathVariable guildId: Long): ResponseEntity<*> {
        speechService.skipToNext(guildId)
        gameSessionService.broadcastUpdate(guildId)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @PostMapping("/interrupt")
    @CanManageGuild
    fun interruptSpeech(@PathVariable guildId: Long): ResponseEntity<*> {
        speechService.interruptSession(guildId)
        gameSessionService.broadcastUpdate(guildId)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @PostMapping("/police-enroll")
    @CanManageGuild
    fun startPoliceEnroll(@PathVariable guildId: Long): ResponseEntity<*> {
        val sessionOpt = gameSessionService.getSession(guildId)
        if (sessionOpt.isEmpty)
            return ResponseEntity.notFound().build<Any>()
        val session = sessionOpt.get()

        WerewolfApplication.jda.getGuildById(guildId) ?: return ResponseEntity.notFound().build<Any>()
        val channel = session.courtTextChannel

        if (channel != null) {
            policeService.startEnrollment(session, channel, null) // Injected access
        }
        gameSessionService.broadcastUpdate(guildId)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @PostMapping("/order")
    @CanManageGuild
    fun setSpeechOrder(
        @PathVariable guildId: Long,
        @RequestBody body: Map<String, String>
    ): ResponseEntity<*> {
        val direction = body["direction"] ?: return ResponseEntity.badRequest().build<Any>()
        val order = SpeechOrder.valueOf(direction.uppercase())

        val session = gameSessionService.getSession(guildId)
            .orElseThrow { Exception("Session not found") }

        speechService.setSpeechOrder(session, order)
        speechService.confirmSpeechOrder(session)

        gameSessionService.broadcastUpdate(guildId)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @PostMapping("/confirm")
    @CanManageGuild
    fun confirmSpeech(@PathVariable guildId: Long): ResponseEntity<*> {
        val session = gameSessionService.getSession(guildId)
            .orElseThrow { Exception("Session not found") }
        speechService.confirmSpeechOrder(session)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @PostMapping("/manual-start")
    @CanManageGuild
    fun manualStartTimer(
        @PathVariable guildId: Long,
        @RequestBody body: Map<String, Int>
    ): ResponseEntity<*> {
        val duration = body["duration"] ?: return ResponseEntity.badRequest().build<Any>()

        val sessionOpt = gameSessionService.getSession(guildId)
        if (sessionOpt.isEmpty)
            return ResponseEntity.notFound().build<Any>()
        val session = sessionOpt.get()

        WerewolfApplication.jda.getGuildById(guildId) ?: return ResponseEntity.notFound().build<Any>()
        val channel = session.courtTextChannel
        val voiceChannel = session.courtVoiceChannel

        if (channel != null) {
            speechService.startTimer(
                guildId, channel.idLong,
                voiceChannel?.idLong ?: 0,
                duration
            )
        }
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @PostMapping("/mute-all")
    @CanManageGuild
    fun muteAll(@PathVariable guildId: Long): ResponseEntity<*> {
        speechService.setAllMute(guildId, true)
        gameSessionService.broadcastUpdate(guildId)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @PostMapping("/unmute-all")
    @CanManageGuild
    fun unmuteAll(@PathVariable guildId: Long): ResponseEntity<*> {
        speechService.setAllMute(guildId, false)
        gameSessionService.broadcastUpdate(guildId)
        return ResponseEntity.ok(mapOf("success" to true))
    }
}
