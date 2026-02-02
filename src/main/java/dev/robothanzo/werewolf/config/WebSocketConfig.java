package dev.robothanzo.werewolf.config;

import dev.robothanzo.werewolf.security.GlobalWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final GlobalWebSocketHandler globalWebSocketHandler;

    public WebSocketConfig(GlobalWebSocketHandler globalWebSocketHandler) {
        this.globalWebSocketHandler = globalWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(globalWebSocketHandler, "/ws")
                .addInterceptors(new org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor())
                .setAllowedOrigins(
                        "http://localhost:5173",
                        "https://wolf.robothanzo.dev",
                        "http://wolf.robothanzo.dev");
    }
}
