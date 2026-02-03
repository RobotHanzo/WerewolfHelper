package dev.robothanzo.werewolf.service

import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.model.SpeechOrder
import dev.robothanzo.werewolf.model.SpeechSession
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent

/**
 * Service for managing speech sessions during the game,
 * including auto-speech flow, last words, and timers.
 */
interface SpeechService {
    /**
     * Starts a speech poll workflow.
     */
    fun startSpeechPoll(
        guild: Guild,
        enrollMessage: Message?,
        players: Collection<Session.Player>,
        callback: (() -> Unit)?
    )

    /**
     * Starts last words speech for a player.
     */
    fun startLastWordsSpeech(
        guild: Guild,
        channelId: Long,
        player: Session.Player,
        callback: (() -> Unit)?
    )

    /**
     * Sets the speech order for a guild.
     */
    fun setSpeechOrder(guildId: Long, order: SpeechOrder)

    /**
     * Confirms the speech order and starts the speech flow for a guild.
     */
    fun confirmSpeechOrder(guildId: Long)

    /**
     * Handles order selection from a dropdown.
     */
    fun handleOrderSelection(event: StringSelectInteractionEvent)

    /**
     * Confirms the selected order and starts the speech.
     */
    fun confirmOrder(event: ButtonInteractionEvent)

    /**
     * Skips the current speaker's turn.
     */
    fun skipSpeech(event: ButtonInteractionEvent)

    /**
     * Interrupts the current speaker's turn (e.g., via vote or admin).
     */
    fun interruptSpeech(event: ButtonInteractionEvent)

    /**
     * Starts the automatic speech flow for the daytime.
     */
    fun startAutoSpeechFlow(guildId: Long, channelId: Long)

    /**
     * Starts a standalone timer.
     */
    fun startTimer(guildId: Long, channelId: Long, voiceChannelId: Long, seconds: Int)

    /**
     * Stops a timer running in a specific channel.
     */
    fun stopTimer(channelId: Long)

    /**
     * Gets the current speech session for a guild, if any.
     * Use with caution, intended for read-only access.
     */
    fun getSpeechSession(guildId: Long): SpeechSession?

    /**
     * Interrupts the entire speech session for a guild.
     */
    fun interruptSession(guildId: Long)

    /**
     * Skips to the next speaker for a guild.
     */
    fun skipToNext(guildId: Long)

    /**
     * Mutes or unmutes all non-admin members in a guild.
     */
    fun setAllMute(guildId: Long, mute: Boolean)
}
