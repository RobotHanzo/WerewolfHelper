package dev.robothanzo.werewolf.service;

import dev.robothanzo.werewolf.database.documents.Session;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;

import java.util.Collection;

/**
 * Service for managing speech sessions during the game,
 * including auto-speech flow, last words, and timers.
 */
public interface SpeechService {
    /**
     * Starts a speech poll workflow.
     */
    void startSpeechPoll(Guild guild, Message enrollMessage, Collection<Session.Player> players, Runnable callback);

    /**
     * Starts last words speech for a player.
     */
    void startLastWordsSpeech(Guild guild, long channelId, Session.Player player, Runnable callback);

    /**
     * Sets the speech order for a guild.
     */
    void setSpeechOrder(long guildId, dev.robothanzo.werewolf.model.SpeechOrder order);

    /**
     * Confirms the speech order and starts the speech flow for a guild.
     */
    void confirmSpeechOrder(long guildId);

    /**
     * Handles order selection from a dropdown.
     */
    void handleOrderSelection(StringSelectInteractionEvent event);

    /**
     * Confirms the selected order and starts the speech.
     */
    void confirmOrder(ButtonInteractionEvent event);

    /**
     * Skips the current speaker's turn.
     */
    void skipSpeech(ButtonInteractionEvent event);

    /**
     * Interrupts the current speaker's turn (e.g., via vote or admin).
     */
    void interruptSpeech(ButtonInteractionEvent event);

    /**
     * Starts the automatic speech flow for the daytime.
     */
    void startAutoSpeechFlow(long guildId, long channelId);

    /**
     * Starts a standalone timer.
     */
    void startTimer(long guildId, long channelId, long voiceChannelId, int seconds);

    /**
     * Stops a timer running in a specific channel.
     */
    void stopTimer(long channelId);

    /**
     * Gets the current speech session for a guild, if any.
     * Use with caution, intended for read-only access.
     */
    dev.robothanzo.werewolf.model.SpeechSession getSpeechSession(long guildId);

    /**
     * Interrupts the entire speech session for a guild.
     */
    void interruptSession(long guildId);

    /**
     * Skips to the next speaker for a guild.
     */
    void skipToNext(long guildId);

    /**
     * Mutes or unmutes all non-admin members in a guild.
     */
    void setAllMute(long guildId, boolean mute);
}
