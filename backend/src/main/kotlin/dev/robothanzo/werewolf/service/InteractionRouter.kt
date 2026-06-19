package dev.robothanzo.werewolf.service

import dev.robothanzo.werewolf.discord.DiscordBot
import dev.robothanzo.werewolf.discord.DiscordInteractionHandler
import dev.robothanzo.werewolf.discord.InteractionReply
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * The single [DiscordInteractionHandler] the [DiscordBot] knows about. Component custom ids are
 * namespaced `wh:<ns>:...`; this router reads the `<ns>` segment and dispatches to the orchestrator
 * that registered it (`night` → [NightOrchestrator], `day` → [DayOrchestrator]). Orchestrators
 * register themselves in `@PostConstruct`, avoiding a constructor cycle with the bot.
 */
@Component
class InteractionRouter(
    private val discord: DiscordBot,
) : DiscordInteractionHandler {

    private val handlers = ConcurrentHashMap<String, DiscordInteractionHandler>()

    @PostConstruct
    fun register() = discord.setInteractionHandler(this)

    /** Register a sub-handler for the `wh:<namespace>:...` custom-id family. */
    fun register(namespace: String, handler: DiscordInteractionHandler) {
        handlers[namespace] = handler
    }

    override fun handle(guildId: Long, userId: Long, channelId: Long, customId: String, values: List<String>): InteractionReply? {
        val namespace = customId.split(":").getOrNull(1) ?: return null
        return handlers[namespace]?.handle(guildId, userId, channelId, customId, values)
    }
}
