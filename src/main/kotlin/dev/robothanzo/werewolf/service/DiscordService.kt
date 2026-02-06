package dev.robothanzo.werewolf.service

import net.dv8tion.jda.api.JDA

/**
 * Service for interacting with the Discord JDA API.
 * Provides helper methods to retrieve various Discord entities.
 */
interface DiscordService {
    /**
     * Gets the JDA instance used by the application.
     *
     * @return the JDA instance
     */
    val jda: JDA
}
