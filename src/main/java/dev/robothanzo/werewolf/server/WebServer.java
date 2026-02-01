package dev.robothanzo.werewolf.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Updates;
import dev.robothanzo.werewolf.database.documents.AuthSession;
import dev.robothanzo.werewolf.database.documents.Session;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.HandlerType;
import io.javalin.http.UnauthorizedResponse;
import io.javalin.websocket.WsContext;
import io.mokulu.discord.oauth.DiscordAPI;
import io.mokulu.discord.oauth.DiscordOAuth;
import io.mokulu.discord.oauth.model.TokensResponse;
import io.mokulu.discord.oauth.model.User;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.mongodb.client.model.Filters.eq;

@Slf4j
public class WebServer implements Runnable {
    // OAuth Configuration
    private static final String CLIENT_ID = System.getenv().getOrDefault("DISCORD_CLIENT_ID", "");
    private static final String CLIENT_SECRET = System.getenv().getOrDefault("DISCORD_CLIENT_SECRET", "");
    private static final String REDIRECT_URI = System.getenv().getOrDefault("DISCORD_REDIRECT_URI", "http://localhost:5173/auth/callback");
    private final int port;
    private final Set<WsContext> wsClients = ConcurrentHashMap.newKeySet();
    private final ObjectMapper objectMapper;
    private final DiscordOAuth discordOAuth;
    private Javalin app;
    private JDA jda;

    public WebServer(int port) {
        this.port = port;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.discordOAuth = new DiscordOAuth(CLIENT_ID, CLIENT_SECRET, REDIRECT_URI, new String[]{"identify", "guilds", "guilds.members.read"});
    }

    public void setJDA(JDA jda) {
        this.jda = jda;
    }

    @Override
    public void run() {
        start();
    }

    public void start() {
        app = Javalin.create(config -> {
            // Static file serving for dashboard (optional - use Vite dev server during development)
            // Uncomment after building dashboard with: cd src/dashboard && yarn build
            // config.staticFiles.add(staticFiles -> {
            //     staticFiles.hostedPath = "/";
            //     staticFiles.directory = "/dashboard";
            //     staticFiles.location = Location.CLASSPATH;
            // });
        }).start(port);

        // Manual CORS configuration - set headers explicitly
        app.before(ctx -> {
            String origin = ctx.header("Origin");
            if (origin != null) {
                if (origin.equals("http://localhost:5173") ||
                        origin.equals("https://wolf.robothanzo.dev") ||
                        origin.equals("http://wolf.robothanzo.dev")) {
                    ctx.header("Access-Control-Allow-Origin", origin);
                }
            } else {
                ctx.header("Access-Control-Allow-Origin", "*");
            }

            ctx.header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            ctx.header("Access-Control-Allow-Headers", "Origin, Content-Type, Accept, Authorization");
            ctx.header("Access-Control-Allow-Credentials", "true");
        });

        // Handle preflight OPTIONS requests
        app.options("/*", ctx -> {
            ctx.status(204);
        });

        log.info("Web server started on port {}", port);

        // WebSocket endpoint
        app.ws("/ws", ws -> {
            ws.onConnect(ctx -> {
                ctx.session.setIdleTimeout(Duration.ofMinutes(20));
                wsClients.add(ctx);
                log.info("WebSocket client connected. Total clients: {}", wsClients.size());
            });

            ws.onClose(ctx -> {
                wsClients.remove(ctx);
                log.info("WebSocket client disconnected. Total clients: {}", wsClients.size());
            });

            ws.onMessage(ctx -> {
                try {
                    String message = ctx.message();
                    if (message.contains("\"type\":\"PING\"")) {
                        ctx.send("{\"type\":\"PONG\"}");
                    }
                } catch (Exception e) {
                    log.error("WebSocket message handling error", e);
                }
            });

            ws.onError(ctx -> {
                log.error("WebSocket error", ctx.error());
                wsClients.remove(ctx);
            });
        });

        // Global Exception Handler
        app.exception(Exception.class, (e, ctx) -> {
            log.error("Unhandled exception: " + e.getMessage(), e);
            ctx.status(500);
            ctx.json(Map.of(
                    "success", false,
                    "error", e.getMessage() != null ? e.getMessage() : "Internal Server Error"
            ));
        });

        // API Routes
        setupApiRoutes();
    }

    // Helper method to determine user role
    private String determineUserRole(String userId, long guildId) {
        try {
            net.dv8tion.jda.api.entities.Guild guild = jda.getGuildById(guildId);
            if (guild == null) {
                return "BLOCKED";
            }

            Member member = guild.getMemberById(userId);
            if (member == null) {
                return "BLOCKED";
            }

            // Check if user is admin (Judge)
            if (member.hasPermission(Permission.ADMINISTRATOR)) {
                return "JUDGE";
            }

            // Find session
            Session session = Session.fetchCollection().find(eq("guildId", guildId)).first();
            if (session == null) {
                return "SPECTATOR";
            }

            // Check if user is an active player
            for (Session.Player player : session.getPlayers().values()) {
                if (player.getUserId() != null && String.valueOf(player.getUserId()).equals(userId)) {
                    if (player.isAlive()) {
                        return "BLOCKED"; // Active players can't access
                    }
                }
            }

            return "SPECTATOR"; // Dead players or non-players can spectate
        } catch (Exception e) {
            log.error("Error determining user role", e);
            return "BLOCKED";
        }
    }

