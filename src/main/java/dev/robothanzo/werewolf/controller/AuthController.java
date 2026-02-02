package dev.robothanzo.werewolf.controller;

import dev.robothanzo.werewolf.database.documents.AuthSession;
import dev.robothanzo.werewolf.database.documents.UserRole;
import dev.robothanzo.werewolf.service.DiscordService;
import io.mokulu.discord.oauth.DiscordAPI;
import io.mokulu.discord.oauth.DiscordOAuth;
import io.mokulu.discord.oauth.model.TokensResponse;
import io.mokulu.discord.oauth.model.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String CLIENT_ID = System.getenv().getOrDefault("DISCORD_CLIENT_ID", "");
    private static final String CLIENT_SECRET = System.getenv().getOrDefault("DISCORD_CLIENT_SECRET", "");
    private static final String REDIRECT_URI = System.getenv().getOrDefault("DISCORD_REDIRECT_URI",
            "http://localhost:5173/auth/callback");

    private final DiscordOAuth discordOAuth = new DiscordOAuth(CLIENT_ID, CLIENT_SECRET, REDIRECT_URI,
            new String[]{"identify", "guilds", "guilds.members.read"});
    private final DiscordService discordService;

    @GetMapping("/login")
    public void login(@RequestParam(name = "guild_id", required = false) String guildId, HttpServletResponse response)
            throws IOException {
        String state = guildId != null ? guildId : "no_guild";
        response.sendRedirect(discordOAuth.getAuthorizationURL(state));
    }

    @GetMapping("/callback")
    public void callback(@RequestParam String code, @RequestParam String state, 
                         HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            TokensResponse tokenResponse = discordOAuth.getTokens(code);
            DiscordAPI discordAPI = new DiscordAPI(tokenResponse.getAccessToken());
            User user = discordAPI.fetchUser();

            // Prevent session fixation: invalidate old session and create new one
            HttpSession oldSession = request.getSession(false);
            if (oldSession != null) {
                oldSession.invalidate();
            }
            HttpSession session = request.getSession(true);

            // Store user in Session
            AuthSession authSession = AuthSession.builder()
                    .userId(user.getId())
                    .username(user.getUsername())
                    .discriminator(user.getDiscriminator())
                    .avatar(user.getAvatar())
                    .build();

            session.setAttribute("user", authSession);

            if (!"no_guild".equals(state)) {
                try {
                    // Validate it's a number
                    long gid = Long.parseLong(state);
                    authSession.setGuildId(state); // Store as String

                    // Attempt to pre-calculate role
                    net.dv8tion.jda.api.entities.Member member = discordService.getMember(gid, user.getId());
                    if (member != null && (member.hasPermission(net.dv8tion.jda.api.Permission.ADMINISTRATOR)
                            || member.hasPermission(net.dv8tion.jda.api.Permission.MANAGE_SERVER))) {
                        authSession.setRole(UserRole.JUDGE);
                    } else {
                        authSession.setRole(UserRole.SPECTATOR);
                    }
                } catch (Exception e) {
                    log.warn("Failed to set initial guild info: {}", state, e);
                    authSession.setRole(UserRole.PENDING);
                }

                session.setAttribute("user", authSession);
                response.sendRedirect(
                        System.getenv().getOrDefault("DASHBOARD_URL", "http://localhost:5173") + "/server/" + state);
            } else {
                authSession.setRole(UserRole.PENDING);
                session.setAttribute("user", authSession);
                response.sendRedirect(System.getenv().getOrDefault("DASHBOARD_URL", "http://localhost:5173") + "/");
            }
        } catch (Exception e) {
            log.error("Auth callback failed", e);
            response.sendError(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Auth failed");
        }
    }

    @PostMapping("/select-guild/{guildId}")
    public ResponseEntity<?> selectGuild(@PathVariable String guildId, HttpSession session) {
        AuthSession user = (AuthSession) session.getAttribute("user");
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            long gid = Long.parseLong(guildId);
            net.dv8tion.jda.api.entities.Guild guild = discordService.getGuild(gid);
            if (guild == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Guild not found"));
            }

            net.dv8tion.jda.api.entities.Member member = discordService.getMember(gid, user.getUserId());
            if (member == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("success", false, "error", "Not a member"));
            }

            // Determine role
            UserRole role = UserRole.SPECTATOR; // Default
            if (member.hasPermission(net.dv8tion.jda.api.Permission.ADMINISTRATOR)
                    || member.hasPermission(net.dv8tion.jda.api.Permission.MANAGE_SERVER)) {
                role = UserRole.JUDGE;
            }

            user.setGuildId(guildId); // Store as String
            user.setRole(role);
            session.setAttribute("user", user);

            return ResponseEntity.ok(Map.of("success", true, "user", user));
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Invalid guild ID"));
        } catch (Exception e) {
            log.error("Failed to select guild", e);
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", "Internal error"));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(HttpSession session) {
        AuthSession user = (AuthSession) session.getAttribute("user");
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "error", "Not authenticated"));
        }
        return ResponseEntity.ok(Map.of("success", true, "user", user));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.ok(Map.of("success", true));
    }
}
