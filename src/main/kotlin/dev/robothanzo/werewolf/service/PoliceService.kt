package dev.robothanzo.werewolf.service

import dev.robothanzo.werewolf.database.documents.Player
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.PoliceStatus
import dev.robothanzo.werewolf.model.PoliceSession
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent


/**
 * Service for managing police (sheriff) election processes,
 * including enrollment, speech management, and voting.
 */
interface PoliceService {
    /**
     * Retrieves all active police election sessions.
     *
     * @return a map of guild IDs to their active police sessions
     */
    val sessions: Map<Long, PoliceSession>

    /**
     * Starts the police enrollment phase for a game session.
     *
     * @param session the game session
     * @param channel the Discord channel where the election is taking place
     * @param message an optional message to reply to or edit
     */
    fun startEnrollment(
        session: Session,
        channel: GuildMessageChannel,
        message: Message?,
        callback: (() -> Unit)? = null
    )

    /**
     * Handles a user's interaction when they click the "Enroll in Police" button.
     *
     * @param event the button interaction event
     */
    fun enrollPolice(event: ButtonInteractionEvent)

    /**
     * Advances the police election process to the next stage (enrollment -> speech
     * -> voting).
     *
     * @param guildId the ID of the guild
     */
    fun next(guildId: Long)

    /**
     * Interrupts and ends the police election process for a guild.
     *
     * @param guildId the ID of the guild
     */
    fun interrupt(guildId: Long, triggerCallback: Boolean = false)

    /**
     * Forcefully transitions the election stage to the voting phase.
     *
     * @param guildId the ID of the guild
     */
    fun forceStartVoting(guildId: Long)

    /**
     * Initiates the process of transferring the police badge from one player to another.
     * Use this when the current police dies or voluntarily gives up the badge.
     *
     * @param session  the game session
     * @param guild    the Discord guild
     * @param player   the current police player who is transferring the badge
     * @param callback optional callback to run after the transfer is complete or destroyed
     */
    fun transferPolice(
        session: Session,
        guild: Guild?,
        player: Player,
        callback: (() -> Unit)?
    )

    /**
     * Handles the selection of a new police candidate from the dropdown menu.
     *
     * @param event the entity select interaction event
     */
    fun selectNewPolice(event: net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent)

    /**
     * Confirms the transfer of the police badge to the selected candidate.
     *
     * @param event the button interaction event
     */
    fun confirmNewPolice(event: ButtonInteractionEvent)

    /**
     * Destroys the police badge (no successor).
     *
     * @param event the button interaction event
     */
    fun destroyPolice(event: ButtonInteractionEvent)

    fun getPoliceStatus(guildId: Long): PoliceStatus?

    /**
     * Removes a player from the police candidates list or marks them as quit.
     *
     * @param guildId the ID of the guild
     * @param playerId the ID of the player to remove/mark as quit
     * @return true if the player was successfully removed or marked as quit
     */
    fun quitEnrollment(guildId: Long, playerId: Int): Boolean
}

data class TransferPoliceSession(
    val guildId: Long,
    val senderId: Int,
    val possibleRecipientIds: MutableList<Int> = ArrayList(),
    var recipientId: Int? = null,
    val callback: (() -> Unit)? = null
)