    private void setupApiRoutes() {
        // OAuth Login Endpoint
        app.get("/api/auth/login", ctx -> {
            String guildId = ctx.queryParam("guild_id");

            // guild_id is now optional - if not provided, user logs in first then selects server
            String state = guildId != null ? guildId : "no_guild";
            String authUrl = discordOAuth.getAuthorizationURL(state);
            ctx.redirect(authUrl);
        });

        // OAuth Callback Endpoint
        app.get("/api/auth/callback", ctx -> {
            String code = ctx.queryParam("code");
            String state = ctx.queryParam("state");

            log.info("OAuth callback received - code: {}, state: {}", code != null ? "present" : "null", state);

            if (code == null || state == null) {
                ctx.status(400).json(Map.of("success", false, "error", "Invalid callback"));
                return;
            }

            try {
                // Exchange code for token
                log.info("Exchanging code for token...");
                log.info("Using Redirect URI: {}", REDIRECT_URI);
                log.info("Client ID present: {}", !CLIENT_ID.isEmpty());
                log.info("Client Secret present: {}", !CLIENT_SECRET.isEmpty());
                TokensResponse tokenResponse = discordOAuth.getTokens(code);
                DiscordAPI discordAPI = new DiscordAPI(tokenResponse.getAccessToken());
                User user = discordAPI.fetchUser();
                log.info("User fetched: {} ({})", user.getUsername(), user.getId());

                // Create session
                String sessionId = UUID.randomUUID().toString();
                log.info("Creating session with ID: {}", sessionId);

                // If state is "no_guild", create session without role (role will be determined when accessing specific guild)
                if ("no_guild".equals(state)) {
                    log.info("Creating session without guild (PENDING role)");
                    AuthSession authSession = AuthSession.builder()
                            .sessionId(sessionId)
                            .userId(user.getId())
                            .username(user.getUsername())
                            .discriminator(user.getDiscriminator())
                            .avatar(user.getAvatar())
                            .guildId(0)
                            .role("PENDING")
                            .createdAt(new java.util.Date())
                            .build();

                    AuthSession.fetchCollection().insertOne(authSession);

                    // Set cookie (7 days)
                    ctx.cookie("session_id", sessionId, 60 * 60 * 24 * 7);
                    log.info("Cookie set: session_id={}", sessionId);

                    // Redirect to server selector
                    String dashboardUrl = System.getenv().getOrDefault("DASHBOARD_URL", "http://localhost:5173");
                    log.info("Redirecting to: {}", dashboardUrl + "/");
                    ctx.redirect(dashboardUrl + "/");
                } else {
                    log.info("Creating session with guild: {}", state);
                    // Legacy flow: state contains guild_id
                    long guildId = Long.parseLong(state);
                    String role = determineUserRole(user.getId(), guildId);

                    AuthSession authSession = AuthSession.builder()
                            .sessionId(sessionId)
                            .userId(user.getId())
                            .username(user.getUsername())
                            .discriminator(user.getDiscriminator())
                            .avatar(user.getAvatar())
                            .guildId(guildId)
                            .role(role)
                            .createdAt(new java.util.Date())
                            .build();

                    AuthSession.fetchCollection().insertOne(authSession);

                    // Set cookie (7 days)
                    ctx.cookie("session_id", sessionId, 60 * 60 * 24 * 7);
                    log.info("Cookie set: session_id={}", sessionId);

                    // Redirect to dashboard
                    String dashboardUrl = System.getenv().getOrDefault("DASHBOARD_URL", "http://localhost:5173");
                    log.info("Redirecting to: {}/server/{}", dashboardUrl, guildId);
                    ctx.redirect(dashboardUrl + "/server/" + guildId);
                }
            } catch (Exception e) {
                log.error("OAuth callback error", e);
                ctx.status(500).json(Map.of("success", false, "error", e.getMessage()));
            }
        });

        // Get current user session
        app.get("/api/auth/me", ctx -> {
            String sessionId = ctx.cookie("session_id");
            log.info("Auth check - session_id cookie: {}", sessionId);

            UserSession session = getUserSession(ctx);
            if (session == null) {
                log.warn("No session found for session_id: {}", sessionId);
                ctx.status(401).json(Map.of("success", false, "error", "Not authenticated"));
                return;
            }

            log.info("Session found for user: {}", session.username());
            ctx.json(Map.of(
                    "success", true,
                    "user", Map.of(
                            "userId", session.userId(),
                            "username", session.username(),
                            "avatar", session.avatar() != null ? session.avatar() : "",
                            "guildId", String.valueOf(session.guildId()),
                            "role", session.role()
                    )
            ));
        });

        // Select a guild and update user's role
        app.post("/api/auth/select-guild/{guildId}", ctx -> {
            UserSession session = getUserSession(ctx);
            if (session == null) {
                ctx.status(401).json(Map.of("success", false, "error", "Not authenticated"));
                return;
            }

            long guildId = Long.parseLong(ctx.pathParam("guildId"));
            log.info("User {} selecting guild {}", session.userId(), guildId);

            // Determine role for this guild
            String role = determineUserRole(session.userId(), guildId);
            log.info("Determined role for user {} in guild {}: {}", session.userId(), guildId, role);

            // Update session with new role and guildId
            UserSession updatedSession = new UserSession(
                    session.sessionId(),
                    session.userId(),
                    session.username(),
                    session.discriminator(),
                    session.avatar(),
                    guildId,
                    role,
                    System.currentTimeMillis()
            );

            // Update session in MongoDB
            AuthSession.fetchCollection().updateOne(
                    eq("sessionId", session.sessionId()),
                    Updates.combine(
                            Updates.set("guildId", guildId),
                            Updates.set("role", role)
                    )
            );
            log.info("Updated session for user {} with role {} in guild {}", session.userId(), role, guildId);

            ctx.json(Map.of(
                    "success", true,
                    "user", Map.of(
                            "userId", updatedSession.userId(),
                            "username", updatedSession.username(),
                            "avatar", updatedSession.avatar(),
                            "guildId", String.valueOf(updatedSession.guildId()),
                            "role", updatedSession.role()
                    )
            ));
        });

        // Logout
        app.post("/api/auth/logout", ctx -> {
            String sessionId = ctx.cookie("session_id");
            if (sessionId != null) {
                AuthSession.fetchCollection().deleteOne(eq("sessionId", sessionId));
            }
            ctx.removeCookie("session_id");
            ctx.json(Map.of("success", true));
        });

        // Permission middleware for API endpoints
        app.before("/api/*", ctx -> {
            String path = ctx.path();
            log.info("Middleware check: {} {}", ctx.method(), path);

            // 1. Allow all auth routes (login, callback, me, logout, select-guild)
            if (path.startsWith("/api/auth/")) {
                return;
            }

            // 2. Allow GET /api/sessions (the list of all available games)
            // This is needed for the server selection page.
            if (ctx.method() == HandlerType.GET && path.equals("/api/sessions")) {
                return;
            }

            // 3. For everything else, require authentication
            UserSession session = getUserSession(ctx);
            if (session == null) {
                log.warn("Unauthorized access attempt: {} {}", ctx.method(), path);
                throw new UnauthorizedResponse("Not authenticated");
            }

            // 4. Guild-specific data protection
            // Paths like /api/sessions/{guildId}/**
            if (path.startsWith("/api/sessions/")) {
                String[] parts = path.split("/");
                // path is /api/sessions/{guildId}/...
                // parts[0] is "", parts[1] is "api", parts[2] is "sessions", parts[3] is {guildId}
                if (parts.length >= 4) {
                    String guildIdStr = parts[3];

                    // Enforce session matches the requested guild
                    if (!String.valueOf(session.guildId()).equals(guildIdStr)) {
                        log.warn("User {} tried to access guild {} while active in guild {}", session.userId(), guildIdStr, session.guildId());
                        throw new ForbiddenResponse("Please switch to this server first.");
                    }

                    // Enforce role permission for this guild
                    if (ctx.method() == HandlerType.GET) {
                        // GET requires JUDGE or SPECTATOR
                        if (!"JUDGE".equals(session.role()) && !"SPECTATOR".equals(session.role())) {
                            log.warn("User {} (role {}) denied GET on guild {}", session.userId(), session.role(), guildIdStr);
                            throw new ForbiddenResponse("Access denied. Active players cannot view management data.");
                        }
                    } else {
                        // Mutations (POST, PUT, DELETE) require JUDGE
                        if (!"JUDGE".equals(session.role())) {
                            log.warn("User {} (role {}) denied {} on guild {}", session.userId(), session.role(), ctx.method(), guildIdStr);
                            throw new ForbiddenResponse("Insufficient permissions. Only judges can modify game state.");
                        }
                    }
                }
            }
        });

        // List all sessions
        app.get("/api/sessions", ctx -> {
            List<Map<String, Object>> sessions = new ArrayList<>();
            try (MongoCursor<Session> cursor = Session.fetchCollection().find().iterator()) {
                while (cursor.hasNext()) {
                    Session session = cursor.next();
                    sessions.add(SessionAPI.toJSON(session, jda));
                }
            }
            ctx.json(Map.of("success", true, "data", sessions));
        });

        // Get specific session
        app.get("/api/sessions/{guildId}", ctx -> {
            long guildId = Long.parseLong(ctx.pathParam("guildId"));
            Session session = Session.fetchCollection().find(eq("guildId", guildId)).first();

            if (session == null) {
                ctx.status(404);
                ctx.json(Map.of("success", false, "error", "找不到該伺服器的遊戲"));
                return;
            }

            ctx.json(Map.of("success", true, "data", SessionAPI.toJSON(session, jda)));
        });

        // Get players for a session
        app.get("/api/sessions/{guildId}/players", ctx -> {
            long guildId = Long.parseLong(ctx.pathParam("guildId"));
            Session session = Session.fetchCollection().find(eq("guildId", guildId)).first();

            if (session == null) {
                ctx.status(404);
                ctx.json(Map.of("success", false, "error", "找不到該伺服器的遊戲"));
                return;
            }

            ctx.json(Map.of("success", true, "data", SessionAPI.playersToJSON(session, jda)));
        });

        // Get guild members (potential judges)
        app.get("/api/sessions/{guildId}/members", ctx -> {
            long guildId = Long.parseLong(ctx.pathParam("guildId"));
            Session session = Session.fetchCollection().find(eq("guildId", guildId)).first();

            if (session == null) {
                ctx.status(404);
                ctx.json(Map.of("success", false, "error", "找不到該伺服器的遊戲"));
                return;
            }

            try {
                ctx.json(Map.of("success", true, "data", SessionAPI.getGuildMembers(guildId, jda)));
            } catch (Exception e) {
                log.error("Failed to fetch guild members", e);
                ctx.status(500);
                ctx.json(Map.of("success", false, "error", e.getMessage()));
            }
        });

        // Assign roles
        app.post("/api/sessions/{guildId}/players/assign", ctx -> {
            long guildId = Long.parseLong(ctx.pathParam("guildId"));

            try {
                SessionAPI.assignRoles(guildId, jda,
                        msg -> {
                            log.info("Broadcasting progress message for guild {}: {}", guildId, msg);
                            broadcastEvent("PROGRESS", Map.of("message", msg, "guildId", String.valueOf(guildId)));
                        },
                        p -> {
                            log.info("Broadcasting progress percentage for guild {}: {}%", guildId, p);
                            broadcastEvent("PROGRESS", Map.of("percent", p, "guildId", String.valueOf(guildId)));
                        }
                );

                Session updated = Session.fetchCollection().find(eq("guildId", guildId)).first();
                if (updated != null) {
                    broadcastSessionUpdate(updated);
                }
                ctx.json(Map.of("success", true, "message", "角色已分配"));
            } catch (Exception e) {
                log.error("Failed to assign roles", e);
                ctx.status(500);
                ctx.json(Map.of("success", false, "error", e.getMessage()));
            }
        });

        // Reset session
        app.post("/api/sessions/{guildId}/reset", ctx -> {
            long guildId = Long.parseLong(ctx.pathParam("guildId"));

            try {
                SessionAPI.resetSession(guildId, jda,
                        msg -> {
                            log.info("Broadcasting reset progress for guild {}: {}", guildId, msg);
                            broadcastEvent("PROGRESS", Map.of("message", msg, "guildId", String.valueOf(guildId)));
                        },
                        p -> {
                            log.info("Broadcasting reset percentage for guild {}: {}%", guildId, p);
                            broadcastEvent("PROGRESS", Map.of("percent", p, "guildId", String.valueOf(guildId)));
                        }
                );

                Session updated = Session.fetchCollection().find(eq("guildId", guildId)).first();
                if (updated != null) {
                    broadcastSessionUpdate(updated);
                }
                ctx.json(Map.of("success", true, "message", "遊戲已重置"));
            } catch (Exception e) {
                log.error("Failed to reset session", e);
                ctx.status(500);
                ctx.json(Map.of("success", false, "error", e.getMessage()));
            }
        });

        // Mark player as dead
        app.post("/api/sessions/{guildId}/players/{userId}/died", ctx -> {
            long guildId = Long.parseLong(ctx.pathParam("guildId"));
            long userId = Long.parseLong(ctx.pathParam("userId"));
            String lastWordsParam = ctx.queryParam("lastWords");
            boolean lastWords = Boolean.parseBoolean(lastWordsParam);

            try {
                SessionAPI.markPlayerDead(guildId, userId, lastWords, jda);
                Session updated = Session.fetchCollection().find(eq("guildId", guildId)).first();
                if (updated != null) {
                    broadcastSessionUpdate(updated);
                }
                ctx.json(Map.of("success", true, "message", "玩家已標記為死亡"));
            } catch (Exception e) {
                log.error("Failed to mark player as dead", e);
                ctx.status(500);
                ctx.json(Map.of("success", false, "error", e.getMessage()));
            }
        });

        // Revive player (all roles)
        app.post("/api/sessions/{guildId}/players/{userId}/revive", ctx -> {
            long guildId = Long.parseLong(ctx.pathParam("guildId"));
            long userId = Long.parseLong(ctx.pathParam("userId"));

            try {
                SessionAPI.revivePlayer(guildId, userId, jda);
                Session updated = Session.fetchCollection().find(eq("guildId", guildId)).first();
                if (updated != null) {
                    broadcastSessionUpdate(updated);
                }
                ctx.json(Map.of("success", true, "message", "玩家已完全復活"));
            } catch (Exception e) {
                log.error("Failed to revive player", e);
                ctx.status(500);
                ctx.json(Map.of("success", false, "error", e.getMessage()));
            }
        });

        // Revive specific role
        app.post("/api/sessions/{guildId}/players/{userId}/revive-role", ctx -> {
            long guildId = Long.parseLong(ctx.pathParam("guildId"));
            long userId = Long.parseLong(ctx.pathParam("userId"));
            String role = ctx.queryParam("role");

            if (role == null || role.isEmpty()) {
                ctx.status(400);
                ctx.json(Map.of("success", false, "error", "Role parameter is required"));
                return;
            }

            try {
                SessionAPI.revivePlayerRole(guildId, userId, role, jda);
                Session updated = Session.fetchCollection().find(eq("guildId", guildId)).first();
                if (updated != null) {
                    broadcastSessionUpdate(updated);
                }
                ctx.json(Map.of("success", true, "message", "角色已復活"));
            } catch (Exception e) {
                log.error("Failed to revive role", e);
                ctx.status(500);
                ctx.json(Map.of("success", false, "error", e.getMessage()));
            }
        });

        // Set police
        app.post("/api/sessions/{guildId}/players/{userId}/police", ctx -> {
            long guildId = Long.parseLong(ctx.pathParam("guildId"));
            long userId = Long.parseLong(ctx.pathParam("userId"));

            try {
                SessionAPI.setPolice(guildId, userId, jda);
                Session updated = Session.fetchCollection().find(eq("guildId", guildId)).first();
                if (updated != null) {
                    broadcastSessionUpdate(updated);
                }
                ctx.json(Map.of("success", true, "message", "警長已設定"));
            } catch (Exception e) {
                log.error("Failed to set police", e);
                ctx.status(500);
                ctx.json(Map.of("success", false, "error", e.getMessage()));
            }
        });

        // Update player roles
        app.post("/api/sessions/{guildId}/players/{playerId}/roles", ctx -> {
            long guildId = Long.parseLong(ctx.pathParam("guildId"));
            String playerId = ctx.pathParam("playerId");
            List<String> roles = ctx.bodyAsClass(List.class);

            try {
                SessionAPI.updatePlayerRoles(guildId, playerId, roles, jda);
                Session updated = Session.fetchCollection().find(eq("guildId", guildId)).first();
                if (updated != null) {
                    broadcastSessionUpdate(updated);
                }
                ctx.json(Map.of("success", true, "message", "玩家角色已更新"));
            } catch (Exception e) {
                log.error("Failed to update player roles", e);
                ctx.status(500);
                ctx.json(Map.of("success", false, "error", e.getMessage()));
            }
        });




        // Update user Discord role (Judge/Spectator)
        app.post("/api/sessions/{guildId}/players/{userId}/role", ctx -> {
            long guildId = Long.parseLong(ctx.pathParam("guildId"));
            long userId = Long.parseLong(ctx.pathParam("userId"));
            Map<String, String> body = ctx.bodyAsClass(Map.class);
            String role = body.get("role");

            if (role == null) {
                ctx.status(400);
                ctx.json(Map.of("success", false, "error", "Role is required"));
                return;
            }

            try {
                SessionAPI.updateUserRole(guildId, userId, role, jda);
                ctx.json(Map.of("success", true, "message", "使用者身分已更新"));
            } catch (Exception e) {
                log.error("Failed to update user role", e);
                ctx.status(500);
                ctx.json(Map.of("success", false, "error", e.getMessage()));
            }
        });

        // Switch role order
        app.post("/api/sessions/{guildId}/players/{userId}/switch-role-order", ctx -> {
            long guildId = Long.parseLong(ctx.pathParam("guildId"));
            long userId = Long.parseLong(ctx.pathParam("userId"));

            try {
                SessionAPI.switchRoleOrder(guildId, userId, jda);
                Session updated = Session.fetchCollection().find(eq("guildId", guildId)).first();
                if (updated != null) {
                    broadcastSessionUpdate(updated);
                }
                ctx.json(Map.of("success", true, "message", "角色順序已交換"));
            } catch (Exception e) {
                log.error("Failed to switch role order", e);
                ctx.status(500);
                ctx.json(Map.of("success", false, "error", e.getMessage()));
            }
        });


        // Police Enroll Speech
        app.post("/api/sessions/{guildId}/speech/police-enroll", ctx -> {
            long guildId = Long.parseLong(ctx.pathParam("guildId"));
            Session session = Session.fetchCollection().find(eq("guildId", guildId)).first();

            if (session == null) {
                ctx.status(404);
                ctx.json(Map.of("success", false, "error", "Session not found"));
                return;
            }

            net.dv8tion.jda.api.entities.Guild guild = jda.getGuildById(guildId);
            if (guild == null) {
                ctx.status(404);
                ctx.json(Map.of("success", false, "error", "Guild not found"));
                return;
            }

            net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel channel =
                    guild.getTextChannelById(session.getCourtTextChannelId());

            if (channel == null) {
                ctx.status(400); // Bad Request / Configuration
                ctx.json(Map.of("success", false, "error", "Court channel not found"));
                return;
            }

            try {
                dev.robothanzo.werewolf.commands.Poll.Police.startEnrollment(session, channel, null);
                ctx.json(Map.of("success", true, "message", "警長參選流程已啟動"));
            } catch (Exception e) {
                log.error("Failed to start police enroll", e);
                ctx.status(500);
                ctx.json(Map.of("success", false, "error", e.getMessage()));
            }
        });

        // Set role position lock
        app.post("/api/sessions/{guildId}/players/{userId}/role-lock", ctx -> {
            long guildId = Long.parseLong(ctx.pathParam("guildId"));
            String userId = ctx.pathParam("userId");
            String lockedParam = ctx.queryParam("locked");
            boolean locked = Boolean.parseBoolean(lockedParam);

            try {
                SessionAPI.setRolePositionLock(guildId, userId, locked); // userId here is actually 'id' (player ID string key) based on existing API pattern for updatePlayerRoles?
                // Wait, updatePlayerRoles uses "playerId" which maps to the key in players map (string).
                // markPlayerDead uses "userId" (long) which seems to be Discord User ID?
                // SessionAPI.setRolePositionLock takes String playerId.
                // In setupApiRoutes, updatePlayerRoles uses pathParam("playerId") and passes it.
                // Let's verify if markPlayerDead uses userId (Long) or playerId (String).
                // markPlayerDead takes (long guildId, long userId, ...).
                // updatePlayerRoles takes (long guildId, String playerId, ...).
                // Session.players map key is likely String version of ID or something?
                // Session.java: Map<String, Player> players.
                // In playersToJSON: id is String.valueOf(player.getId()).
                // Let's assume typical usage is player ID (int/string) for managing player entity, and UserID for discord actions.
                // setRolePositionLock implementation I added takes String playerId. So I should use that.

                Session updated = Session.fetchCollection().find(eq("guildId", guildId)).first();
                if (updated != null) {
                    broadcastSessionUpdate(updated);
                }
                ctx.json(Map.of("success", true, "message", "角色鎖定狀態已更新"));
            } catch (Exception e) {
                log.error("Failed to set role position lock", e);
                ctx.status(500);
                ctx.json(Map.of("success", false, "error", e.getMessage()));
            }
        });

        // Get roles for session
        app.get("/api/sessions/{guildId}/roles", ctx -> {
            long guildId = Long.parseLong(ctx.pathParam("guildId"));
            Session session = Session.fetchCollection().find(eq("guildId", guildId)).first();

            if (session == null) {
                ctx.status(404);
                ctx.json(Map.of("success", false, "error", "找不到該伺服器的遊戲"));
                return;
            }

            ctx.json(Map.of("success", true, "data", session.getRoles()));
        });

        // Add role
        app.post("/api/sessions/{guildId}/roles/add", ctx -> {
            long guildId = Long.parseLong(ctx.pathParam("guildId"));
            String role = ctx.queryParam("role");
            String amountParam = ctx.queryParam("amount");
            int amount = amountParam != null ? Integer.parseInt(amountParam) : 1;

            try {
                SessionAPI.addRole(guildId, role, amount);
                Session updated = Session.fetchCollection().find(eq("guildId", guildId)).first();
                if (updated != null) {
                    broadcastSessionUpdate(updated);
                }
                ctx.json(Map.of("success", true, "message", "角色已新增"));
            } catch (Exception e) {
                log.error("Failed to add role", e);
                ctx.status(500);
                ctx.json(Map.of("success", false, "error", e.getMessage()));
            }
        });

        // Remove role
        app.delete("/api/sessions/{guildId}/roles/{role}", ctx -> {
            long guildId = Long.parseLong(ctx.pathParam("guildId"));
            String role = ctx.pathParam("role");
            String amountParam = ctx.queryParam("amount");
            int amount = amountParam != null ? Integer.parseInt(amountParam) : 1;

            try {
                SessionAPI.removeRole(guildId, role, amount);
                Session updated = Session.fetchCollection().find(eq("guildId", guildId)).first();
                if (updated != null) {
                    broadcastSessionUpdate(updated);
                }
                ctx.json(Map.of("success", true, "message", "角色已移除"));
            } catch (Exception e) {
                log.error("Failed to remove role", e);
                ctx.status(500);
                ctx.json(Map.of("success", false, "error", e.getMessage()));
            }
        });


        // Start game
        app.post("/api/sessions/{guildId}/start", ctx -> {
            long guildId = Long.parseLong(ctx.pathParam("guildId"));

            try {
                SessionAPI.startGame(guildId);
                Session updated = Session.fetchCollection().find(eq("guildId", guildId)).first();
                if (updated != null) {
                    broadcastSessionUpdate(updated);
                }
                ctx.json(Map.of("success", true, "message", "遊戲已開始"));
            } catch (Exception e) {
                log.error("Failed to start game", e);
                ctx.status(500);
                ctx.json(Map.of("success", false, "error", e.getMessage()));
            }
        });


        // Update settings
        app.put("/api/sessions/{guildId}/settings", ctx -> {
            long guildId = Long.parseLong(ctx.pathParam("guildId"));

            try {
                Map<String, Object> settings = ctx.bodyAsClass(Map.class);
                SessionAPI.updateSettings(guildId, settings);
                Session updated = Session.fetchCollection().find(eq("guildId", guildId)).first();
                if (updated != null) {
                    broadcastSessionUpdate(updated);
                }
                ctx.json(Map.of("success", true, "message", "設定已更新"));
            } catch (Exception e) {
                log.error("Failed to update settings", e);
                ctx.status(500);
                ctx.json(Map.of("success", false, "error", e.getMessage()));
            }
        });

        // Set player count
        app.post("/api/sessions/{guildId}/player-count", ctx -> {
            long guildId = Long.parseLong(ctx.pathParam("guildId"));
            Map<String, Object> body = ctx.bodyAsClass(Map.class);
            
            if (!body.containsKey("count")) {
                ctx.status(400);
                ctx.json(Map.of("success", false, "error", "Count is required"));
                return;
            }

            int count = Integer.parseInt(body.get("count").toString());

            try {
                SessionAPI.setPlayerCount(guildId, count, jda);
                Session updated = Session.fetchCollection().find(eq("guildId", guildId)).first();
                if (updated != null) {
                    broadcastSessionUpdate(updated);
                }
                ctx.json(Map.of("success", true, "message", "Player count updated"));
            } catch (Exception e) {
                log.error("Failed to update player count", e);
                ctx.status(500);
                ctx.json(Map.of("success", false, "error", e.getMessage()));
            }
        });

        // Start auto speech
        app.post("/api/sessions/{guildId}/speech/auto", ctx -> {
            long guildId = Long.parseLong(ctx.pathParam("guildId"));
            try {
                Session session = Session.fetchCollection().find(eq("guildId", guildId)).first();
                if (session == null) {
                    ctx.status(404);
                    ctx.json(Map.of("success", false, "error", "Session not found"));
                    return;
                }

                net.dv8tion.jda.api.entities.channel.concrete.TextChannel channel = jda.getTextChannelById(session.getCourtTextChannelId());
                if (channel == null) {
                    ctx.status(400);
                    ctx.json(Map.of("success", false, "error", "Court channel not found"));
                    return;
                }

                net.dv8tion.jda.api.entities.Guild guild = jda.getGuildById(guildId);
                if (guild == null) {
                    ctx.status(404);
                    ctx.json(Map.of("success", false, "error", "Guild not found"));
                    return;
                }

                dev.robothanzo.werewolf.commands.Speech.startAutoSpeech(guild, channel, session);
                broadcastSessionUpdate(session);
                ctx.json(Map.of("success", true, "message", "Speech started"));
            } catch (Exception e) {
                log.error("Failed to start speech", e);
                ctx.status(500);
                ctx.json(Map.of("success", false, "error", e.getMessage()));
            }
        });

        // Set speech order (Override)
        app.post("/api/sessions/{guildId}/speech/order", ctx -> {
            long guildId = Long.parseLong(ctx.pathParam("guildId"));
            Map<String, String> body = ctx.bodyAsClass(Map.class);
            String direction = body.get("direction");

            if (direction == null || (!direction.equalsIgnoreCase("UP") && !direction.equalsIgnoreCase("DOWN"))) {
                ctx.status(400);
                ctx.json(Map.of("success", false, "error", "Invalid direction. Must be UP or DOWN."));
                return;
            }

            try {
                Session session = Session.fetchCollection().find(eq("guildId", guildId)).first();
                if (session == null) {
                    ctx.status(404);
                    ctx.json(Map.of("success", false, "error", "Session not found"));
                    return;
                }

                net.dv8tion.jda.api.entities.Guild guild = jda.getGuildById(guildId);
                if (guild == null) {
                    ctx.status(404);
                    ctx.json(Map.of("success", false, "error", "Guild not found"));
                    return;
                }

                if (!dev.robothanzo.werewolf.commands.Speech.speechSessions.containsKey(guildId)) {
                    ctx.status(400);
                    ctx.json(Map.of("success", false, "error", "No active speech session"));
                    return;
                }

                // Find police
                Session.Player police = null;
                for (Session.Player p : session.fetchAlivePlayers().values()) {
                    if (p.isPolice()) {
                        police = p;
                        break;
                    }
                }

                if (police == null) {
                    // Start random if no police? Or just error?
                    // If no police, autostart should have handled it.
                    // But maybe we force random?
                    // Let's assume we map UP/DOWN to a random player if no police, or just error "No police to pivot around".
                    // Actually, let's pick the first player as pivot if no police, similar to random.
                    List<Session.Player> players = new LinkedList<>(session.fetchAlivePlayers().values());
                    if (players.isEmpty()) {
                        ctx.status(400);
                        ctx.json(Map.of("success", false, "error", "No alive players"));
                        return;
                    }
                    police = players.get(0); // arbitrary pivot
                }

                dev.robothanzo.werewolf.commands.Speech.Order order = dev.robothanzo.werewolf.commands.Speech.Order.valueOf(direction.toUpperCase());
                dev.robothanzo.werewolf.commands.Speech.changeOrder(guild, order, session.fetchAlivePlayers().values(), police);

                // Notify Discord
                net.dv8tion.jda.api.entities.channel.concrete.TextChannel channel = guild.getTextChannelById(session.getCourtTextChannelId());
                if (channel != null) {
                    channel.sendMessage("法官已由網頁後台設定發言順序為: " + order).queue();
                }

                // Start the flow immediately
                dev.robothanzo.werewolf.commands.Speech.speechSessions.get(guildId).next();

                broadcastSessionUpdate(session);
                ctx.json(Map.of("success", true, "message", "Speech order set to " + direction));
            } catch (Exception e) {
                log.error("Failed to set speech order", e);
                ctx.status(500);
                ctx.json(Map.of("success", false, "error", e.getMessage()));
            }
        });

        // Confirm/Start speech (Web equivalent of confirmOrder)
        app.post("/api/sessions/{guildId}/speech/confirm", ctx -> {
            long guildId = Long.parseLong(ctx.pathParam("guildId"));
            try {
                if (!dev.robothanzo.werewolf.commands.Speech.speechSessions.containsKey(guildId)) {
                    ctx.status(400);
                    ctx.json(Map.of("success", false, "error", "No active speech session"));
                    return;
                }

                dev.robothanzo.werewolf.commands.Speech.SpeechSession speechSession = dev.robothanzo.werewolf.commands.Speech.speechSessions.get(guildId);
                if (speechSession.getOrder().isEmpty()) {
                    ctx.status(400);
                    ctx.json(Map.of("success", false, "error", "Order not set"));
                    return;
                }

                speechSession.next();

                Session session = Session.fetchCollection().find(eq("guildId", guildId)).first();
                if (session != null) broadcastSessionUpdate(session);

                ctx.json(Map.of("success", true, "message", "Speech confirmed and started"));
            } catch (Exception e) {
                log.error("Failed to confirm speech", e);
                ctx.status(500);
                ctx.json(Map.of("success", false, "error", e.getMessage()));
            }
        });

        // Skip current speaker
        app.post("/api/sessions/{guildId}/speech/skip", ctx -> {
            long guildId = Long.parseLong(ctx.pathParam("guildId"));
            try {
                dev.robothanzo.werewolf.commands.Speech.skip(guildId);
                ctx.json(Map.of("success", true, "message", "Skipped"));
            } catch (Exception e) {
                log.error("Failed to skip speech", e);
                ctx.status(500);
                ctx.json(Map.of("success", false, "error", e.getMessage()));
            }
        });

        // Interrupt speech
        app.post("/api/sessions/{guildId}/speech/interrupt", ctx -> {
            long guildId = Long.parseLong(ctx.pathParam("guildId"));
            try {
                dev.robothanzo.werewolf.commands.Speech.interrupt(guildId);
                ctx.json(Map.of("success", true, "message", "Interrupted"));
            } catch (Exception e) {
                log.error("Failed to interrupt speech", e);
                ctx.status(500);
                ctx.json(Map.of("success", false, "error", e.getMessage()));
            }
        });

        // Start manual timer
        app.post("/api/sessions/{guildId}/speech/manual-start", ctx -> {
            long guildId = Long.parseLong(ctx.pathParam("guildId"));
            Map<String, Object> body = ctx.bodyAsClass(Map.class);
            
            Session session = Session.fetchCollection().find(eq("guildId", guildId)).first();
            if (session == null) {
                ctx.status(404);
                ctx.json(Map.of("success", false, "error", "Session not found"));
                return;
            }

            net.dv8tion.jda.api.entities.Guild guild = jda.getGuildById(guildId);
            if (guild == null) {
                ctx.status(404);
                ctx.json(Map.of("success", false, "error", "Guild not found"));
                return;
            }

            net.dv8tion.jda.api.entities.channel.concrete.TextChannel channel = guild.getTextChannelById(session.getCourtTextChannelId());
            net.dv8tion.jda.api.entities.channel.middleman.AudioChannel voiceChannel = guild.getVoiceChannelById(session.getCourtVoiceChannelId());

            if (channel == null) {
                ctx.status(400);
                ctx.json(Map.of("success", false, "error", "Court channel not found"));
                return;
            }

            int duration = body.containsKey("duration") ? Integer.parseInt(body.get("duration").toString()) : 60;
            
            try {
                dev.robothanzo.werewolf.commands.Speech.startTimer(guild, channel, voiceChannel, duration);
                ctx.json(Map.of("success", true, "message", "Timer started"));
            } catch (Exception e) {
                log.error("Failed to start timer", e);
                ctx.status(500);
                ctx.json(Map.of("success", false, "error", e.getMessage()));
            }
        });

        // Mute All
        app.post("/api/sessions/{guildId}/speech/mute-all", ctx -> {
            long guildId = Long.parseLong(ctx.pathParam("guildId"));
            net.dv8tion.jda.api.entities.Guild guild = jda.getGuildById(guildId);
             if (guild == null) {
                ctx.status(404);
                ctx.json(Map.of("success", false, "error", "Guild not found"));
                return;
            }
            try {
                dev.robothanzo.werewolf.commands.Speech.muteAll(guild);
                ctx.json(Map.of("success", true, "message", "Muted all"));
            } catch (Exception e) {
                log.error("Failed to mute all", e);
                ctx.status(500);
                ctx.json(Map.of("success", false, "error", e.getMessage()));
            }
        });

        // Unmute All
        app.post("/api/sessions/{guildId}/speech/unmute-all", ctx -> {
            long guildId = Long.parseLong(ctx.pathParam("guildId"));
            net.dv8tion.jda.api.entities.Guild guild = jda.getGuildById(guildId);
             if (guild == null) {
                ctx.status(404);
                ctx.json(Map.of("success", false, "error", "Guild not found"));
                return;
            }
            try {
                dev.robothanzo.werewolf.commands.Speech.unmuteAll(guild);
                ctx.json(Map.of("success", true, "message", "Unmuted all"));
            } catch (Exception e) {
                log.error("Failed to unmute all", e);
                ctx.status(500);
                ctx.json(Map.of("success", false, "error", e.getMessage()));
            }
        });




        // Error handling
        app.exception(Exception.class, (e, ctx) -> {
            log.error("API error", e);
            ctx.status(500);
            ctx.json(Map.of("success", false, "error", e.getMessage()));
        });
    }

