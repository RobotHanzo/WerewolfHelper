package dev.robothanzo.werewolf.websocket

import dev.robothanzo.werewolf.security.DashboardRoleService
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.http.server.ServletServerHttpRequest
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry
import org.springframework.web.socket.server.HandshakeInterceptor
import org.springframework.context.annotation.Configuration

/**
 * Registers the per-guild WS endpoint at `/ws`. The handshake authenticates: the client must be
 * logged in and authorized to view the requested guild; otherwise the upgrade is rejected.
 */
@Configuration
@EnableWebSocket
class WebSocketConfig(
    private val handler: GameWebSocketHandler,
    private val roleService: DashboardRoleService,
) : WebSocketConfigurer {

    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(handler, "/ws")
            .addInterceptors(AuthHandshakeInterceptor(roleService))
            .setAllowedOriginPatterns("*")
    }
}

/**
 * Copies the authenticated user from the HTTP session onto the WS attributes and authorizes the
 * requested guild at handshake time (privileged users only, guild match).
 */
class AuthHandshakeInterceptor(private val roleService: DashboardRoleService) : HandshakeInterceptor {

    override fun beforeHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        attributes: MutableMap<String, Any>,
    ): Boolean {
        val servletRequest = (request as? ServletServerHttpRequest)?.servletRequest ?: return false
        val guildId = request.uri.query
            ?.split("&")
            ?.firstOrNull { it.startsWith("guildId=") }
            ?.substringAfter("=")
            ?.toLongOrNull()
            ?: return false

        val userId = (servletRequest.getSession(false)?.getAttribute("wh.userId") as? String)?.toLongOrNull()
            ?: return false
        if (!roleService.canView(guildId, userId)) return false

        attributes[GameWebSocketHandler.ATTR_GUILD_ID] = guildId
        attributes[GameWebSocketHandler.ATTR_USER_ID] = userId
        return true
    }

    override fun afterHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        exception: Exception?,
    ) {
    }
}
