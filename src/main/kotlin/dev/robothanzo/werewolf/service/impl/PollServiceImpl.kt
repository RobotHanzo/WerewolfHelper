package dev.robothanzo.werewolf.service.impl

import dev.robothanzo.werewolf.model.AbstractPoll
import dev.robothanzo.werewolf.service.PollService
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class PollServiceImpl : PollService {
    // polls map structure: guildId -> (pollId -> AbstractPoll)
    private val polls = ConcurrentHashMap<Long, MutableMap<String, AbstractPoll>>()

    override fun getPolls(guildId: Long): Map<String, AbstractPoll> {
        return polls[guildId] ?: emptyMap()
    }

    override fun hasPoll(guildId: Long, pollId: String): Boolean {
        return polls[guildId]?.containsKey(pollId) == true
    }

    override fun getPoll(guildId: Long, pollId: String): AbstractPoll? {
        return polls[guildId]?.get(pollId)
    }

    override fun removePoll(guildId: Long, pollId: String) {
        polls[guildId]?.remove(pollId)
    }
}
