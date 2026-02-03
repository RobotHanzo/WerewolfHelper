package dev.robothanzo.werewolf.controller

import dev.robothanzo.werewolf.security.annotations.CanViewGuild
import dev.robothanzo.werewolf.service.DiscordService
import dev.robothanzo.werewolf.service.GameSessionService
import dev.robothanzo.werewolf.utils.IdentityUtils
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/sessions")
class SessionController(
    private val gameSessionService: GameSessionService,
    private val discordService: DiscordService,
    private val identityUtils: IdentityUtils
) {

    @GetMapping
    fun getAllSessions(): ResponseEntity<*> {
        val userOpt = identityUtils.getCurrentUser()
        if (userOpt.isEmpty) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build<Any>()
        }
        val user = userOpt.get()

        val data = gameSessionService.getAllSessions().filter { session ->
            // Check if user is in guild
            discordService.getMember(session.guildId, user.userId) != null
        }.map { session -> gameSessionService.sessionToSummaryJSON(session) }
        return ResponseEntity.ok(mapOf("success" to true, "data" to data))
    }

    @GetMapping("/{guildId}")
    @CanViewGuild
    fun getSession(@PathVariable guildId: Long): ResponseEntity<*> {
        return gameSessionService.getSession(guildId)
            .map { session ->
                ResponseEntity
                    .ok(mapOf("success" to true, "data" to gameSessionService.sessionToJSON(session)))
            }
            .orElse(ResponseEntity.notFound().build())
    }
}
