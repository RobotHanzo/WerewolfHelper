package dev.robothanzo.werewolf.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class GlobalWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalWebSocketHandler.class);

    // Maintain a list of sessions.
    // In a real app with auth, you might map userId -> Session or guildId ->
    // List<Session>
    private final CopyOnWriteArrayList<WebSocketSession> sessions = new CopyOnWriteArrayList<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Object userObj = session.getAttributes().get("user");
        if (!(userObj instanceof dev.robothanzo.werewolf.database.documents.AuthSession user)) {
            log.warn("Rejected WS connection: No user in session");
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        // Only allow connection if the user is a spectator or judge
        if (!user.isPrivileged()) {
            log.warn("Rejected WS connection: User {} has unauthorized role {}", user.getUserId(), user.getRole());
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        // Check if listening to selected server
        java.net.URI uri = session.getUri();
        String query = uri != null ? uri.getQuery() : null;
        String requestedGuildId = null;
        if (query != null) {
            requestedGuildId = java.util.stream.Stream.of(query.split("&"))
                    .filter(s -> s.startsWith("guildId="))
                    .map(s -> s.substring(8))
                    .findFirst()
                    .orElse(null);
        }

        if (requestedGuildId == null || !requestedGuildId.equals(user.getGuildId())) {
            log.warn("Rejected WS connection: Guild mismatch. Requested: {}, Authorized: {}", requestedGuildId,
                    user.getGuildId());
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        sessions.add(session);
        log.info("WebSocket connection established for user {} in guild {}: {}", user.getUserId(), user.getGuildId(),
                session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.info("WebSocket connection closed: " + session.getId());
    }

    public void broadcast(String message) {
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                synchronized (session) {
                    try {
                        session.sendMessage(new TextMessage(message));
                    } catch (IOException e) {
                        log.error("Failed to send message to session " + session.getId(), e);
                    }
                }
            }
        }
    }
}
