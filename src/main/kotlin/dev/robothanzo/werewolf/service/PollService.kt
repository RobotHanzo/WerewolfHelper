package dev.robothanzo.werewolf.service

import dev.robothanzo.werewolf.model.AbstractPoll

interface PollService {
    /**
     * Get all active polls for a guild
     */
    fun getPolls(guildId: Long): Map<String, AbstractPoll>

    /**
     * Check if a poll exists
     */
    fun hasPoll(guildId: Long, pollId: String): Boolean

    /**
     * Get a specific poll by ID
     */
    fun getPoll(guildId: Long, pollId: String): AbstractPoll?

    /**
     * Remove a poll
     */
    fun removePoll(guildId: Long, pollId: String)
}
