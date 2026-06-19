package dev.robothanzo.werewolf.discord

import club.minnced.discord.jdave.interop.JDaveSessionFactory
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.audio.AudioModuleConfig
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.ChunkingFilter
import net.dv8tion.jda.api.utils.MemberCachePolicy
import net.dv8tion.jda.api.utils.cache.CacheFlag
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
     * The single JDA connection, shared by every Discord-touching component. **Nullable on purpose**:
     * with no token (or a bad one) the bean is `null` and every consumer injects `JDA?`, guarding its
     * Discord calls — so the REST API + WebSocket hub still serve fully (the old no-op gateway is gone,
     * the null is the no-op). Construction is wrapped so a bad token degrades to `null` instead of
     * failing startup; the dashboard must always come up.
     */
    @Bean
    fun jda(): JDA? {
        if (!properties.hasToken) {
            log.warn("No Discord token configured — Discord features disabled (REST/WS still serve).")
            return null
        }
        return try {
            JDABuilder.create(
                properties.token,
                GatewayIntent.GUILD_MEMBERS,
                GatewayIntent.GUILD_MESSAGES,
                GatewayIntent.GUILD_VOICE_STATES,
                GatewayIntent.MESSAGE_CONTENT,
            )
                .setMemberCachePolicy(MemberCachePolicy.ALL)
                .setChunkingFilter(ChunkingFilter.ALL)
                .enableCache(CacheFlag.VOICE_STATE)
                .disableCache(CacheFlag.ACTIVITY, CacheFlag.EMOJI, CacheFlag.STICKER, CacheFlag.SCHEDULED_EVENTS)
                .setAudioModuleConfig(AudioModuleConfig().withDaveSessionFactory(JDaveSessionFactory()))
                .build()
        } catch (e: Exception) {
            log.error("Failed to start JDA ({}); Discord features disabled.", e.message)
            null
        }
    }
}
