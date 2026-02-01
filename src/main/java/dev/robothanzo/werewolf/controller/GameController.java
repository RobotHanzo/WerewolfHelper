package dev.robothanzo.werewolf.controller;

import dev.robothanzo.werewolf.security.annotations.CanManageGuild;
import dev.robothanzo.werewolf.security.annotations.CanViewGuild;
import dev.robothanzo.werewolf.service.GameActionService;
import dev.robothanzo.werewolf.service.GameSessionService;
import dev.robothanzo.werewolf.service.PlayerService;
import dev.robothanzo.werewolf.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sessions/{guildId}")
@RequiredArgsConstructor
public class GameController {

    private final PlayerService playerService;
    private final RoleService roleService;
    private final GameActionService gameActionService;
    private final GameSessionService gameSessionService;

    // --- Players ---
    @GetMapping("/players")
    @CanViewGuild
    public ResponseEntity<?> getPlayers(@PathVariable long guildId) {
        return ResponseEntity.ok(Map.of("success", true, "data", playerService.getPlayersJSON(guildId)));
    }

    @PostMapping("/players/assign")
    @CanManageGuild
    public ResponseEntity<?> assignRoles(@PathVariable long guildId) {
        try {
            roleService.assignRoles(guildId,
                    msg -> gameActionService.broadcastProgress(guildId, msg, null),
                    pct -> gameActionService.broadcastProgress(guildId, null, pct));
            return ResponseEntity.ok(Map.of("success", true, "message", "Roles assigned"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/players/{playerId}/roles")
    @CanManageGuild
    public ResponseEntity<?> updatePlayerRoles(@PathVariable long guildId, @PathVariable String playerId,
                                               @RequestBody List<String> roles) {
        try {
            playerService.updatePlayerRoles(guildId, playerId, roles);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/players/{userId}/role")
    @CanManageGuild
    public ResponseEntity<?> updateUserRole(@PathVariable long guildId, @PathVariable long userId,
                                            @RequestBody Map<String, String> body) {
        try {
            dev.robothanzo.werewolf.database.documents.UserRole role = dev.robothanzo.werewolf.database.documents.UserRole
                    .fromString(body.get("role"));
            gameSessionService.updateUserRole(guildId, userId, role);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/players/{playerId}/switch-role-order")
    @CanManageGuild
    public ResponseEntity<?> switchRoleOrder(@PathVariable long guildId, @PathVariable String playerId) {
        try {
            playerService.switchRoleOrder(guildId, playerId);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/players/{playerId}/role-lock")
    @CanManageGuild
    public ResponseEntity<?> setRoleLock(@PathVariable long guildId, @PathVariable String playerId,
                                         @RequestParam boolean locked) {
        try {
            playerService.setRolePositionLock(guildId, playerId, locked);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // --- Actions ---
    @PostMapping("/players/{userId}/died")
    @CanManageGuild
    public ResponseEntity<?> markDead(@PathVariable long guildId, @PathVariable long userId,
                                      @RequestParam(defaultValue = "false") boolean lastWords) {
        gameActionService.markPlayerDead(guildId, userId, lastWords);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/players/{userId}/revive")
    @CanManageGuild
    public ResponseEntity<?> revive(@PathVariable long guildId, @PathVariable long userId) {
        gameActionService.revivePlayer(guildId, userId);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/players/{userId}/revive-role")
    @CanManageGuild
    public ResponseEntity<?> reviveRole(@PathVariable long guildId, @PathVariable long userId,
                                        @RequestParam String role) {
        gameActionService.reviveRole(guildId, userId, role);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/players/{userId}/police")
    @CanManageGuild
    public ResponseEntity<?> setPolice(@PathVariable long guildId, @PathVariable long userId) {
        gameActionService.setPolice(guildId, userId);
        return ResponseEntity.ok(Map.of("success", true));
    }

    // --- Roles ---
    @GetMapping("/roles")
    @CanViewGuild
    public ResponseEntity<?> getRoles(@PathVariable long guildId) {
        return ResponseEntity.ok(Map.of("success", true, "data", roleService.getRoles(guildId)));
    }

    @PostMapping("/roles/add")
    @CanManageGuild
    public ResponseEntity<?> addRole(@PathVariable long guildId, @RequestParam String role,
                                     @RequestParam(defaultValue = "1") int amount) {
        roleService.addRole(guildId, role, amount);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @DeleteMapping("/roles/{role}")
    @CanManageGuild
    public ResponseEntity<?> removeRole(@PathVariable long guildId, @PathVariable String role,
                                        @RequestParam(defaultValue = "1") int amount) {
        roleService.removeRole(guildId, role, amount);
        return ResponseEntity.ok(Map.of("success", true));
    }

    // --- Guild ---
    @GetMapping("/members")
    @CanViewGuild
    public ResponseEntity<?> getMembers(@PathVariable long guildId) {
        try {
            return ResponseEntity.ok(Map.of("success", true, "data", gameSessionService.getGuildMembers(guildId)));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PutMapping("/settings")
    @CanManageGuild
    public ResponseEntity<?> updateSettings(@PathVariable long guildId, @RequestBody Map<String, Object> body) {
        try {
            gameSessionService.updateSettings(guildId, body);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/player-count")
    @CanManageGuild
    public ResponseEntity<?> setPlayerCount(@PathVariable long guildId, @RequestBody Map<String, Integer> body) {
        try {
            playerService.setPlayerCount(guildId, body.get("count"),
                    msg -> gameActionService.broadcastProgress(guildId, msg, null),
                    pct -> gameActionService.broadcastProgress(guildId, null, pct));
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/start")
    @CanManageGuild
    public ResponseEntity<?> startGame(@PathVariable long guildId) {
        try {
            var session = gameSessionService.getSession(guildId)
                    .orElseThrow(() -> new Exception("Session not found"));
            session.addLog(dev.robothanzo.werewolf.database.documents.LogType.GAME_STARTED, "遊戲正式開始！", null);
            gameSessionService.saveSession(session);
            gameSessionService.broadcastSessionUpdate(session);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/reset")
    @CanManageGuild
    public ResponseEntity<?> resetGame(@PathVariable long guildId) {
        try {
            gameActionService.resetGame(guildId,
                    msg -> gameActionService.broadcastProgress(guildId, msg, null),
                    pct -> gameActionService.broadcastProgress(guildId, null, pct));
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", e.getMessage()));
        }
    }
}
