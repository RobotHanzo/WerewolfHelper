package dev.robothanzo.werewolf.controller

import dev.robothanzo.werewolf.database.documents.LogType
import dev.robothanzo.werewolf.database.documents.UserRole
import dev.robothanzo.werewolf.security.annotations.CanManageGuild
import dev.robothanzo.werewolf.security.annotations.CanViewGuild
import dev.robothanzo.werewolf.service.GameActionService
import dev.robothanzo.werewolf.service.GameSessionService
import dev.robothanzo.werewolf.service.PlayerService
import dev.robothanzo.werewolf.service.RoleService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/sessions/{guildId}")
class GameController(
    private val playerService: PlayerService,
    private val roleService: RoleService,
    private val gameActionService: GameActionService,
    private val gameSessionService: GameSessionService
) {

    // --- Players ---
    @GetMapping("/players")
    @CanViewGuild
    fun getPlayers(@PathVariable guildId: Long): ResponseEntity<*> {
        return ResponseEntity.ok(mapOf("success" to true, "data" to playerService.getPlayersJSON(guildId)))
    }

    @PostMapping("/players/assign")
    @CanManageGuild
    fun assignRoles(@PathVariable guildId: Long): ResponseEntity<*> {
        return try {
            roleService.assignRoles(
                guildId,
                { msg: String -> gameActionService.broadcastProgress(guildId, msg, null) },
                { pct: Int -> gameActionService.broadcastProgress(guildId, "", pct) }
            )
            ResponseEntity.ok(mapOf("success" to true, "message" to "Roles assigned"))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(mapOf("success" to false, "error" to e.message))
        }
    }

    @PostMapping("/players/{playerId}/roles")
    @CanManageGuild
    fun updatePlayerRoles(
        @PathVariable guildId: Long, @PathVariable playerId: String,
        @RequestBody roles: List<String>
    ): ResponseEntity<*> {
        return try {
            playerService.updatePlayerRoles(guildId, playerId, roles)
            ResponseEntity.ok(mapOf("success" to true))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(mapOf("success" to false, "error" to e.message))
        }
    }

    @PostMapping("/players/{userId}/role")
    @CanManageGuild
    fun updateUserRole(
        @PathVariable guildId: Long, @PathVariable userId: Long,
        @RequestBody body: Map<String, String>
    ): ResponseEntity<*> {
        return try {
            val roleName = body["role"] ?: throw IllegalArgumentException("Role is missing")
            val role = UserRole.fromString(roleName)
            gameSessionService.updateUserRole(guildId, userId, role)
            ResponseEntity.ok(mapOf("success" to true))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(mapOf("success" to false, "error" to e.message))
        }
    }

    @PostMapping("/players/{playerId}/switch-role-order")
    @CanManageGuild
    fun switchRoleOrder(@PathVariable guildId: Long, @PathVariable playerId: String): ResponseEntity<*> {
        return try {
            playerService.switchRoleOrder(guildId, playerId)
            ResponseEntity.ok(mapOf("success" to true))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(mapOf("success" to false, "error" to e.message))
        }
    }

    @PostMapping("/players/{playerId}/role-lock")
    @CanManageGuild
    fun setRoleLock(
        @PathVariable guildId: Long, @PathVariable playerId: String,
        @RequestParam locked: Boolean
    ): ResponseEntity<*> {
        return try {
            playerService.setRolePositionLock(guildId, playerId, locked)
            ResponseEntity.ok(mapOf("success" to true))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(mapOf("success" to false, "error" to e.message))
        }
    }

    // --- Actions ---
    @PostMapping("/players/{userId}/died")
    @CanManageGuild
    fun markDead(
        @PathVariable guildId: Long, @PathVariable userId: Long,
        @RequestParam(defaultValue = "false") lastWords: Boolean
    ): ResponseEntity<*> {
        gameActionService.markPlayerDead(guildId, userId, lastWords)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @PostMapping("/players/{userId}/revive")
    @CanManageGuild
    fun revive(@PathVariable guildId: Long, @PathVariable userId: Long): ResponseEntity<*> {
        gameActionService.revivePlayer(guildId, userId)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @PostMapping("/players/{userId}/revive-role")
    @CanManageGuild
    fun reviveRole(
        @PathVariable guildId: Long, @PathVariable userId: Long,
        @RequestParam role: String
    ): ResponseEntity<*> {
        gameActionService.reviveRole(guildId, userId, role)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @PostMapping("/players/{userId}/police")
    @CanManageGuild
    fun setPolice(@PathVariable guildId: Long, @PathVariable userId: Long): ResponseEntity<*> {
        gameActionService.setPolice(guildId, userId)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    // --- Roles ---
    @GetMapping("/roles")
    @CanViewGuild
    fun getRoles(@PathVariable guildId: Long): ResponseEntity<*> {
        return ResponseEntity.ok(mapOf("success" to true, "data" to roleService.getRoles(guildId)))
    }

    @PostMapping("/roles/add")
    @CanManageGuild
    fun addRole(
        @PathVariable guildId: Long, @RequestParam role: String,
        @RequestParam(defaultValue = "1") amount: Int
    ): ResponseEntity<*> {
        roleService.addRole(guildId, role, amount)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @DeleteMapping("/roles/{role}")
    @CanManageGuild
    fun removeRole(
        @PathVariable guildId: Long, @PathVariable role: String,
        @RequestParam(defaultValue = "1") amount: Int
    ): ResponseEntity<*> {
        roleService.removeRole(guildId, role, amount)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    // --- Guild ---
    @GetMapping("/members")
    @CanViewGuild
    fun getMembers(@PathVariable guildId: Long): ResponseEntity<*> {
        return try {
            ResponseEntity.ok(mapOf("success" to true, "data" to gameSessionService.getGuildMembers(guildId)))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(mapOf("success" to false, "error" to e.message))
        }
    }

    @PutMapping("/settings")
    @CanManageGuild
    fun updateSettings(
        @PathVariable guildId: Long,
        @RequestBody body: Map<String, Any>
    ): ResponseEntity<*> {
        return try {
            gameSessionService.updateSettings(guildId, body)
            ResponseEntity.ok(mapOf("success" to true))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(mapOf("success" to false, "error" to e.message))
        }
    }

    @PostMapping("/player-count")
    @CanManageGuild
    fun setPlayerCount(
        @PathVariable guildId: Long,
        @RequestBody body: Map<String, Int>
    ): ResponseEntity<*> {
        return try {
            val count = body["count"] ?: throw IllegalArgumentException("Count is missing")
            playerService.setPlayerCount(
                guildId, count,
                { msg: String -> gameActionService.broadcastProgress(guildId, msg, null) },
                { pct: Int -> gameActionService.broadcastProgress(guildId, "", pct) }
            )
            ResponseEntity.ok(mapOf("success" to true))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(mapOf("success" to false, "error" to e.message))
        }
    }

    @PostMapping("/start")
    @CanManageGuild
    fun startGame(@PathVariable guildId: Long): ResponseEntity<*> {
        return try {
            val session = gameSessionService.getSession(guildId)
                .orElseThrow { Exception("Session not found") }
            session.addLog(LogType.GAME_STARTED, "遊戲正式開始！", null)
            gameSessionService.saveSession(session)
            gameSessionService.broadcastSessionUpdate(session)
            ResponseEntity.ok(mapOf("success" to true))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(mapOf("success" to false, "error" to e.message))
        }
    }

    @PostMapping("/reset")
    @CanManageGuild
    fun resetGame(@PathVariable guildId: Long): ResponseEntity<*> {
        return try {
            gameActionService.resetGame(
                guildId,
                { msg: String -> gameActionService.broadcastProgress(guildId, msg, null) },
                { pct: Int -> gameActionService.broadcastProgress(guildId, "", pct) }
            )
            ResponseEntity.ok(mapOf("success" to true))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(mapOf("success" to false, "error" to e.message))
        }
    }
}
