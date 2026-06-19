package dev.robothanzo.werewolf.service

import dev.robothanzo.werewolf.discord.DiscordBot
import dev.robothanzo.werewolf.discord.DiscordCommandHandler
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component

/**
 * The single [DiscordCommandHandler] the [DiscordBot] knows about. Slash commands fan out to the
 * service that owns each one: `/server` lifecycle → [ServerProvisioningService], `/game` gameplay
 * actions → [DayOrchestrator]. Mirrors [InteractionRouter] for component interactions, and keeps each
 * service focused on its own concern instead of all implementing one fat handler. Registers itself in
 * `@PostConstruct`, avoiding a constructor cycle with the bot.
 */
@Component
class CommandRouter(
    private val discord: DiscordBot,
    private val provisioning: ServerProvisioningService,
    private val day: DayOrchestrator,
) : DiscordCommandHandler {

    @PostConstruct
    fun register() = discord.setCommandHandler(this)

    override fun onServerCreate(creatorId: Long, playerCount: Int, doubleIdentity: Boolean): String =
        provisioning.onServerCreate(creatorId, playerCount, doubleIdentity)

    override fun onServerDelete(creatorId: Long, guildId: Long): String =
        provisioning.onServerDelete(creatorId, guildId)

    override fun onGuildJoined(guildId: Long, ownerId: Long) =
        provisioning.onGuildJoined(guildId, ownerId)

    override fun onDetonate(guildId: Long, userId: Long): String =
        day.detonate(guildId, userId)
}
