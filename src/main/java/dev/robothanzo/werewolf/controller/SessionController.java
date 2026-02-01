package dev.robothanzo.werewolf.controller;

import dev.robothanzo.werewolf.security.annotations.CanViewGuild;
import dev.robothanzo.werewolf.service.GameSessionService;
import dev.robothanzo.werewolf.utils.IdentityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final GameSessionService gameSessionService;
    private final dev.robothanzo.werewolf.service.DiscordService discordService;
    private final IdentityUtils identityUtils;

    @GetMapping
    public ResponseEntity<?> getAllSessions() {
        var userOpt = identityUtils.getCurrentUser();
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).build();
        }
        dev.robothanzo.werewolf.database.documents.AuthSession user = userOpt.get();

        List<Map<String, Object>> data = gameSessionService.getAllSessions().stream()
                .filter(session -> {
                    // Check if user is in guild
                    return discordService.getMember(session.getGuildId(), user.getUserId()) != null;
                })
                .map(gameSessionService::sessionToSummaryJSON)
                .collect(Collectors.toList());
        return ResponseEntity.ok(Map.of("success", true, "data", data));
    }

    @GetMapping("/{guildId}")
    @CanViewGuild
    public ResponseEntity<?> getSession(@PathVariable long guildId) {
        return gameSessionService.getSession(guildId)
                .map(session -> ResponseEntity
                        .ok(Map.of("success", true, "data", gameSessionService.sessionToJSON(session))))
                .orElse(ResponseEntity.notFound().build());
    }
}
