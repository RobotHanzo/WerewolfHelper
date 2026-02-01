package dev.robothanzo.werewolf.service;

import dev.robothanzo.werewolf.database.documents.Session;
import dev.robothanzo.werewolf.model.PoliceSession;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

import java.util.Map;

/**
 * Service for managing police (sheriff) election processes,
 * including enrollment, speech management, and voting.
 */
public interface PoliceService {
    /**
     * Retrieves all active police election sessions.
     *
     * @return a map of guild IDs to their active police sessions
     */
    Map<Long, PoliceSession> getSessions();

    /**
     * Starts the police enrollment phase for a game session.
     *
     * @param session the game session
     * @param channel the Discord channel where the election is taking place
     * @param message an optional message to reply to or edit
     */
    void startEnrollment(Session session, GuildMessageChannel channel, Message message);

    /**
     * Handles a user's interaction when they click the "Enroll in Police" button.
     *
     * @param event the button interaction event
     */
    void enrollPolice(ButtonInteractionEvent event);

    /**
     * Advances the police election process to the next stage (enrollment -> speech
     * -> voting).
     *
     * @param guildId the ID of the guild
     */
    void next(long guildId);

    /**
     * Interrupts and ends the police election process for a guild.
     *
     * @param guildId the ID of the guild
     */
    void interrupt(long guildId);

    /**
     * Forcefully transitions the election stage to the voting phase.
     *
     * @param guildId the ID of the guild
     */
    void forceStartVoting(long guildId);
}
