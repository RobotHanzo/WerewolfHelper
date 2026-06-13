package dev.robothanzo.werewolf.discord

import dev.robothanzo.werewolf.domain.repo.GameSessionRepository
import dev.robothanzo.werewolf.game.roles.RoleRegistry
import dev.robothanzo.werewolf.i18n.Msg
import dev.robothanzo.werewolf.ops.BulkOperationEngine
import dev.robothanzo.werewolf.websocket.GameWebSocketHandler
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** Discord configuration; everything is optional so the app boots without a bot. */
@ConfigurationProperties(prefix = "werewolf.discord")
data class DiscordProperties(
    val token: String = "",
    val clientId: String = "",
    val clientSecret: String = "",
    val redirectUri: String = "",
    /** Comma-separated allowlist of trusted server-creator user ids. */
    val serverCreators: String = "",
) {
    val hasToken: Boolean get() = token.isNotBlank()
    val serverCreatorIds: Set<Long>
        get() = serverCreators.split(",").mapNotNull { it.trim().toLongOrNull() }.toSet()
}

@Configuration
@EnableConfigurationProperties(DiscordProperties::class)
class DiscordConfig(private val properties: DiscordProperties) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Picks the live JDA gateway when a token is present, otherwise the no-op gateway. JDA
     * construction is guarded so a bad token degrades to no-op instead of failing startup — the
     * dashboard must still come up.
     */
    @Bean
    fun discordGateway(
        nicknameService: NicknameService,
        sessions: GameSessionRepository,
        roles: RoleRegistry,
        engine: BulkOperationEngine,
        ws: GameWebSocketHandler,
        msg: Msg,
    ): DiscordGateway {
        if (!properties.hasToken) {
            log.warn("No Discord token configured — running with the no-op gateway (REST/WS still serve).")
            return NoOpDiscordGateway(ws, msg)
        }
        return try {
            JdaDiscordGateway(properties, sessions, roles, engine, ws, msg)
        } catch (e: Exception) {
            log.error("Failed to start JDA ({}); falling back to the no-op gateway.", e.message)
            NoOpDiscordGateway(ws, msg)
        }
    }
}

