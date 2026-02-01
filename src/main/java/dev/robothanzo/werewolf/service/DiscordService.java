package dev.robothanzo.werewolf.service;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;

/**
 * Service for interacting with the Discord JDA API.
 * Provides helper methods to retrieve various Discord entities.
 */
public interface DiscordService {
    /**
     * Gets the JDA instance used by the application.
     *
     * @return the JDA instance
     */
    JDA getJDA();

    /**
     * Retrieves a Discord guild by its ID.
     *
     * @param guildId the ID of the guild
     * @return the guild, or null if not found
     */
    Guild getGuild(long guildId);

    /**
     * Retrieves a member of a guild by their user ID.
     *
     * @param guildId the ID of the guild
     * @param userId  the ID of the user
     * @return the member, or null if not found
     */
    Member getMember(long guildId, String userId);

    /**
     * Retrieves a text channel by its guild and channel ID.
     *
     * @param guildId   the ID of the guild
     * @param channelId the ID of the channel
     * @return the text channel, or null if not found
     */
    TextChannel getTextChannel(long guildId, long channelId);

    /**
     * Retrieves a voice channel by its guild and channel ID.
     *
     * @param guildId   the ID of the guild
     * @param channelId the ID of the channel
     * @return the voice channel, or null if not found
     */
    VoiceChannel getVoiceChannel(long guildId, long channelId);
}
