package dev.robothanzo.werewolf.model

import java.util.concurrent.ConcurrentHashMap

data class ExpelSession(
    val guildId: Long,
    var startTime: Long = 0,
    var endTime: Long = 0,
    val candidates: MutableMap<Int, Candidate> = ConcurrentHashMap()
)
