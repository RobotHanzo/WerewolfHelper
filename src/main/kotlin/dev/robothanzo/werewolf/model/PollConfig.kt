package dev.robothanzo.werewolf.model

import dev.robothanzo.werewolf.audio.Audio
import net.dv8tion.jda.api.components.buttons.Button

/**
 * Unified configuration for both expel voting and police elections.
 *
 * @param title The title for the Discord embed (e.g., "驅逐投票" or "警長投票")
 * @param description The description for the Discord embed
 * @param buttonStyle Function to create buttons - receives candidate and returns a Button
 * @param audioResource Audio to play when poll starts (or null for none)
 * @param onVotingFinished Callback invoked when voting completes with winners and allowPK flag
 * @param noVotesMessage Message to display when no one votes
 * @param tiePKMessage Message to display on second tie (if allowPK = false)
 */
data class PollConfig(
    val title: String,
    val description: String,
    val buttonStyle: (Candidate) -> Button,
    val audioResource: Audio.Resource,
    val onVotingFinished: (winners: List<Candidate>, allowPK: Boolean) -> Unit,
    val noVotesMessage: String,
    val tiePKMessage: String
)
