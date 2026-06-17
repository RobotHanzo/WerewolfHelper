package dev.robothanzo.werewolf.domain.repo

import dev.robothanzo.werewolf.domain.DashboardUser
import dev.robothanzo.werewolf.domain.GameLogEntry
import dev.robothanzo.werewolf.domain.GameRecording
import dev.robothanzo.werewolf.domain.GameSession
import dev.robothanzo.werewolf.domain.PendingSetup
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface GameSessionRepository : MongoRepository<GameSession, Long>

@Repository
interface GameRecordingRepository : MongoRepository<GameRecording, String> {
    /** Every recording the user took part in (any role), newest first. */
    fun findByParticipantIdsContainingOrderByEndedAtDesc(userId: Long): List<GameRecording>
}

@Repository
interface GameLogRepository : MongoRepository<GameLogEntry, String> {
    fun findByGuildIdOrderByTimestampDesc(guildId: Long): List<GameLogEntry>
    fun deleteByGuildId(guildId: Long)
}

@Repository
interface DashboardUserRepository : MongoRepository<DashboardUser, String> {
    fun findByGuildIdAndUserId(guildId: Long, userId: Long): DashboardUser?
    fun findByGuildId(guildId: Long): List<DashboardUser>
}

@Repository
interface PendingSetupRepository : MongoRepository<PendingSetup, String> {
    fun findByCreatorId(creatorId: Long): PendingSetup?
}
