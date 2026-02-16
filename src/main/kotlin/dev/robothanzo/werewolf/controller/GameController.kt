package dev.robothanzo.werewolf.controller

import dev.robothanzo.werewolf.controller.dto.ApiResponse
import dev.robothanzo.werewolf.controller.dto.GameRequests
import dev.robothanzo.werewolf.controller.dto.GuildMembersResponse
import dev.robothanzo.werewolf.controller.dto.StateActionResponse
import dev.robothanzo.werewolf.database.documents.LogType
import dev.robothanzo.werewolf.database.documents.UserRole
import dev.robothanzo.werewolf.game.model.DeathCause
import dev.robothanzo.werewolf.security.annotations.CanManageGuild
import dev.robothanzo.werewolf.security.annotations.CanViewGuild
import dev.robothanzo.werewolf.service.*
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Lazy
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@RestController
@RequestMapping("/api/sessions/{guildId}")
@Tag(name = "Game Management", description = "Endpoints for managing the game state, players, and roles")
class GameController(
    @param:Lazy
    private val playerService: PlayerService,
    private val roleService: RoleService,
    private val gameActionService: GameActionService,
    private val gameSessionService: GameSessionService,
    private val gameStateService: GameStateService
) {
    // --- Game State ---
    @Operation(summary = "Advance Game State", description = "Manually advances the game to the next state/phase.")
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "Game state advanced successfully"),
            SwaggerApiResponse(responseCode = "403", description = "User does not have permission to manage this guild")
        ]
    )
    @PostMapping("/state/next")
    @CanManageGuild
    fun nextState(@PathVariable guildId: String): ResponseEntity<ApiResponse> {
        gameSessionService.withLockedSession(guildId.toLong()) { session ->
            gameStateService.nextStep(session)
        }
        return ResponseEntity.ok(ApiResponse.ok(message = "Game state advanced"))
    }

    @Operation(summary = "Set Game State", description = "Sets the game to a specific step/phase.")
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "Game state set successfully"),
            SwaggerApiResponse(responseCode = "403", description = "User does not have permission to manage this guild")
        ]
    )
    @PostMapping("/state/set")
    @CanManageGuild
    fun setState(
        @PathVariable guildId: String,
        @RequestBody body: GameRequests.StateSetRequest
    ): ResponseEntity<ApiResponse> {
        gameSessionService.withLockedSession(guildId.toLong()) { session ->
            gameStateService.startStep(session, body.stepId)
        }
        return ResponseEntity.ok(ApiResponse.ok(message = "Game state set to ${body.stepId}"))
    }

    @Operation(
        summary = "Execute State Action",
        description = "Performs a specific action within the current game state (e.g., voting)."
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(
                responseCode = "200", description = "Action executed successfully",
                content = [Content(schema = Schema(implementation = StateActionResponse::class))]
            ),
            SwaggerApiResponse(responseCode = "404", description = "Session not found"),
            SwaggerApiResponse(responseCode = "403", description = "User does not have permission to manage this guild")
        ]
    )
    @PostMapping("/state/action")
    @CanManageGuild
    fun stateAction(
        @PathVariable guildId: String,
        @RequestBody body: GameRequests.StateActionRequest
    ): ResponseEntity<ApiResponse> {
        val session = gameSessionService.getSession(guildId.toLong())
            .orElseThrow { Exception("Session not found") }

        // Construct map as expected by service
        val actionMap: MutableMap<String, Any> = mutableMapOf("action" to body.action)
        body.data?.let { actionMap.putAll(it) }

        val result = gameStateService.handleInput(session, actionMap)
        return ResponseEntity.ok(StateActionResponse(result))
    }

    // --- Players ---

    @Operation(
        summary = "Assign Roles",
        description = "Randomly assigns roles to players based on the current configuration."
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "Roles assigned successfully"),
            SwaggerApiResponse(responseCode = "404", description = "Session not found"),
            SwaggerApiResponse(responseCode = "403", description = "User does not have permission to manage this guild")
        ]
    )
    @PostMapping("/players/assign")
    @CanManageGuild
    fun assignRoles(@PathVariable guildId: String): ResponseEntity<ApiResponse> {
        return try {
            val session = gameSessionService.getSession(guildId.toLong())
                .orElseThrow { Exception("Session not found") }
            roleService.assignRoles(
                session,
                { msg: String -> gameActionService.broadcastProgress(guildId.toLong(), msg, null) },
                { pct: Int -> gameActionService.broadcastProgress(guildId.toLong(), "", pct) }
            )
            ResponseEntity.ok(ApiResponse.ok(message = "Roles assigned successfully"))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(ApiResponse.error(e.message ?: "Unknown error"))
        }
    }

    @Operation(summary = "Update Player Roles", description = "Manually updates the roles for a specific player.")
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "Player roles updated successfully"),
            SwaggerApiResponse(responseCode = "404", description = "Session or Player not found"),
            SwaggerApiResponse(responseCode = "403", description = "User does not have permission to manage this guild")
        ]
    )
    @PostMapping("/players/{playerId}/roles")
    @CanManageGuild
    fun updatePlayerRoles(
        @PathVariable guildId: String, @PathVariable playerId: Int,
        @RequestBody body: List<String> // Keeping list for backward compat, but ideally should be wrapped
    ): ResponseEntity<ApiResponse> {
        return try {
            val session = gameSessionService.getSession(guildId.toLong())
                .orElseThrow { Exception("Session not found") }
            val player = session.getPlayer(playerId) ?: throw Exception("Player not found")
            playerService.updatePlayerRoles(player, body)
            ResponseEntity.ok(ApiResponse.ok(message = "Player roles updated"))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(ApiResponse.error(e.message ?: "Unknown error"))
        }
    }

    @Operation(
        summary = "Update User Role (Permission)",
        description = "Updates the permission level (e.g., JUDGE, SPECTATOR) of a user."
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "User role updated successfully"),
            SwaggerApiResponse(responseCode = "404", description = "Session not found"),
            SwaggerApiResponse(responseCode = "403", description = "User does not have permission to manage this guild")
        ]
    )
    @PostMapping("/players/{userId}/role")
    @CanManageGuild
    fun updateUserRole(
        @PathVariable guildId: String, @PathVariable userId: String,
        @RequestBody body: GameRequests.PlayerRoleUpdateRequest
    ): ResponseEntity<ApiResponse> {
        return try {
            val session = gameSessionService.getSession(guildId.toLong())
                .orElseThrow { Exception("Session not found") }
            val role = UserRole.fromString(body.role)
            gameSessionService.updateUserRole(session, userId.toLong(), role)
            ResponseEntity.ok(ApiResponse.ok(message = "User role updated"))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(ApiResponse.error(e.message ?: "Unknown error"))
        }
    }

    @Operation(summary = "Switch Role Order", description = "Switches the order of dual roles for a player.")
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "Role order switched successfully"),
            SwaggerApiResponse(responseCode = "404", description = "Session or Player not found"),
            SwaggerApiResponse(responseCode = "403", description = "User does not have permission to manage this guild")
        ]
    )
    @PostMapping("/players/{playerId}/switch-role-order")
    @CanManageGuild
    fun switchRoleOrder(
        @PathVariable guildId: String,
        @PathVariable playerId: Int
    ): ResponseEntity<ApiResponse> {
        return try {
            val session = gameSessionService.getSession(guildId.toLong())
                .orElseThrow { Exception("Session not found") }
            val player = session.getPlayer(playerId) ?: throw Exception("Player not found")
            playerService.switchRoleOrder(player)
            ResponseEntity.ok(ApiResponse.ok(message = "Role order switched"))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(ApiResponse.error(e.message ?: "Unknown error"))
        }
    }

    @Operation(summary = "Set Role Lock", description = "Locks or unlocks the player's role position/card.")
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "Role lock updated successfully"),
            SwaggerApiResponse(responseCode = "404", description = "Session or Player not found"),
            SwaggerApiResponse(responseCode = "403", description = "User does not have permission to manage this guild")
        ]
    )
    @PostMapping("/players/{playerId}/role-lock")
    @CanManageGuild
    fun setRoleLock(
        @PathVariable guildId: String, @PathVariable playerId: Int,
        @RequestParam locked: Boolean
    ): ResponseEntity<ApiResponse> {
        return try {
            val session = gameSessionService.getSession(guildId.toLong())
                .orElseThrow { Exception("Session not found") }
            val player = session.getPlayer(playerId) ?: throw Exception("Player not found")
            playerService.setRolePositionLock(player, locked)
            ResponseEntity.ok(ApiResponse.ok(message = "Role lock updated"))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(ApiResponse.error(e.message ?: "Unknown error"))
        }
    }

    // --- Actions ---
    @Operation(summary = "Mark Player Dead", description = "Marks a player as dead in the game.")
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "Player marked as dead successfully"),
            SwaggerApiResponse(responseCode = "404", description = "Session not found"),
            SwaggerApiResponse(responseCode = "403", description = "User does not have permission to manage this guild")
        ]
    )
    @PostMapping("/players/{playerId}/died")
    @CanManageGuild
    fun markDead(
        @PathVariable guildId: String, @PathVariable playerId: Int,
        @Parameter(description = "Whether the player is allowed to leave last words")
        @RequestParam(defaultValue = "false") lastWords: Boolean
    ): ResponseEntity<ApiResponse> {
        val session = gameSessionService.getSession(guildId.toLong())
            .orElseThrow { Exception("Session not found") }
        val player = session.getPlayer(playerId) ?: throw Exception("Player not found")
        player.died(DeathCause.UNKNOWN, lastWords)
        return ResponseEntity.ok(ApiResponse.ok(message = "Player marked as dead"))
    }

    @Operation(summary = "Revive Player", description = "Revives a dead player.")
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "Player revived successfully"),
            SwaggerApiResponse(responseCode = "404", description = "Session not found"),
            SwaggerApiResponse(responseCode = "403", description = "User does not have permission to manage this guild")
        ]
    )
    @PostMapping("/players/{playerId}/revive")
    @CanManageGuild
    fun revive(@PathVariable guildId: String, @PathVariable playerId: Int): ResponseEntity<ApiResponse> {
        val session = gameSessionService.getSession(guildId.toLong())
            .orElseThrow { Exception("Session not found") }
        gameActionService.revivePlayer(session, playerId)
        return ResponseEntity.ok(ApiResponse.ok(message = "Player revived"))
    }

    @Operation(
        summary = "Revive Role",
        description = "Revives a specific role for a player (for multi-role scenarios)."
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "Role revived successfully"),
            SwaggerApiResponse(responseCode = "404", description = "Session not found"),
            SwaggerApiResponse(responseCode = "403", description = "User does not have permission to manage this guild")
        ]
    )
    @PostMapping("/players/{playerId}/revive-role")
    @CanManageGuild
    fun reviveRole(
        @PathVariable guildId: String, @PathVariable playerId: Int,
        @RequestParam role: String
    ): ResponseEntity<ApiResponse> {
        val session = gameSessionService.getSession(guildId.toLong())
            .orElseThrow { Exception("Session not found") }
        gameActionService.reviveRole(session, playerId, role)
        return ResponseEntity.ok(ApiResponse.ok(message = "Role revived"))
    }

    @Operation(summary = "Set Sheriff (Police)", description = "Appoints a player as the Sheriff (Police).")
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "Sheriff appointed successfully"),
            SwaggerApiResponse(responseCode = "404", description = "Session not found"),
            SwaggerApiResponse(responseCode = "403", description = "User does not have permission to manage this guild")
        ]
    )
    @PostMapping("/players/{playerId}/police")
    @CanManageGuild
    fun setPolice(@PathVariable guildId: String, @PathVariable playerId: Int): ResponseEntity<ApiResponse> {
        val session = gameSessionService.getSession(guildId.toLong())
            .orElseThrow { Exception("Session not found") }
        gameActionService.setPolice(session, playerId)
        return ResponseEntity.ok(ApiResponse.ok(message = "Sheriff appointed"))
    }

    // --- Roles ---
    @Operation(summary = "Add Role", description = "Adds a role to the game configuration.")
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "Role added successfully"),
            SwaggerApiResponse(responseCode = "404", description = "Session not found"),
            SwaggerApiResponse(responseCode = "403", description = "User does not have permission to manage this guild")
        ]
    )
    @PostMapping("/roles/add")
    @CanManageGuild
    fun addRole(
        @PathVariable guildId: String, @RequestParam role: String,
        @RequestParam(defaultValue = "1") amount: Int
    ): ResponseEntity<ApiResponse> {
        val session = gameSessionService.getSession(guildId.toLong())
            .orElseThrow { Exception("Session not found") }
        roleService.addRole(session, role, amount)
        return ResponseEntity.ok(ApiResponse.ok(message = "Role added"))
    }

    @Operation(summary = "Remove Role", description = "Removes a role from the game configuration.")
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "Role removed successfully"),
            SwaggerApiResponse(responseCode = "404", description = "Session not found"),
            SwaggerApiResponse(responseCode = "403", description = "User does not have permission to manage this guild")
        ]
    )
    @DeleteMapping("/roles/{role}")
    @CanManageGuild
    fun removeRole(
        @PathVariable guildId: String, @PathVariable role: String,
        @RequestParam(defaultValue = "1") amount: Int
    ): ResponseEntity<ApiResponse> {
        val session = gameSessionService.getSession(guildId.toLong())
            .orElseThrow { Exception("Session not found") }
        roleService.removeRole(session, role, amount)
        return ResponseEntity.ok(ApiResponse.ok(message = "Role removed"))
    }

    // --- Guild ---
    @Operation(
        summary = "Get Guild Members",
        description = "Retrieves a list of members in the guild (Discord members)."
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(
                responseCode = "200", description = "Successfully retrieved guild members",
                content = [Content(schema = Schema(implementation = GuildMembersResponse::class))]
            ),
            SwaggerApiResponse(responseCode = "404", description = "Session not found"),
            SwaggerApiResponse(responseCode = "403", description = "User does not have permission to view this guild")
        ]
    )
    @GetMapping("/members")
    @CanViewGuild
    fun getMembers(@PathVariable guildId: String): ResponseEntity<ApiResponse> {
        return try {
            val session = gameSessionService.getSession(guildId.toLong())
                .orElseThrow { Exception("Session not found") }

            val members = gameSessionService.getGuildMembers(session)
            return ResponseEntity.ok(GuildMembersResponse(members))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(ApiResponse.error(e.message ?: "Unknown error"))
        }
    }

    @Operation(summary = "Update Game Settings", description = "Updates game configuration settings.")
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "Game settings updated successfully"),
            SwaggerApiResponse(responseCode = "404", description = "Session not found"),
            SwaggerApiResponse(responseCode = "403", description = "User does not have permission to manage this guild")
        ]
    )
    @PutMapping("/settings")
    @CanManageGuild
    fun updateSettings(
        @PathVariable guildId: String,
        @RequestBody settings: Map<String, Any> // Keeping generic Map for settings for now as they are dynamic
    ): ResponseEntity<ApiResponse> {
        return try {
            gameSessionService.withLockedSession(guildId.toLong()) { session ->
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
            ResponseEntity.ok(ApiResponse.ok(message = "Settings updated"))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(ApiResponse.error(e.message ?: "Invalid setting"))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(ApiResponse.error(e.message ?: "Unknown error"))
        }
    }

    @Operation(summary = "Set Player Count", description = "Sets the number of players for the game.")
    @PostMapping("/player-count")
    @CanManageGuild
    fun setPlayerCount(
        @PathVariable guildId: String,
        @RequestBody body: GameRequests.PlayerCountRequest
    ): ResponseEntity<ApiResponse> {
        return try {
            val session = gameSessionService.getSession(guildId.toLong())
                .orElseThrow { Exception("Session not found") }
            playerService.setPlayerCount(
                session, body.count,
                { msg: String -> gameActionService.broadcastProgress(guildId.toLong(), msg, null) },
                { pct: Int -> gameActionService.broadcastProgress(guildId.toLong(), "", pct) }
            )
            ResponseEntity.ok(ApiResponse.ok(message = "Player count set"))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(ApiResponse.error(e.message ?: "Unknown error"))
        }
    }

    @Operation(summary = "Start Game", description = "Starts the game.")
    @PostMapping("/start")
    @CanManageGuild
    fun startGame(@PathVariable guildId: String): ResponseEntity<ApiResponse> {
        return try {
            val session = gameSessionService.getSession(guildId.toLong())
                .orElseThrow { Exception("Session not found") }
            session.addLog(LogType.GAME_STARTED, "遊戲正式開始！", null)
            gameSessionService.saveSession(session)

            // Start the first step (Night Phase)
            gameStateService.startStep(session, "NIGHT_PHASE")

            gameSessionService.broadcastSessionUpdate(session)
            ResponseEntity.ok(ApiResponse.ok(message = "Game started"))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(ApiResponse.error(e.message ?: "Unknown error"))
        }
    }

    @Operation(summary = "Reset Game", description = "Resets the game to its initial state.")
    @PostMapping("/reset")
    @CanManageGuild
    fun resetGame(@PathVariable guildId: String): ResponseEntity<ApiResponse> {
        return try {
            val session = gameSessionService.getSession(guildId.toLong())
                .orElseThrow { Exception("Session not found") }
            gameActionService.resetGame(
                session,
                { msg: String -> gameActionService.broadcastProgress(guildId.toLong(), msg, null) },
                { pct: Int -> gameActionService.broadcastProgress(guildId.toLong(), "", pct) }
            )
            ResponseEntity.ok(ApiResponse.ok(message = "Game reset"))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(ApiResponse.error(e.message ?: "Unknown error"))
        }
    }
}
