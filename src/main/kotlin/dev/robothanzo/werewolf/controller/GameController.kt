package dev.robothanzo.werewolf.controller

import dev.robothanzo.werewolf.database.documents.LogType
import dev.robothanzo.werewolf.database.documents.UserRole
import dev.robothanzo.werewolf.security.annotations.CanManageGuild
import dev.robothanzo.werewolf.security.annotations.CanViewGuild
import dev.robothanzo.werewolf.service.*
import org.springframework.context.annotation.Lazy
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/sessions/{guildId}")
class GameController(
    @param:Lazy
    private val playerService: PlayerService,
    private val roleService: RoleService,
    private val gameActionService: GameActionService,
    private val gameSessionService: GameSessionService,
    private val gameStateService: GameStateService,
    private val nightManager: NightManager
) {
    // --- Game State ---
    @GetMapping("/state")
    @CanViewGuild
    fun getGameState(@PathVariable guildId: Long): ResponseEntity<*> {
        val session = gameSessionService.getSession(guildId)
            .orElseThrow { Exception("Session not found") }
        return ResponseEntity.ok(
            mapOf(
                "success" to true,
                "currentState" to session.currentState,
                "currentStep" to gameStateService.getCurrentStep(session)?.name,
                "day" to session.day,
                "stateData" to session.stateData
            )
        )
    }

    @PostMapping("/state/next")
    @CanManageGuild
    fun nextState(@PathVariable guildId: Long): ResponseEntity<*> {
        gameSessionService.withLockedSession(guildId) { session ->
            gameStateService.nextStep(session)
        }
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @PostMapping("/state/set")
    @CanManageGuild
    fun setState(
        @PathVariable guildId: Long,
        @RequestBody body: Map<String, String>
    ): ResponseEntity<*> {
        val stepId = body["stepId"] ?: throw IllegalArgumentException("stepId is missing")
        gameSessionService.withLockedSession(guildId) { session ->
            gameStateService.startStep(session, stepId)
        }
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @PostMapping("/state/action")
    @CanManageGuild
    fun stateAction(
        @PathVariable guildId: Long,
        @RequestBody body: Map<String, Any>
    ): ResponseEntity<*> {
        val session = gameSessionService.getSession(guildId)
            .orElseThrow { Exception("Session not found") }
        val result = gameStateService.handleInput(session, body)
        return ResponseEntity.ok(result)
    }

    // --- Players ---
    @GetMapping("/players")
    @CanViewGuild
    fun getPlayers(@PathVariable guildId: Long): ResponseEntity<*> {
        val session = gameSessionService.getSession(guildId)
            .orElseThrow { Exception("Session not found") }
        return ResponseEntity.ok(mapOf("success" to true, "data" to playerService.getPlayersJSON(session)))
    }

    @PostMapping("/players/assign")
    @CanManageGuild
    fun assignRoles(@PathVariable guildId: Long): ResponseEntity<*> {
        return try {
            val session = gameSessionService.getSession(guildId)
                .orElseThrow { Exception("Session not found") }
            roleService.assignRoles(
                session,
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
            val session = gameSessionService.getSession(guildId)
                .orElseThrow { Exception("Session not found") }
            val player = session.players[playerId] ?: throw Exception("Player not found")
            playerService.updatePlayerRoles(player, roles)
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
            val session = gameSessionService.getSession(guildId)
                .orElseThrow { Exception("Session not found") }
            val roleName = body["role"] ?: throw IllegalArgumentException("Role is missing")
            val role = UserRole.fromString(roleName)
            gameSessionService.updateUserRole(session, userId, role)
            ResponseEntity.ok(mapOf("success" to true))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(mapOf("success" to false, "error" to e.message))
        }
    }

    @PostMapping("/players/{playerId}/switch-role-order")
    @CanManageGuild
    fun switchRoleOrder(@PathVariable guildId: Long, @PathVariable playerId: String): ResponseEntity<*> {
        return try {
            val session = gameSessionService.getSession(guildId)
                .orElseThrow { Exception("Session not found") }
            val player = session.players[playerId] ?: throw Exception("Player not found")
            playerService.switchRoleOrder(player)
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
            val session = gameSessionService.getSession(guildId)
                .orElseThrow { Exception("Session not found") }
            val player = session.players[playerId] ?: throw Exception("Player not found")
            playerService.setRolePositionLock(player, locked)
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
        val session = gameSessionService.getSession(guildId)
            .orElseThrow { Exception("Session not found") }
        gameActionService.markPlayerDead(session, userId, lastWords)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @PostMapping("/players/{userId}/revive")
    @CanManageGuild
    fun revive(@PathVariable guildId: Long, @PathVariable userId: Long): ResponseEntity<*> {
        val session = gameSessionService.getSession(guildId)
            .orElseThrow { Exception("Session not found") }
        gameActionService.revivePlayer(session, userId)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @PostMapping("/players/{userId}/revive-role")
    @CanManageGuild
    fun reviveRole(
        @PathVariable guildId: Long, @PathVariable userId: Long,
        @RequestParam role: String
    ): ResponseEntity<*> {
        val session = gameSessionService.getSession(guildId)
            .orElseThrow { Exception("Session not found") }
        gameActionService.reviveRole(session, userId, role)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @PostMapping("/players/{userId}/police")
    @CanManageGuild
    fun setPolice(@PathVariable guildId: Long, @PathVariable userId: Long): ResponseEntity<*> {
        val session = gameSessionService.getSession(guildId)
            .orElseThrow { Exception("Session not found") }
        gameActionService.setPolice(session, userId)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    // --- Roles ---
    @GetMapping("/roles")
    @CanViewGuild
    fun getRoles(@PathVariable guildId: Long): ResponseEntity<*> {
        val session = gameSessionService.getSession(guildId)
            .orElseThrow { Exception("Session not found") }
        return ResponseEntity.ok(mapOf("success" to true, "data" to roleService.getRoles(session)))
    }

    @PostMapping("/roles/add")
    @CanManageGuild
    fun addRole(
        @PathVariable guildId: Long, @RequestParam role: String,
        @RequestParam(defaultValue = "1") amount: Int
    ): ResponseEntity<*> {
        val session = gameSessionService.getSession(guildId)
            .orElseThrow { Exception("Session not found") }
        roleService.addRole(session, role, amount)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @DeleteMapping("/roles/{role}")
    @CanManageGuild
    fun removeRole(
        @PathVariable guildId: Long, @PathVariable role: String,
        @RequestParam(defaultValue = "1") amount: Int
    ): ResponseEntity<*> {
        val session = gameSessionService.getSession(guildId)
            .orElseThrow { Exception("Session not found") }
        roleService.removeRole(session, role, amount)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    // --- Guild ---
    @GetMapping("/members")
    @CanViewGuild
    fun getMembers(@PathVariable guildId: Long): ResponseEntity<*> {
        return try {
            val session = gameSessionService.getSession(guildId)
                .orElseThrow { Exception("Session not found") }
            ResponseEntity.ok(mapOf("success" to true, "data" to gameSessionService.getGuildMembers(session)))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(mapOf("success" to false, "error" to e.message))
        }
    }

    @PutMapping("/settings")
    @CanManageGuild
    fun updateSettings(
        @PathVariable guildId: Long,
        @RequestBody settings: Map<String, Any>
    ): ResponseEntity<*> {
        return try {
            gameSessionService.withLockedSession(guildId) { session ->
                for ((key, value) in settings) {
                    when (key) {
                        "doubleIdentities" -> session.doubleIdentities = value as Boolean
                        "muteAfterSpeech" -> session.muteAfterSpeech = value as Boolean
                        "witchCanSaveSelf" -> session.settings.witchCanSaveSelf = value as Boolean
                        "allowWolfSelfKill" -> session.settings.allowWolfSelfKill = value as Boolean
                        "hiddenRoleOnDeath" -> session.settings.hiddenRoleOnDeath = value as Boolean
                        else -> throw IllegalArgumentException("Unknown setting: $key")
                    }
                }
            }
            ResponseEntity.ok(mapOf("success" to true))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("success" to false, "error" to e.message))
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
            val session = gameSessionService.getSession(guildId)
                .orElseThrow { Exception("Session not found") }
            playerService.setPlayerCount(
                session, count,
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

            // Start the first step (Night Phase)
            gameStateService.startStep(session, "NIGHT_PHASE")

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
            val session = gameSessionService.getSession(guildId)
                .orElseThrow { Exception("Session not found") }
            gameActionService.resetGame(
                session,
                { msg: String -> gameActionService.broadcastProgress(guildId, msg, null) },
                { pct: Int -> gameActionService.broadcastProgress(guildId, "", pct) }
            )
            ResponseEntity.ok(mapOf("success" to true))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(mapOf("success" to false, "error" to e.message))
        }
    }

    @GetMapping("/night-status")
    @CanViewGuild
    fun getNightStatus(@PathVariable guildId: Long): ResponseEntity<*> {
        return try {
            val session = gameSessionService.getSession(guildId)
                .orElseThrow { Exception("Session not found") }

            if (session.currentState != "NIGHT_PHASE") {
                return ResponseEntity.ok(
                    mapOf(
                        "success" to false,
                        "message" to "Not in night phase"
                    )
                )
            }

            val nightStatus = nightManager.buildNightStatus(session)
            ResponseEntity.ok(
                mapOf(
                    "success" to true,
                    "data" to nightStatus
                )
            )
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(
                mapOf(
                    "success" to false,
                    "error" to e.message
                )
            )
        }
    }
}