    public void broadcastSessionUpdate(Session session) {
        try {
            Map<String, Object> json = SessionAPI.toJSON(session, jda);
            String jsonString = objectMapper.writeValueAsString(json);
            wsClients.forEach(client -> {
                try {
                    client.send(jsonString);
                } catch (Exception e) {
                    log.error("Failed to send message to client", e);
                }
            });
        } catch (Exception e) {
            log.error("Failed to serialize session for broadcast", e);
        }
    }

    public void broadcastEvent(String type, Map<String, Object> data) {
        try {
            Map<String, Object> message = new HashMap<>(data);
            message.put("type", type);
            String jsonString = objectMapper.writeValueAsString(message);
            wsClients.forEach(client -> {
                try {
                    if (client.session.isOpen()) {
                        client.send(jsonString);
                    }
                } catch (Exception e) {
                    log.error("Failed to send event to client", e);
                }
            });
        } catch (Exception e) {
            log.error("Failed to serialize event for broadcast", e);
        }
    }

    public void stop() {
        if (app != null) {
            app.stop();
            log.info("Web server stopped");
        }
    }

    /**
     * Get user session from session cookie
     */
    private UserSession getUserSession(Context ctx) {
        String sessionId = ctx.cookie("session_id");
        if (sessionId == null) {
            return null;
        }

        AuthSession doc = AuthSession.fetchCollection().find(eq("sessionId", sessionId)).first();
        if (doc == null) return null;

        return new UserSession(
                doc.getSessionId(),
                doc.getUserId(),
                doc.getUsername(),
                doc.getDiscriminator(),
                doc.getAvatar(),
                doc.getGuildId(),
                doc.getRole(),
                doc.getCreatedAt().getTime()
        );
    }

    /**
     * @param role JUDGE, SPECTATOR, BLOCKED, PENDING
     */
    public record UserSession(String sessionId, String userId, String username, String discriminator, String avatar,
                              long guildId, String role, long createdAt) {
    }
}
