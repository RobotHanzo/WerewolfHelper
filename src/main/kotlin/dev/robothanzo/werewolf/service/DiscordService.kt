package dev.robothanzo.werewolf.service

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel

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
    val jda: JDA?

    /**
     * Retrieves a Discord guild by its ID.
     *
     * @param guildId the ID of the guild
     * @return the guild, or null if not found
     */
    fun getGuild(guildId: Long): Guild?

    /**
     * Retrieves a member of a guild by their user ID.
     *
     * @param guildId the ID of the guild
     * @param userId  the ID of the user
     * @return the member, or null if not found
     */
    fun getMember(guildId: Long, userId: String?): Member?

    /**
     * Retrieves a text channel by its guild and channel ID.
     *
     * @param guildId   the ID of the guild
     * @param channelId the ID of the channel
     * @return the text channel, or null if not found
     */
    fun getTextChannel(guildId: Long, channelId: Long): TextChannel?

    /**
     * Retrieves a voice channel by its guild and channel ID.
     *
     * @param guildId   the ID of the guild
     * @param channelId the ID of the channel
     * @return the voice channel, or null if not found
     */
    fun getVoiceChannel(guildId: Long, channelId: Long): VoiceChannel?
}
