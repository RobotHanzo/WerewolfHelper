package dev.robothanzo.werewolf.controller

import dev.robothanzo.werewolf.database.documents.LogType
import dev.robothanzo.werewolf.database.documents.UserRole
import dev.robothanzo.werewolf.security.annotations.CanManageGuild
import dev.robothanzo.werewolf.security.annotations.CanViewGuild
import dev.robothanzo.werewolf.service.*
import dev.robothanzo.werewolf.utils.parseLong
import org.slf4j.LoggerFactory
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
    private val roleActionService: RoleActionService
) {
    private val log = LoggerFactory.getLogger(GameController::class.java)

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
        val session = gameSessionService.getSession(guildId)
            .orElseThrow { Exception("Session not found") }
        gameStateService.nextStep(session)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @PostMapping("/state/set")
    @CanManageGuild
    fun setState(
        @PathVariable guildId: Long,
        @RequestBody body: Map<String, String>
    ): ResponseEntity<*> {
        val session = gameSessionService.getSession(guildId)
            .orElseThrow { Exception("Session not found") }
        val stepId = body["stepId"] ?: throw IllegalArgumentException("stepId is missing")
        gameStateService.startStep(session, stepId)
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

    // --- Role Actions ---
    @PostMapping("/actions/submit")
    @CanManageGuild
    fun submitRoleAction(
        @PathVariable guildId: Long,
        @RequestBody request: Map<String, Any>
    ): ResponseEntity<*> {
        return try {
            val actionDefinitionId = request["actionDefinitionId"] as? String
                ?: return ResponseEntity.badRequest()
                    .body(mapOf("success" to false, "error" to "Missing actionDefinitionId"))
            val actorUserId = parseLong(request["actorUserId"])
                ?: return ResponseEntity.badRequest().body(mapOf("success" to false, "error" to "Missing actorUserId"))

            @Suppress("UNCHECKED_CAST")
            val targetUserIds = (request["targetUserIds"] as? List<*>)?.mapNotNull { parseLong(it) } ?: emptyList()
            val submittedBy = request["submittedBy"] as? String ?: "PLAYER"

            log.info(
                "[ActionSubmit] guildId={}, actorUserId={}, actionDefinitionId={}, targets={}, submittedBy={}",
                guildId,
                actorUserId,
                actionDefinitionId,
                targetUserIds,
                submittedBy
            )
            val result =
                roleActionService.submitAction(guildId, actionDefinitionId, actorUserId, targetUserIds, submittedBy)
            log.info("[ActionSubmit] result={}", result)
            ResponseEntity.ok(result)
        } catch (e: Exception) {
            log.error("[ActionSubmit] Error submitting action: {}", e.message, e)
            ResponseEntity.internalServerError().body(mapOf("success" to false, "error" to e.message))
        }
    }

    @GetMapping("/actions/available")
    @CanViewGuild
    fun getAvailableActions(
        @PathVariable guildId: Long,
        @RequestParam(required = false) userId: Long?
    ): ResponseEntity<*> {
        return try {
            val session = gameSessionService.getSession(guildId)
                .orElseThrow { Exception("Session not found") }

            val result = if (userId != null) {
                // Get actions for specific player
                mapOf("actions" to roleActionService.getAvailableActionsForPlayer(session, userId))
            } else {
                // Get all available actions for judge
                mapOf("actions" to roleActionService.getAvailableActionsForJudge(session))
            }

            ResponseEntity.ok(result)
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(mapOf("success" to false, "error" to e.message))
        }
    }

    @PostMapping("/settings/update")
    @CanManageGuild
    fun updateGameSettings(
        @PathVariable guildId: Long,
        @RequestBody settings: Map<String, Any>
    ): ResponseEntity<*> {
        return try {
            val session = gameSessionService.getSession(guildId)
                .orElseThrow { Exception("Session not found") }

            // Update specific settings
            for ((key, value) in settings) {
                session.settings[key] = value
            }

            gameSessionService.saveSession(session)
            ResponseEntity.ok(mapOf("success" to true))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(mapOf("success" to false, "error" to e.message))
        }
    }

    @PostMapping("/custom-roles/save")
    @CanManageGuild
    fun saveCustomRole(
        @PathVariable guildId: Long,
        @RequestBody roleDefinition: dev.robothanzo.werewolf.model.CustomRoleDefinition
    ): ResponseEntity<*> {
        return try {
            val session = gameSessionService.getSession(guildId)
                .orElseThrow { Exception("Session not found") }

            @Suppress("UNCHECKED_CAST")
            val customRoles =
                (session.settings.getOrPut("customRoles") { mutableMapOf<String, Any>() } as MutableMap<String, Any>)

            // Validate custom role
            val warnings = mutableListOf<String>()
            if (roleDefinition.camp !in listOf("WEREWOLF", "GOD", "VILLAGER")) {
                warnings.add("Invalid camp: ${roleDefinition.camp}")
            }
            if (roleDefinition.actions.isEmpty()) {
                warnings.add("Custom role has no actions")
            }

            // Convert to map and store
            val roleMap = mapOf(
                "name" to roleDefinition.name,
                "camp" to roleDefinition.camp,
                "actions" to roleDefinition.actions,
                "eventListeners" to roleDefinition.eventListeners
            )

            customRoles[roleDefinition.name] = roleMap
            gameSessionService.saveSession(session)

            ResponseEntity.ok(
                mapOf(
                    "success" to true,
                    "warnings" to warnings
                )
            )
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(mapOf("success" to false, "error" to e.message))
        }
    }
}
