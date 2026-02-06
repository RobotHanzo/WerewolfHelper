package dev.robothanzo.werewolf.controller

import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.security.annotations.CanViewGuild
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/users")
class UserController {
    private val log = LoggerFactory.getLogger(UserController::class.java)

    @GetMapping("/{guildId}/{userId}")
    @CanViewGuild
    fun getUser(@PathVariable guildId: Long, @PathVariable userId: String): ResponseEntity<*> {
        return try {
            val member = WerewolfApplication.jda.getGuildById(guildId)?.getMemberById(userId)
            if (member != null) {
                ResponseEntity.ok(
                    mapOf(
                        "success" to true,
                        "data" to mapOf(
                            "id" to member.id,
                            "name" to member.effectiveName,
                            "avatar" to member.effectiveAvatarUrl
                        )
                    )
                )
            } else {
                ResponseEntity.notFound().build<Any>()
            }
        } catch (e: Exception) {
            log.error("Failed to fetch user info", e)
            ResponseEntity.internalServerError().body(mapOf("success" to false, "error" to e.message))
        }
    }
}
